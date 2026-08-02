#!/usr/bin/env bash
set -Eeuo pipefail

# 本地数据库与 Docker 基础设施从零重建脚本。
# 仅操作 MySQL、Redis 和 Flyway 容器，不构建或启动前后端。
#
# 重建策略：
#   1. 删除本地 MySQL、Redis 持久化数据；
#   2. 使用 Flyway执行正式目录中的全部版本迁移；
#   3. 在全新数据库中建立默认测试管理员；
#   4. 导入 backend-java/docs/sql/reset_and_seed_test_data.sql；
#   5. 全部测试数据不写入 flyway_schema_history。
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
ADMIN_BOOTSTRAP_SQL="${PROJECT_ROOT}/backend-java/docs/sql/bootstrap_test_admin.sql"
FULL_TEST_DATA_SQL="${PROJECT_ROOT}/backend-java/docs/sql/reset_and_seed_test_data.sql"

MYSQL_DATA_DIR="${PROJECT_ROOT}/data/mysql"
REDIS_DATA_DIR="${PROJECT_ROOT}/data/redis"

MYSQL_CONTAINER="wust-dormitory-mysql"
REDIS_CONTAINER="wust-dormitory-redis"
DOCKER_NETWORK="wust-dormitory-network"
FLYWAY_IMAGE="${WUST_DORMITORY_FLYWAY_IMAGE:-flyway/flyway:11.14.1-alpine}"
LATEST_MIGRATION_VERSION=""

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

latest_migration_version() {
  find "${FORMAL_SQL_DIR}" \
    -maxdepth 1 \
    -type f \
    -name 'V[0-9]*__*.sql' \
    -printf '%f\n' \
    | sed -nE 's/^V([0-9]+)__.*/\1/p' \
    | sort -n \
    | tail -n 1
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
  [[ -f "${ENV_FILE}" ]] || fail "未找到 ${ENV_FILE}。请先执行：cp .env.example .env"
  [[ -f "${COMPOSE_FILE}" ]] || fail "未找到 Docker Compose 文件：${COMPOSE_FILE}"
  [[ -d "${FORMAL_SQL_DIR}" ]] || fail "未找到正式迁移目录：${FORMAL_SQL_DIR}"
  [[ -f "${ADMIN_BOOTSTRAP_SQL}" ]] || fail "未找到测试管理员引导脚本：${ADMIN_BOOTSTRAP_SQL}"
  [[ -f "${FULL_TEST_DATA_SQL}" ]] || fail "未找到全量测试数据脚本：${FULL_TEST_DATA_SQL}"

  LATEST_MIGRATION_VERSION="$(latest_migration_version)"
  [[ "${LATEST_MIGRATION_VERSION}" =~ ^[0-9]+$ ]] || fail "无法从正式迁移目录识别最新Flyway版本"

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
    grep -qE "^${variable_name}=.+" "${ENV_FILE}" || fail ".env 缺少有效配置：${variable_name}"
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

随后会执行正式迁移，并导入V9全量测试数据。
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
    database) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_DATABASE"' ;;
    user) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_USER"' ;;
    password) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_PASSWORD"' ;;
    *) fail "未知 MySQL 配置项：$1" ;;
  esac
}

run_formal_migrations() {
  local database user password
  database="$(mysql_env database)"
  user="$(mysql_env user)"
  password="$(mysql_env password)"
  [[ -n "${database}" && -n "${user}" && -n "${password}" ]] || fail "无法从 MySQL 容器读取数据库连接信息"

  docker image inspect "${FLYWAY_IMAGE}" >/dev/null 2>&1 || docker pull "${FLYWAY_IMAGE}"
  log "使用 Flyway 容器执行正式版本迁移 V1～V${LATEST_MIGRATION_VERSION}"
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
    exec mysql --binary-mode=1 --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"
  ' < "${sql_file}"
}

import_test_data() {
  import_sql_file "${ADMIN_BOOTSTRAP_SQL}" "默认测试管理员"
  import_sql_file "${FULL_TEST_DATA_SQL}" "V9全量测试数据"
}

