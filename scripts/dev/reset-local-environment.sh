#!/usr/bin/env bash
set -Eeuo pipefail

# 本地数据库与 Docker 基础设施从零重建脚本。
# 仅操作 MySQL、Redis 和 Flyway 容器，不构建或启动前后端。
# 警告：会永久删除项目根目录 data/mysql 和 data/redis。
#
# 用法：
#   bash scripts/dev/reset-local-environment.sh
#   bash scripts/dev/reset-local-environment.sh --yes

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
FORMAL_SQL="${PROJECT_ROOT}/backend-java/server/src/main/resources/db/migration"
DEV_SQL="${PROJECT_ROOT}/backend-java/server/src/test/resources/db/dev-migration"
MYSQL_DATA="${PROJECT_ROOT}/data/mysql"
REDIS_DATA="${PROJECT_ROOT}/data/redis"

MYSQL_CONTAINER="wust-dormitory-mysql"
REDIS_CONTAINER="wust-dormitory-redis"
DOCKER_NETWORK="wust-dormitory-network"
FLYWAY_IMAGE="${WUST_DORMITORY_FLYWAY_IMAGE:-flyway/flyway:11.14.1-alpine}"

ASSUME_YES=0
case "${1:-}" in
  "") ;;
  --yes) ASSUME_YES=1 ;;
  *) printf '[错误] 未知参数：%s\n' "$1" >&2; exit 2 ;;
esac

log() { printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
fail() { printf '\n[错误] %s\n' "$*" >&2; exit 1; }

on_error() {
  local code=$?
  printf '\n[失败] 数据库重建未完成，退出码：%s\n' "${code}" >&2
  docker logs --tail 100 "${MYSQL_CONTAINER}" >&2 2>/dev/null || true
  exit "${code}"
}
trap on_error ERR

compose() {
  env \
    -u WUST_DORMITORY_TIMEZONE \
    -u WUST_DORMITORY_SERVER_PORT \
    -u WUST_DORMITORY_MYSQL_IMAGE \
    -u WUST_DORMITORY_DB_HOST \
    -u WUST_DORMITORY_DB_PORT \
    -u WUST_DORMITORY_DB_NAME \
    -u WUST_DORMITORY_DB_USER \
    -u WUST_DORMITORY_DB_PASSWORD \
    -u WUST_DORMITORY_DB_ROOT_PASSWORD \
    -u WUST_DORMITORY_FLYWAY_LOCATIONS \
    -u WUST_DORMITORY_REDIS_IMAGE \
    -u WUST_DORMITORY_REDIS_HOST \
    -u WUST_DORMITORY_REDIS_PORT \
    -u WUST_DORMITORY_REDIS_PASSWORD \
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

validate() {
  command -v docker >/dev/null 2>&1 || fail "未安装 Docker"
  docker compose version >/dev/null 2>&1 || fail "当前 Docker 不支持 Compose"

  [[ -f "${ENV_FILE}" ]] || fail "未找到 .env，请先执行：cp .env.example .env"
  [[ -f "${COMPOSE_FILE}" ]] || fail "未找到 ${COMPOSE_FILE}"
  [[ -d "${FORMAL_SQL}" ]] || fail "未找到正式迁移目录：${FORMAL_SQL}"
  [[ -d "${DEV_SQL}" ]] || fail "未找到开发数据迁移目录：${DEV_SQL}"

  grep -q '请替换为' "${ENV_FILE}" \
    && fail ".env 中仍有占位密码，请先填写真实值"
  grep -qE '^[A-Za-z_][A-Za-z0-9_]*=\$\{[^}]+\}$' "${ENV_FILE}" \
    && fail ".env 中存在未展开的变量表达式，请直接填写真实值"

  local key
  for key in \
    WUST_DORMITORY_MYSQL_IMAGE \
    WUST_DORMITORY_DB_NAME \
    WUST_DORMITORY_DB_USER \
    WUST_DORMITORY_DB_PASSWORD \
    WUST_DORMITORY_DB_ROOT_PASSWORD \
    WUST_DORMITORY_REDIS_IMAGE \
    WUST_DORMITORY_REDIS_PASSWORD; do
    grep -qE "^${key}=.+" "${ENV_FILE}" || fail ".env 缺少：${key}"
  done
}

confirm_reset() {
  [[ "${ASSUME_YES}" -eq 1 ]] && return 0
  cat <<EOF

即将永久删除：
  ${MYSQL_DATA}
  ${REDIS_DATA}

请输入大写 RESET 继续：
EOF
  local answer
  read -r -p "> " answer
  [[ "${answer}" == "RESET" ]] || { echo "已取消。"; exit 0; }
}

wait_healthy() {
  local container="$1"
  local name="$2"
  local status

  for _ in $(seq 1 60); do
    status="$(docker inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "${container}" 2>/dev/null || true)"
    [[ "${status}" == "healthy" ]] && { log "${name} 已健康"; return 0; }
    [[ "${status}" == "unhealthy" || "${status}" == "exited" ]] \
      && fail "${name} 容器状态异常：${status}"
    sleep 2
  done
  fail "${name} 未在预期时间内进入 healthy 状态"
}

reset_data() {
  [[ "${MYSQL_DATA}" == "${PROJECT_ROOT}/data/mysql" ]] || fail "MySQL 数据目录异常"
  [[ "${REDIS_DATA}" == "${PROJECT_ROOT}/data/redis" ]] || fail "Redis 数据目录异常"

  log "停止并删除旧容器"
  compose down --remove-orphans

  log "删除 MySQL 和 Redis 本地数据"
  if ! rm -rf -- "${MYSQL_DATA}" "${REDIS_DATA}"; then
    docker run --rm \
      -v "${PROJECT_ROOT}/data:/workspace-data" \
      alpine:3.20 \
      sh -ec 'rm -rf /workspace-data/mysql /workspace-data/redis'
  fi
  mkdir -p "${MYSQL_DATA}" "${REDIS_DATA}"

  log "启动全新的 MySQL 和 Redis"
  compose up -d mysql redis
  wait_healthy "${MYSQL_CONTAINER}" "MySQL"
  wait_healthy "${REDIS_CONTAINER}" "Redis"
  docker network inspect "${DOCKER_NETWORK}" >/dev/null 2>&1 \
    || fail "未找到 Docker 网络：${DOCKER_NETWORK}"
}

mysql_env() {
  case "$1" in
    database) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_DATABASE"' ;;
    user) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_USER"' ;;
    password) docker exec "${MYSQL_CONTAINER}" sh -ec 'printf "%s" "$MYSQL_PASSWORD"' ;;
    *) fail "未知 MySQL 配置项：$1" ;;
  esac
}

