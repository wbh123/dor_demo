#!/usr/bin/env bash
set -Eeuo pipefail

# 武汉科技大学学生宿舍智能选择系统
# 本地环境从零重建脚本
#
# 功能：
#   1. 停止旧后端和 Docker 基础设施
#   2. 删除本地 MySQL、Redis 持久化数据
#   3. 重新启动 MySQL、Redis
#   4. 构建后端和前端
#   5. 启动后端，由 Flyway 执行 V1～V4
#   6. 加载开发测试数据
#   7. 校验专业、学生、房间、床位、问卷和测试账号
#
# 使用方式（项目根目录）：
#   chmod +x scripts/dev/reset-local-environment.sh
#   ./scripts/dev/reset-local-environment.sh
#
# 跳过交互确认：
#   ./scripts/dev/reset-local-environment.sh --yes
#
# 跳过前端构建：
#   SKIP_FRONTEND_BUILD=1 ./scripts/dev/reset-local-environment.sh
#
# 警告：
#   本脚本会永久删除项目根目录 data/mysql 和 data/redis 中的全部本地数据。
#   不得在生产环境执行。

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
BACKEND_POM="${PROJECT_ROOT}/backend-java/pom.xml"
BACKEND_JAR="${PROJECT_ROOT}/backend-java/starter/target/Service.jar"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"

MYSQL_DATA_DIR="${PROJECT_ROOT}/data/mysql"
REDIS_DATA_DIR="${PROJECT_ROOT}/data/redis"

RUNTIME_DIR="${PROJECT_ROOT}/run"
LOG_DIR="${PROJECT_ROOT}/logs"
BACKEND_PID_FILE="${RUNTIME_DIR}/wust-dormitory-backend.pid"
BACKEND_LOG="${LOG_DIR}/wust-dormitory-backend.log"

MYSQL_CONTAINER="wust-dormitory-mysql"
REDIS_CONTAINER="wust-dormitory-redis"

FLYWAY_LOCATIONS="classpath:db/migration,filesystem:backend-java/server/src/test/resources/db/dev-migration"

ASSUME_YES=0
if [[ "${1:-}" == "--yes" ]]; then
  ASSUME_YES=1
elif [[ -n "${1:-}" ]]; then
  printf '[错误] 未知参数：%s\n' "$1" >&2
  printf '支持参数：--yes\n' >&2
  exit 2
fi

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

on_error() {
  local exit_code=$?
  printf '\n[失败] 从零重建未完成，退出码：%s\n' "${exit_code}" >&2
  if [[ -f "${BACKEND_LOG}" ]]; then
    printf '\n后端日志最后 120 行：\n' >&2
    tail -n 120 "${BACKEND_LOG}" >&2 || true
  fi
  exit "${exit_code}"
}
trap on_error ERR

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "未找到命令：$1"
}

ensure_safe_data_path() {
  local path="$1"
  case "${path}" in
    "${PROJECT_ROOT}/data/mysql"|"${PROJECT_ROOT}/data/redis")
      ;;
    *)
      fail "拒绝删除非预期目录：${path}"
      ;;
  esac
}

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
    docker compose \
      --env-file "${ENV_FILE}" \
      -f "${COMPOSE_FILE}" \
      "$@"
}

