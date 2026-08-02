#!/usr/bin/env bash
set -Eeuo pipefail

# 本地 MySQL 与 Redis 数据清空脚本。
#
# 本脚本只负责：
#   1. 停止项目 MySQL、Redis 容器；
#   2. 删除项目根目录 data/mysql 与 data/redis；
#   3. 重新创建并启动空的 MySQL、Redis 容器；
#   4. 等待两个容器进入 healthy 状态。
#
# 本脚本不会：
#   - 执行 Flyway 迁移；
#   - 导入 backend-java/docs/sql/schema.sql；
#   - 创建管理员账号；
#   - 导入 backend-java/docs/sql/reset_and_seed_test_data.sql；
#   - 构建或启动前后端。
#
# 警告：会永久删除本项目本地 MySQL 和 Redis 数据。
#
# 用法：
#   bash scripts/dev/reset-local-environment.sh
#   bash scripts/dev/reset-local-environment.sh --yes

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
MYSQL_DATA_DIR="${PROJECT_ROOT}/data/mysql"
REDIS_DATA_DIR="${PROJECT_ROOT}/data/redis"

MYSQL_CONTAINER="wust-dormitory-mysql"
REDIS_CONTAINER="wust-dormitory-redis"

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
  [[ -f "${ENV_FILE}" ]] || fail "未找到 ${ENV_FILE}。请先执行：cp .env.example .env"
  [[ -f "${COMPOSE_FILE}" ]] || fail "未找到 Docker Compose 文件：${COMPOSE_FILE}"

  if grep -q '请替换为' "${ENV_FILE}"; then
    fail ".env 中仍有“请替换为...”占位配置，请先填写 MySQL 和 Redis 密码。"
  fi
  if grep -qE '^[A-Za-z_][A-Za-z0-9_]*=\$\{[^}]+\}$' "${ENV_FILE}"; then
    fail ".env 中存在未展开的变量表达式，请直接填写真实配置值。"
  fi
}

confirm_destruction() {
  if [[ "${ASSUME_YES}" -eq 1 ]]; then
    return 0
  fi

  cat <<EOF

============================================================
危险操作确认
============================================================

即将永久删除本项目本地数据：

  ${MYSQL_DATA_DIR}
  ${REDIS_DATA_DIR}

删除后只会重新启动空的 MySQL 与 Redis。
不会执行数据库迁移，也不会导入测试数据。

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
  log "停止项目容器并移除孤立容器"
  compose down --remove-orphans

  log "删除 MySQL 和 Redis 持久化目录"
  remove_data_directory "${MYSQL_DATA_DIR}"
  remove_data_directory "${REDIS_DATA_DIR}"
  mkdir -p "${MYSQL_DATA_DIR}" "${REDIS_DATA_DIR}"

  log "启动空的 MySQL 和 Redis"
  compose up -d mysql redis
  wait_for_container "${MYSQL_CONTAINER}" "MySQL" 60
  wait_for_container "${REDIS_CONTAINER}" "Redis" 60
}

print_summary() {
  cat <<EOF

============================================================
MySQL 与 Redis 本地数据已清空
============================================================

容器状态：
  MySQL：healthy
  Redis：healthy

当前数据库仅包含 MySQL 镜像初始化时创建的空数据库。
本脚本没有执行 Flyway、schema.sql 或测试数据导入。

后续操作请按需要单独执行：

  1. 建议开发环境通过 Flyway 执行正式迁移 V1～V16；或手动导入：
     backend-java/docs/sql/schema.sql

  2. 导入 500 人测试数据：
     backend-java/docs/sql/reset_and_seed_test_data.sql

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
  print_summary
}

main "$@"