migrate() {
  local database user password
  database="$(mysql_env database)"
  user="$(mysql_env user)"
  password="$(mysql_env password)"

  [[ -n "${database}" && -n "${user}" && -n "${password}" ]] \
    || fail "无法从 MySQL 容器读取数据库连接信息"

  docker image inspect "${FLYWAY_IMAGE}" >/dev/null 2>&1 \
    || docker pull "${FLYWAY_IMAGE}"

  log "使用 Flyway 容器执行 V1～V4 和开发测试数据迁移"
  docker run --rm \
    --network "${DOCKER_NETWORK}" \
    -e "FLYWAY_URL=jdbc:mysql://${MYSQL_CONTAINER}:3306/${database}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
    -e "FLYWAY_USER=${user}" \
    -e "FLYWAY_PASSWORD=${password}" \
    -e "FLYWAY_LOCATIONS=filesystem:/flyway/sql/formal,filesystem:/flyway/sql/dev" \
    -e "FLYWAY_CONNECT_RETRIES=60" \
    -e "FLYWAY_VALIDATE_ON_MIGRATE=true" \
    -e "FLYWAY_CLEAN_DISABLED=true" \
    -v "${FORMAL_SQL}:/flyway/sql/formal:ro" \
    -v "${DEV_SQL}:/flyway/sql/dev:ro" \
    "${FLYWAY_IMAGE}" migrate
}

query() {
  docker exec -i "${MYSQL_CONTAINER}" sh -ec '
    MYSQL_PWD="$MYSQL_PASSWORD" exec mysql \
      --batch --skip-column-names \
      --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" "$MYSQL_DATABASE"
  '
}

verify() {
  local version counts smoking lock_table admin_count

  version="$(query <<'SQL'
SELECT MAX(CAST(version AS UNSIGNED))
FROM flyway_schema_history
WHERE success=1 AND version IS NOT NULL;
SQL
)"
  [[ "${version}" == "4" ]] || fail "Flyway 版本应为 4，实际为：${version}"

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
  [[ "${lock_table}" == "1" ]] || fail "V4 活动批次锁表未建立"

  admin_count="$(query <<'SQL'
SELECT COUNT(*)
FROM app_user
WHERE username='admin' AND account_status='ACTIVE';
SQL
)"
  [[ "${admin_count}" == "1" ]] || fail "管理员测试账号未建立"

  log "数据库结构与测试数据校验通过"
}

summary() {
  cat <<'EOF'

============================================================
数据库与 Docker 基础设施已从零重建
============================================================
本脚本未构建或启动前后端。

数据规模：
  专业：5
  学生：520（男 260、女 260）
  房间：144
  床位：640
  测试批次：1

管理员：
  用户名：admin
  密码：Dormitory@2026

学生示例：
  学号：202600000001
  姓名：测试男生001
  首次登录前需在账号激活页面自行设置密码。
============================================================
EOF
}

main() {
  cd "${PROJECT_ROOT}"
  validate
  confirm_reset
  reset_data
  migrate
  verify
  summary
}

main "$@"
