#!/usr/bin/env bash
set -Eeuo pipefail

# 本地数据库与 Docker 基础设施从零重建脚本。
# 仅操作 MySQL、Redis 和 Flyway 容器，不构建或启动前后端。
#
# 重建策略：
#   1. 删除本地 MySQL、Redis 持久化数据；
#   2. 使用 Flyway 只执行正式版本迁移 V1～V4；
#   3. 使用 MySQL 客户端直接导入开发测试数据；
#   4. 测试数据不写入 flyway_schema_history，避免后端只加载正式迁移时校验失败。
#
# 警告：会永久删除项目根目录 data/mysql 和 data/redis。
#
# 用法：
#   bash scripts/dev/reset-local-environment.sh
#   bash scripts/dev/reset-local-environment.sh --yes

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
FORMAL_SQL_DIR="${PROJECT_ROOT}/backend-java/server/src/main/resources/db/migration"
DEV_DATA_SQL="${PROJECT_ROOT}/backend-java/server/src/test/resources/db/dev-migration/R__development_test_data.sql"
DEV_REFINEMENT_SQL="${PROJECT_ROOT}/backend-java/server/src/test/resources/db/dev-migration/R__zz_refine_development_questionnaire.sql"

MYSQL_DATA_DIR="${PROJECT_ROOT}/data/mysql"
REDIS_DATA_DIR="${PROJECT_ROOT}/data/redis"

MYSQL_CONTAINER="wust-dormitory-mysql"
REDIS_CONTAINER="wust-dormitory-redis"
DOCKER_NETWORK="wust-dormitory-network"
FLYWAY_IMAGE="${WUST_DORMITORY_FLYWAY_IMAGE:-flyway/flyway:11.14.1-alpine}"

ASSUME_YES=0
case "${1:-}" in
  "") ;;
  --yes) ASSUME_YES=1 ;;
  *)
    printf '[错误] 未知参数：%s\n' "$1" >&2
    printf '支持参数：--yes\n' >&2
    exit 2
    ;;
esac

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

warn() {
  printf '\n[警告] %s\n' "$*" >&2
}

fail() {
  printf '\n[错误] %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "未找到命令：$1"
}

compose() {
  env \
    -u WUST_DORMITORY_TIMEZONE \
    -u WUST_DORMITORY_MYSQL_IMAGE \
    -u WUST_DORMITORY_DB_HOST \
    -u WUST_DORMITORY_DB_PORT \
    -u WUST_DORMITORY_DB_NAME \
    -u WUST_DORMITORY_DB_USER \
    -u WUST_DORMITORY_DB_PASSWORD \
    -u WUST_DORMITORY_DB_ROOT_PASSWORD \
    -u WUST_DORMITORY_REDIS_IMAGE \
    -u WUST_DORMITORY_REDIS_HOST \
    -u WUST_DORMITORY_REDIS_PORT \
    -u WUST_DORMITORY_REDIS_PASSWORD \
    docker compose \
      --env-file "${ENV_FILE}" \
      -f "${COMPOSE_FILE}" \
      "$@"
}

container_status() {
  docker inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$1" 2>/dev/null || true
}

wait_for_container() {
  local container="$1"
  local display_name="$2"
  local max_attempts="${3:-60}"

  for _ in $(seq 1 "${max_attempts}"); do
    local status
    status="$(container_status "${container}")"

    if [[ "${status}" == "healthy" ]]; then
      log "${display_name} 已健康"
      return 0
    fi

    if [[ "${status}" == "unhealthy" || "${status}" == "exited" || "${status}" == "dead" ]]; then
      docker logs --tail 120 "${container}" >&2 || true
      fail "${display_name} 容器状态异常：${status}"
    fi

    sleep 2
  done

  docker logs --tail 120 "${container}" >&2 || true
  fail "${display_name} 未在预期时间内进入 healthy 状态"
}

