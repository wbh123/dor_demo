#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: mysql_root_guard_contract.sh <env-file> <state-file> <mysql-data-dir>}"
state_file="${2:?usage: mysql_root_guard_contract.sh <env-file> <state-file> <mysql-data-dir>}"
data_dir="${3:?usage: mysql_root_guard_contract.sh <env-file> <state-file> <mysql-data-dir>}"
container_name="${WUST_DORMITORY_MYSQL_CONTAINER_NAME:-wust-dormitory-mysql}"

[[ -f "${env_file}" ]] || { echo "missing env file: ${env_file}" >&2; exit 1; }

read_env_value() {
  local key="$1" value first last
  value="$(sed -n -E "s/^[[:space:]]*${key}=//p" "${env_file}" | tail -n 1)"
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "${value}"
}

load_state_value() {
  local key="$1"
  [[ -f "${state_file}" ]] || return 0
  sed -n "s/^${key}=//p" "${state_file}" | tail -n 1
}

current_password="$(read_env_value WUST_DORMITORY_DB_ROOT_PASSWORD)"
[[ -n "${current_password}" ]] || { echo "MySQL root password is empty" >&2; exit 1; }
current_hash="$(printf '%s' "${current_password}" | sha256sum | awk '{print $1}')"
previous_hash="$(load_state_value MYSQL_ROOT_PASSWORD_SHA256)"

if [[ -n "${previous_hash}" && "${previous_hash}" == "${current_hash}" ]]; then
  printf 'CURRENT_MYSQL_ROOT_PASSWORD_SHA256=%s\n' "${current_hash}"
  exit 0
fi

data_nonempty=0
if [[ -d "${data_dir}" ]] && find "${data_dir}" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
  data_nonempty=1
fi

container_exists=0
if command -v docker >/dev/null 2>&1 && docker inspect "${container_name}" >/dev/null 2>&1; then
  container_exists=1
fi

if (( container_exists == 1 )); then
  container_password="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container_name}" \
    | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | tail -n 1)"
  if [[ "${container_password}" == "${current_password}" ]]; then
    printf 'CURRENT_MYSQL_ROOT_PASSWORD_SHA256=%s\n' "${current_hash}"
    exit 0
  fi
  if docker exec -e "MYSQL_PWD=${current_password}" "${container_name}" \
      mysql --protocol=TCP --host 127.0.0.1 --port 3306 --user root --batch --skip-column-names -e 'SELECT 1' \
      >/dev/null 2>&1; then
    printf 'CURRENT_MYSQL_ROOT_PASSWORD_SHA256=%s\n' "${current_hash}"
    exit 0
  fi
  echo "MySQL root password changed, but the running database does not accept the new credential." >&2
  exit 1
fi

if (( data_nonempty == 1 )); then
  echo "MySQL root password cannot be verified because persistent data exists but the previous MySQL container is missing." >&2
  exit 1
fi

printf 'CURRENT_MYSQL_ROOT_PASSWORD_SHA256=%s\n' "${current_hash}"
