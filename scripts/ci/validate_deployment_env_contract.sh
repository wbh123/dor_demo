#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: validate_deployment_env_contract.sh <env-file>}"
[[ -f "${env_file}" ]] || { echo "missing env file: ${env_file}" >&2; exit 1; }

read_env_value() {
  local key="$1" fallback="${2:-}" value first last
  value="$(sed -n -E "s/^[[:space:]]*${key}=//p" "${env_file}" | tail -n 1)"
  [[ -n "${value}" ]] || value="${fallback}"
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "${value}"
}

errors=0
for variable in WUST_DORMITORY_DB_NAME WUST_DORMITORY_DB_USER WUST_DORMITORY_BACKUP_DB_USER; do
  value="$(read_env_value "${variable}")"
  if [[ ! "${value}" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "invalid database identifier: ${variable}=${value}" >&2
    errors=$((errors + 1))
  fi
done

app_user="$(read_env_value WUST_DORMITORY_DB_USER)"
backup_user="$(read_env_value WUST_DORMITORY_BACKUP_DB_USER)"
if [[ "${app_user}" == "root" || "${backup_user}" == "root" ]]; then
  echo "database application/backup accounts must not be root" >&2
  errors=$((errors + 1))
fi
if [[ -n "${app_user}" && "${app_user}" == "${backup_user}" ]]; then
  echo "WUST_DORMITORY_DB_USER and WUST_DORMITORY_BACKUP_DB_USER must be distinct" >&2
  errors=$((errors + 1))
fi

declare -A seen_ports=()
port_entries=(
  "WUST_DORMITORY_DB_PUBLISHED_PORT:3306"
  "WUST_DORMITORY_REDIS_PUBLISHED_PORT:6379"
  "WUST_DORMITORY_MINIO_API_PORT:19000"
  "WUST_DORMITORY_MINIO_CONSOLE_PORT:19001"
  "WUST_DORMITORY_BACKEND_PUBLISHED_PORT:8080"
  "WUST_DORMITORY_NGINX_PORT:80"
)
for entry in "${port_entries[@]}"; do
  variable="${entry%%:*}"
  fallback="${entry#*:}"
  value="$(read_env_value "${variable}" "${fallback}")"
  if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( value < 1 || value > 65535 )); then
    echo "invalid published port: ${variable}=${value}" >&2
    errors=$((errors + 1))
    continue
  fi
  if [[ -n "${seen_ports[${value}]-}" ]]; then
    echo "published port conflict: ${seen_ports[${value}]} and ${variable} both use ${value}" >&2
    errors=$((errors + 1))
  else
    seen_ports["${value}"]="${variable}"
  fi
done

(( errors == 0 )) || exit 1