container_status() {
  local container="$1"
  docker inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "${container}" 2>/dev/null || true
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

stop_known_backend() {
  if [[ ! -f "${BACKEND_PID_FILE}" ]]; then
    return 0
  fi

  local pid
  pid="$(cat "${BACKEND_PID_FILE}" 2>/dev/null || true)"

  if [[ ! "${pid}" =~ ^[0-9]+$ ]]; then
    rm -f "${BACKEND_PID_FILE}"
    return 0
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${BACKEND_PID_FILE}"
    return 0
  fi

  local command_line
  command_line="$(ps -p "${pid}" -o args= 2>/dev/null || true)"

  if [[ "${command_line}" != *"Service.jar"* ]]; then
    fail "PID 文件指向的进程不是本项目后端，拒绝终止：PID=${pid}"
  fi

  log "停止旧后端进程 PID=${pid}"
  kill "${pid}" 2>/dev/null || true

  for _ in $(seq 1 20); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      rm -f "${BACKEND_PID_FILE}"
      return 0
    fi
    sleep 1
  done

  warn "后端未正常退出，发送强制终止信号"
  kill -9 "${pid}" 2>/dev/null || true
  rm -f "${BACKEND_PID_FILE}"
}

check_port_8080() {
  if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -qE '[:.]8080[[:space:]]'; then
    fail "端口 8080 已被其他进程占用。请先停止该进程后重新运行。"
  fi
}

validate_environment() {
  [[ -f "${ENV_FILE}" ]] \
    || fail "未找到 ${ENV_FILE}。请先执行：cp .env.example .env"

  [[ -f "${COMPOSE_FILE}" ]] \
    || fail "未找到 Docker Compose 文件：${COMPOSE_FILE}"

  [[ -f "${BACKEND_POM}" ]] \
    || fail "未找到 Maven 工程：${BACKEND_POM}"

  [[ -d "${FRONTEND_DIR}" ]] \
    || fail "未找到前端目录：${FRONTEND_DIR}"

  if grep -q '请替换为' "${ENV_FILE}"; then
    fail ".env 中仍有“请替换为...”占位配置，请先填写 MySQL 和 Redis 密码。"
  fi

  if grep -qE '^[A-Za-z_][A-Za-z0-9_]*=\$\{[^}]+\}$' "${ENV_FILE}"; then
    fail ".env 中存在未展开的变量表达式，例如 VAR=\${VAR}。请直接填写真实配置值。"
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
    if ! grep -qE "^${variable_name}=.+" "${ENV_FILE}"; then
      fail ".env 缺少有效配置：${variable_name}"
    fi
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

删除后将重新创建数据库和测试数据。
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

reset_persistent_data() {
  ensure_safe_data_path "${MYSQL_DATA_DIR}"
  ensure_safe_data_path "${REDIS_DATA_DIR}"

  log "停止 MySQL 和 Redis 容器"
  compose down --remove-orphans

  log "删除本地 MySQL 和 Redis 数据"
  if ! rm -rf -- "${MYSQL_DATA_DIR}" "${REDIS_DATA_DIR}"; then
    warn "当前用户无法直接删除数据目录，改用临时容器清理"
    docker run --rm \
      -v "${PROJECT_ROOT}/data:/workspace-data" \
      alpine:3.20 \
      sh -ec 'rm -rf /workspace-data/mysql /workspace-data/redis'
  fi

  mkdir -p \
    "${MYSQL_DATA_DIR}" \
    "${REDIS_DATA_DIR}" \
    "${RUNTIME_DIR}" \
    "${LOG_DIR}"
}

start_infrastructure() {
  log "启动全新的 MySQL 和 Redis"
  compose up -d mysql redis

  wait_for_container "${MYSQL_CONTAINER}" "MySQL" 60
  wait_for_container "${REDIS_CONTAINER}" "Redis" 60
}

build_backend() {
  log "构建 Java 后端"
  mvn \
    -f "${BACKEND_POM}" \
    --batch-mode \
    --no-transfer-progress \
    clean package \
    -DskipTests

  [[ -f "${BACKEND_JAR}" ]] \
    || fail "后端构建结束，但未找到：${BACKEND_JAR}"
}

build_frontend() {
  if [[ "${SKIP_FRONTEND_BUILD:-0}" == "1" ]]; then
    warn "已通过 SKIP_FRONTEND_BUILD=1 跳过前端构建"
    return 0
  fi

  require_command npm

  log "安装前端依赖"
  (
    cd "${FRONTEND_DIR}"
    npm install --no-audit --no-fund
  )

  log "生成 OpenAPI 类型并构建前端"
  (
    cd "${FRONTEND_DIR}"
    npm run build
  )
}

start_backend_with_test_data() {
  log "启动后端并执行 Flyway V1～V4及开发测试数据迁移"

  (
    cd "${PROJECT_ROOT}"
    nohup env \
      -u WUST_DORMITORY_TIMEZONE \
      -u WUST_DORMITORY_SERVER_PORT \
      -u WUST_DORMITORY_DB_HOST \
      -u WUST_DORMITORY_DB_PORT \
      -u WUST_DORMITORY_DB_NAME \
      -u WUST_DORMITORY_DB_USER \
      -u WUST_DORMITORY_DB_PASSWORD \
      -u WUST_DORMITORY_DB_ROOT_PASSWORD \
      -u WUST_DORMITORY_REDIS_HOST \
      -u WUST_DORMITORY_REDIS_PORT \
      -u WUST_DORMITORY_REDIS_PASSWORD \
      WUST_DORMITORY_FLYWAY_LOCATIONS="${FLYWAY_LOCATIONS}" \
      java -jar "${BACKEND_JAR}" \
      > "${BACKEND_LOG}" 2>&1 &
    echo $! > "${BACKEND_PID_FILE}"
  )

  local pid
  pid="$(cat "${BACKEND_PID_FILE}")"

  for _ in $(seq 1 90); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      fail "后端进程已提前退出"
    fi

    if curl --fail --silent \
      "http://127.0.0.1:8080/actuator/health" \
      > "${RUNTIME_DIR}/health.json"; then
      log "后端已启动"
      cat "${RUNTIME_DIR}/health.json"
      printf '\n'
      return 0
    fi

    sleep 2
  done

  fail "后端未在预期时间内通过健康检查"
}

query_database() {
  local sql="$1"

  docker exec -i "${MYSQL_CONTAINER}" sh -ec '
    MYSQL_PWD="$MYSQL_PASSWORD" \
    exec mysql \
      --batch \
      --skip-column-names \
      --default-character-set=utf8mb4 \
      -u"$MYSQL_USER" \
      "$MYSQL_DATABASE"
  ' <<< "${sql}"
}

verify_database() {
  log "校验数据库结构与测试数据"

  local flyway_version
  flyway_version="$(query_database \
    "SELECT MAX(CAST(version AS UNSIGNED))
     FROM flyway_schema_history
     WHERE success = 1
       AND version IS NOT NULL;")"

  [[ "${flyway_version}" == "4" ]] \
    || fail "Flyway版本错误，预期4，实际：${flyway_version}"

  local counts
  counts="$(query_database \
    "SELECT CONCAT_WS(',',
       (SELECT COUNT(*) FROM major WHERE id BETWEEN 1 AND 5),
       (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520),
       (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520 AND gender='M'),
       (SELECT COUNT(*) FROM student WHERE id BETWEEN 1 AND 520 AND gender='F'),
       (SELECT COUNT(*) FROM room WHERE id BETWEEN 1 AND 144),
       (SELECT COUNT(*) FROM bed WHERE id BETWEEN 1 AND 640),
       (SELECT COUNT(*) FROM selection_batch WHERE id=1)
     );")"

  [[ "${counts}" == "5,520,260,260,144,640,1" ]] \
    || fail "测试数据数量异常，预期5,520,260,260,144,640,1，实际：${counts}"

  local smoking_options
  smoking_options="$(query_database \
    "SELECT GROUP_CONCAT(qo.option_code ORDER BY qo.sort_order)
     FROM questionnaire_option qo
     JOIN questionnaire_question qq ON qq.id = qo.question_id
     WHERE qq.question_code='SMOKING_ACCEPTANCE';")"

  [[ "${smoking_options}" == "ACCEPT,REJECT,ANY" ]] \
    || fail "吸烟偏好选项异常，实际：${smoking_options}"

  local admin_count
  admin_count="$(query_database \
    "SELECT COUNT(*)
     FROM app_user
     WHERE username='admin'
       AND account_status='ACTIVE';")"

  [[ "${admin_count}" == "1" ]] \
    || fail "管理员测试账号未正确建立"

  log "数据库结构和测试数据校验通过"
}

print_summary() {
  cat <<EOF

============================================================
本地环境已从零重建
============================================================

基础设施：
  MySQL：healthy
  Redis：healthy

数据库：
  Flyway正式迁移：V1～V4
  专业：5
  学生：520（男生260、女生260）
  房间：144
  床位：640
  测试批次：1

后端：
  地址：http://127.0.0.1:8080
  健康检查：http://127.0.0.1:8080/actuator/health
  日志：${BACKEND_LOG}
  PID文件：${BACKEND_PID_FILE}

管理员登录：
  用户名：admin
  密码：Dormitory@2026

学生测试账号：
  学号：202600000001
  姓名：测试男生001

学生没有统一初始密码，首次使用时请在“账号激活”页面输入：
  学号：202600000001
  姓名：测试男生001
  密码：自行设置

随后使用“学号 + 自定义密码”登录。

启动前端开发服务：
  cd ${FRONTEND_DIR}
  npm run dev

停止本脚本启动的后端：
  kill \$(cat ${BACKEND_PID_FILE})

停止基础设施：
  bash scripts/dev/start-infra.sh down
============================================================
EOF
}

main() {
  cd "${PROJECT_ROOT}"

  require_command docker
  require_command mvn
  require_command java
  require_command curl

  docker compose version >/dev/null 2>&1 \
    || fail "当前 Docker 不支持 docker compose。"

  validate_environment
  confirm_destruction
  stop_known_backend
  check_port_8080
  reset_persistent_data
  start_infrastructure
  build_backend
  build_frontend
  start_backend_with_test_data
  verify_database
  print_summary
}

main "$@"