validate_environment() {
  [[ -f "${ENV_FILE}" ]] \
    || fail "未找到 ${ENV_FILE}。请先执行：cp .env.example .env"

  [[ -f "${COMPOSE_FILE}" ]] \
    || fail "未找到 Docker Compose 文件：${COMPOSE_FILE}"

  [[ -d "${FORMAL_SQL_DIR}" ]] \
    || fail "未找到正式迁移目录：${FORMAL_SQL_DIR}"

  [[ -f "${DEV_DATA_SQL}" ]] \
    || fail "未找到开发测试数据脚本：${DEV_DATA_SQL}"

  [[ -f "${DEV_REFINEMENT_SQL}" ]] \
    || fail "未找到开发问卷修正脚本：${DEV_REFINEMENT_SQL}"

  if grep -q '请替换为' "${ENV_FILE}"; then
    fail ".env 中仍有“请替换为...”占位配置，请先填写 MySQL 和 Redis 密码。"
  fi

  if grep -qE '^[A-Za-z_][A-Za-z0-9_]*=\$\{[^}]+\}$' "${ENV_FILE}"; then
    fail ".env 中存在未展开的变量表达式，请直接填写真实配置值。"
  fi

  local required_variables=(
    WUST_DORMITORY_MYSQL_IMAGE
    WUST_DORMITORY_DB_NAME
    WUST_DORMITORY_DB_USER
    WUST_DORMITORY_DB_PASSWORD
    WUST_DORMITORY_DB_ROOT_PASSWORD
    WUST_DORMITORY_REDIS_IMAGE
    WUST_DORMITORY_REDIS_PASSWORD
  )

  for variable_name in "${required_variables[@]}"; do
    grep -qE "^${variable_name}=.+" "${ENV_FILE}" \
      || fail ".env 缺少有效配置：${variable_name}"
  done
}

confirm_destruction() {
  if [[ "${ASSUME_YES}" -eq 1 ]]; then
    return 0
  fi

  cat <<EOF

============================================================
危险操作确认
============================================================

即将永久删除：

  ${MYSQL_DATA_DIR}
  ${REDIS_DATA_DIR}

随后会重新创建数据库结构并导入开发测试数据。
请输入大写 RESET 继续，输入其他内容将取消。
============================================================
EOF

  local answer
  read -r -p "> " answer

  [[ "${answer}" == "RESET" ]] || {
    printf '已取消，没有删除任何数据。\n'
    exit 0
  }
}

remove_data_directory() {
  local directory="$1"

  case "${directory}" in
    "${MYSQL_DATA_DIR}"|"${REDIS_DATA_DIR}") ;;
    *) fail "拒绝删除非预期目录：${directory}" ;;
  esac

  if rm -rf -- "${directory}" 2>/dev/null; then
    return 0
  fi

  warn "当前用户无法直接删除 ${directory}，改用临时容器清理"
  docker run --rm \
    -v "${PROJECT_ROOT}/data:/workspace-data" \
    alpine:3.20 \
    sh -ec "rm -rf /workspace-data/$(basename "${directory}")"
}

reset_containers_and_data() {
  log "停止 MySQL 和 Redis 容器"
  compose down --remove-orphans

  log "删除 MySQL 和 Redis 持久化数据"
  remove_data_directory "${MYSQL_DATA_DIR}"
  remove_data_directory "${REDIS_DATA_DIR}"

  mkdir -p "${MYSQL_DATA_DIR}" "${REDIS_DATA_DIR}"

  log "启动全新的 MySQL 和 Redis"
  compose up -d mysql redis

  wait_for_container "${MYSQL_CONTAINER}" "MySQL" 60
  wait_for_container "${REDIS_CONTAINER}" "Redis" 60
}

mysql_env() {
  case "$1" in
    database)
      docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_DATABASE"'
      ;;
    user)
      docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_USER"'
      ;;
    password)
      docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_PASSWORD"'
      ;;
    *)
      fail "未知 MySQL 配置项：$1"
      ;;
  esac
}

run_formal_migrations() {
  local database user password
  database="$(mysql_env database)"
  user="$(mysql_env user)"
  password="$(mysql_env password)"

  [[ -n "${database}" && -n "${user}" && -n "${password}" ]] \
    || fail "无法从 MySQL 容器读取数据库连接信息"

  docker image inspect "${FLYWAY_IMAGE}" >/dev/null 2>&1 \
    || docker pull "${FLYWAY_IMAGE}"

  log "使用 Flyway 容器执行正式版本迁移 V1～V4"
  docker run --rm \
    --network "${DOCKER_NETWORK}" \
    -e "FLYWAY_URL=jdbc:mysql://${MYSQL_CONTAINER}:3306/${database}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
    -e "FLYWAY_USER=${user}" \
    -e "FLYWAY_PASSWORD=${password}" \
    -e "FLYWAY_LOCATIONS=filesystem:/flyway/sql/formal" \
    -e "FLYWAY_CONNECT_RETRIES=60" \
    -e "FLYWAY_VALIDATE_ON_MIGRATE=true" \
    -e "FLYWAY_CLEAN_DISABLED=true" \
    -v "${FORMAL_SQL_DIR}:/flyway/sql/formal:ro" \
    "${FLYWAY_IMAGE}" migrate
}