query() {
  docker exec -i "${MYSQL_CONTAINER}" sh -ec '
    MYSQL_PWD="$MYSQL_PASSWORD" \
    exec mysql --batch --skip-column-names --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"
  '
}

verify() {
  log "校验正式迁移历史和全量测试数据"
  local version unresolved_repeatable counts statuses fixtures admin_count

  version="$(query <<'SQL'
SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;
SQL
)"
  [[ "${version}" == "${LATEST_MIGRATION_VERSION}" ]] || fail "Flyway版本应为${LATEST_MIGRATION_VERSION}，实际为：${version}"

  unresolved_repeatable="$(query <<'SQL'
SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NULL;
SQL
)"
  [[ "${unresolved_repeatable}" == "0" ]] || fail "Flyway历史中不应记录测试数据脚本，实际数量：${unresolved_repeatable}"

  counts="$(query <<'SQL'
SELECT CONCAT_WS(',',
  (SELECT COUNT(*) FROM major),
  (SELECT COUNT(*) FROM student),
  (SELECT COUNT(*) FROM student WHERE gender='M'),
  (SELECT COUNT(*) FROM student WHERE gender='F'),
  (SELECT COUNT(*) FROM room),
  (SELECT COUNT(*) FROM bed),
  (SELECT COUNT(*) FROM selection_batch)
);
SQL
)"
  [[ "${counts}" == "5,20,10,10,8,36,1" ]] || fail "全量测试数据数量异常：${counts}"

  statuses="$(query <<'SQL'
SELECT CONCAT_WS(',',
  (SELECT COUNT(*) FROM app_user WHERE user_type='STUDENT' AND account_status='PENDING'),
  (SELECT COUNT(*) FROM batch_student_eligibility WHERE batch_id=1 AND eligibility_status='ELIGIBLE'),
  (SELECT COUNT(*) FROM student WHERE nationality_code<>'CN')
);
SQL
)"
  [[ "${statuses}" == "20,20,10" ]] || fail "学生账号、批次资格或国际学生数量异常：${statuses}"

  fixtures="$(query <<'SQL'
SELECT CONCAT_WS(',',
  (SELECT COUNT(*) FROM questionnaire_answer),
  (SELECT COUNT(*) FROM student_feature),
  (SELECT COUNT(*) FROM selection_team),
  (SELECT COUNT(*) FROM team_invitation),
  (SELECT COUNT(*) FROM student_notification),
  (SELECT COUNT(*) FROM allocation_run_result WHERE result_status='UNASSIGNED')
);
SQL
)"
  [[ "${fixtures}" == "1,1,1,1,1,1" ]] || fail "学生重置或分配失败测试样例异常：${fixtures}"

  admin_count="$(query <<'SQL'
SELECT COUNT(*) FROM app_user WHERE username='admin' AND user_type='ADMIN' AND account_status='ACTIVE';
SQL
)"
  [[ "${admin_count}" == "1" ]] || fail "管理员测试账号未正确保留"
  log "数据库结构、Flyway历史和V9全量测试数据校验通过"
}

print_summary() {
  cat <<EOF

============================================================
本地数据库与 Docker 基础设施已从零重建
============================================================

容器：
  MySQL：healthy
  Redis：healthy

正式结构：
  Flyway：V1～V${LATEST_MIGRATION_VERSION}
  数据字典：backend-java/docs/database-dictionary.md

测试数据：
  专业：5
  学生：20（男生10、女生10）
  学生账号：全部为待激活，统一分配仍应包含全部学生
  国际学生：10
  房间：8
  床位：36
  测试批次：1
  重置样例：个人偏好、组队邀请、系统通知
  分配样例：1条未分配结果及失败原因

管理员登录：
  用户名：admin
  密码：Dormitory@2026

学生示例：
  学号：202600000001
  姓名：张明宇
  首次登录前需自行激活并设置密码

脚本没有构建或启动前后端。
============================================================
EOF
}

main() {
  cd "${PROJECT_ROOT}"
  require_command docker
  docker compose version >/dev/null 2>&1 || fail "当前 Docker 不支持 docker compose"
  validate_environment
  confirm_destruction
  reset_containers_and_data
  run_formal_migrations
  import_test_data
  verify
  print_summary
}

main "$@"
