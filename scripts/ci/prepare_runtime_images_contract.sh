#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: prepare_runtime_images_contract.sh <env-file>}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pull_helper="${script_dir}/pull_image_with_fallback.sh"
mysql_root_guard="${script_dir}/mysql_root_guard_contract.sh"
skip_nginx="${WUST_DORMITORY_PREPARE_SKIP_NGINX:-0}"
skip_java="${WUST_DORMITORY_PREPARE_SKIP_JAVA:-${skip_nginx}}"
skip_mysql_guard="${WUST_DORMITORY_PREPARE_SKIP_MYSQL_GUARD:-0}"
java_runtime_image="m.daocloud.io/docker.io/library/eclipse-temurin:21-jre-jammy"

[[ -f "${env_file}" ]] || { echo "missing env file: ${env_file}" >&2; exit 1; }
[[ -f "${pull_helper}" ]] || { echo "missing pull helper: ${pull_helper}" >&2; exit 1; }

# The production deploy validates the full .env before this preflight. This public contract also
# supports minimal image-only fixtures, so the guard runs only when the fixture includes the root key.
if [[ "${skip_mysql_guard}" != "1" && "$(basename "${env_file}")" != ".env.example" ]] \
   && grep -q '^[[:space:]]*WUST_DORMITORY_DB_ROOT_PASSWORD=' "${env_file}"; then
  [[ -f "${mysql_root_guard}" ]] || { echo "missing MySQL root guard: ${mysql_root_guard}" >&2; exit 1; }
  deployment_root="$(cd "$(dirname "${env_file}")" && pwd)"
  mkdir -p "${deployment_root}/data/deploy" "${deployment_root}/data/mysql"
  bash "${mysql_root_guard}" \
    "${env_file}" \
    "${deployment_root}/data/deploy/mysql-root-state.env" \
    "${deployment_root}/data/mysql"
fi

read_env_value() {
  local key="$1" value
  value="$(sed -n "s/^${key}=//p" "${env_file}" | tail -n 1)"
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "${value}"
}

variables=(
  WUST_DORMITORY_MYSQL_IMAGE
  WUST_DORMITORY_REDIS_IMAGE
  WUST_DORMITORY_MINIO_IMAGE
  WUST_DORMITORY_MINIO_MC_IMAGE
)
if [[ "${skip_nginx}" != "1" ]]; then
  variables+=(WUST_DORMITORY_NGINX_IMAGE)
fi

for variable in "${variables[@]}"; do
  image="$(read_env_value "${variable}")"
  [[ -n "${image}" ]] || { echo "missing image: ${variable}" >&2; exit 1; }
  bash "${pull_helper}" "${image}"
done

if [[ "${skip_java}" != "1" ]]; then
  bash "${pull_helper}" "${java_runtime_image}"
fi
