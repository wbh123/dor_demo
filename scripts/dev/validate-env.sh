#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${repository_root}/.env"
example_file="${repository_root}/.env.example"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少根目录 .env。请执行：cp .env.example .env" >&2
  exit 1
fi

declare -A env_values=()
line_number=0
while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
  line_number=$((line_number + 1))
  line="${raw_line%$'\r'}"
  trimmed="${line#"${line%%[![:space:]]*}"}"

  if [[ -z "${trimmed}" || "${trimmed}" == \#* ]]; then
    continue
  fi

  if [[ "${line}" != *=* ]]; then
    echo ".env 第 ${line_number} 行格式错误，必须使用 KEY=VALUE" >&2
    exit 1
  fi

  key="${line%%=*}"
  value="${line#*=}"
  key="${key//[[:space:]]/}"

  if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo ".env 第 ${line_number} 行变量名不合法：${key}" >&2
    exit 1
  fi

  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi

  env_values["${key}"]="${value}"
done < "${env_file}"

required_variables=(
  WUST_DORMITORY_TIMEZONE
  WUST_DORMITORY_SERVER_PORT
  WUST_DORMITORY_MYSQL_IMAGE
  WUST_DORMITORY_DB_HOST
  WUST_DORMITORY_DB_PORT
  WUST_DORMITORY_DB_NAME
  WUST_DORMITORY_DB_USER
  WUST_DORMITORY_DB_PASSWORD
  WUST_DORMITORY_DB_ROOT_PASSWORD
  WUST_DORMITORY_REDIS_IMAGE
  WUST_DORMITORY_REDIS_HOST
  WUST_DORMITORY_REDIS_PORT
  WUST_DORMITORY_REDIS_PASSWORD
)

errors=0
for variable in "${required_variables[@]}"; do
  value="${env_values[${variable}]-}"
  if [[ -z "${value}" ]]; then
    echo "缺少配置：${variable}" >&2
    errors=$((errors + 1))
  elif [[ "${value}" == *"请替换"* ]]; then
    echo "配置仍为模板占位值：${variable}" >&2
    errors=$((errors + 1))
  fi
done

for variable in WUST_DORMITORY_SERVER_PORT WUST_DORMITORY_DB_PORT WUST_DORMITORY_REDIS_PORT; do
  value="${env_values[${variable}]-}"
  if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( value < 1 || value > 65535 )); then
    echo "端口配置不合法：${variable}=${value}" >&2
    errors=$((errors + 1))
  fi
done

if [[ "${env_values[WUST_DORMITORY_DB_USER]-}" == "root" ]]; then
  echo "业务数据库账号不能使用 root" >&2
  errors=$((errors + 1))
fi

if [[ "${env_values[WUST_DORMITORY_DB_PASSWORD]-}" == "${env_values[WUST_DORMITORY_DB_ROOT_PASSWORD]-}" ]]; then
  echo "业务数据库密码与 root 密码不能相同" >&2
  errors=$((errors + 1))
fi

redis_password="${env_values[WUST_DORMITORY_REDIS_PASSWORD]-}"
if (( ${#redis_password} < 8 )); then
  echo "Redis 密码至少需要 8 个字符" >&2
  errors=$((errors + 1))
fi

if (( errors > 0 )); then
  echo "配置校验失败，共 ${errors} 项。参考：${example_file}" >&2
  exit 1
fi

echo "配置校验通过：${env_file}"
echo "- MySQL：${env_values[WUST_DORMITORY_DB_HOST]}:${env_values[WUST_DORMITORY_DB_PORT]}/${env_values[WUST_DORMITORY_DB_NAME]}"
echo "- Redis：${env_values[WUST_DORMITORY_REDIS_HOST]}:${env_values[WUST_DORMITORY_REDIS_PORT]}"
echo "- 服务端口：${env_values[WUST_DORMITORY_SERVER_PORT]}"