import_sql_file() {
  local sql_file="$1"
  local display_name="$2"

  log "导入${display_name}"
  docker exec -i "${MYSQL_CONTAINER}" sh -ec '
    MYSQL_PWD="$MYSQL_PASSWORD" \
    exec mysql \
      --binary-mode=1 \
      --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" \
      "$MYSQL_DATABASE"
  ' < "${sql_file}"
}

import_development_data() {
  # 开发数据不经过 Flyway，避免后端仅加载正式迁移目录时出现：
  # "Detected applied migration not resolved locally"。
  import_sql_file "${DEV_DATA_SQL}" "基础开发测试数据"
  import_sql_file "${DEV_REFINEMENT_SQL}" "三态吸烟偏好修正数据"
}

query() {
  docker exec -i "${MYSQL_CONTAINER}" sh -ec '
    MYSQL_PWD="$MYSQL_PASSWORD" \
    exec mysql \
      --batch \
      --skip-column-names \
      --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" \
      "$MYSQL_DATABASE"
  '
}

verify() {
  log "校验正式迁移历史与开发测试数据"

  local version unresolved_repeatable counts smoking lock_table admin_count

  version="$(query <<'SQL'
SELECT MAX(CAST(version AS UNSIGNED))
FROM flyway_schema_history
WHERE success=1 AND version IS NOT NULL;
SQL
)"
  [[ "${version}" == "4" ]] \
    || fail "Flyway版本应为4，实际为：${version}"

  unresolved_repeatable="$(query <<'SQL'
SELECT COUNT(*)
FROM flyway_schema_history
WHERE success=1 AND version IS NULL;
SQL
)"
  [[ "${unresolved_repeatable}" == "0" ]] \
    || fail "Flyway历史中不应记录开发重复迁移，实际数量：${unresolved_repeatable}"

  counts="$(query <<'SQL'
SELECT CONCAT_WS(',',
  (SELECT COUNT(*) FROM major WHERE id BETWEEN 1 AND 5),
  (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520),
  (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520 AND gender='M'),
  (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520 AND gender='F'),
  (SELECT COUNT(*) FROM room WHERE id BETWEEN 1 AND 144),
  (SELECT COUNT(*) FROM bed WHERE id BETWEEN 1 AND 640),
  (SELECT COUNT(*) FROM selection_batch WHERE id=1)
);
SQL
)"
  [[ "${counts}" == "5,520,260,260,144,640,1" ]] \
    || fail "测试数据数量异常：${counts}"

  smoking="$(query <<'SQL'
SELECT GROUP_CONCAT(qo.option_code ORDER BY qo.sort_order)
FROM questionnaire_option qo
JOIN questionnaire_question qq ON qq.id=qo.question_id
WHERE qq.question_code='SMOKING_ACCEPTANCE';
SQL
)"
  [[ "${smoking}" == "ACCEPT,REJECT,ANY" ]] \
    || fail "吸烟偏好选项异常：${smoking}"

  lock_table="$(query <<'SQL'
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name='active_batch_student_lock';
SQL
)"
  [[ "${lock_table}" == "1" ]] \
    || fail "V4活动批次锁表未建立"

  admin_count="$(query <<'SQL'
SELECT COUNT(*)
FROM app_user
WHERE username='admin'
  AND account_status='ACTIVE';
SQL
)"
  [[ "${admin_count}" == "1" ]] \
    || fail "管理员测试账号未正确建立"

  log "数据库结构、Flyway历史和测试数据校验通过"
}

print_summary() {
  cat <<'EOF'

============================================================
本地数据库与 Docker 基础设施已从零重建
============================================================

容器：
  MySQL：healthy
  Redis：healthy

正式结构：
  Flyway：V1～V4
  Flyway历史中不记录开发测试数据

开发数据：
  专业：5
  学生：520（男生260、女生260）
  房间：144
  床位：640
  测试批次：1
  吸烟偏好：接受 / 不接受 / 均可

管理员登录：
  用户名：admin
  密码：Dormitory@2026

学生示例：
  学号：202600000001
  姓名：测试男生001
  首次登录前需自行激活并设置密码

脚本没有构建或启动前后端。
后端按默认正式 Flyway 目录启动时不会再出现开发迁移缺失校验错误。
============================================================
EOF
}

main() {
  cd "${PROJECT_ROOT}"

  require_command docker
  docker compose version >/dev/null 2>&1 \
    || fail "当前 Docker 不支持 docker compose"

  validate_environment
  confirm_destruction
  reset_containers_and_data
  run_formal_migrations
  import_development_data
  verify
  print_summary
}

main "$@"
