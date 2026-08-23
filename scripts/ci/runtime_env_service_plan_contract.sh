#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: runtime_env_service_plan_contract.sh <env-file> <mysql> <redis> <minio> <backend> <nginx>}"
previous_mysql="${2:-}"
previous_redis="${3:-}"
previous_minio="${4:-}"
previous_backend="${5:-}"
previous_nginx="${6:-}"
[[ -f "${env_file}" ]] || { echo "missing env file: ${env_file}" >&2; exit 1; }

hash_regex() {
  local regex="$1"
  awk -v re="${regex}" '
    $0 !~ /^[[:space:]]*#/ && $0 ~ re { print }
  ' "${env_file}" | sha256sum | awk '{print $1}'
}

hash_backend() {
  awk '
    $0 ~ /^[[:space:]]*#/ { next }
    $0 !~ /^[[:space:]]*WUST_DORMITORY_[A-Za-z0-9_]*=/ { next }
    $0 ~ /^[[:space:]]*WUST_DORMITORY_(MYSQL_IMAGE|DB_PUBLISHED_PORT|DB_ROOT_PASSWORD|REDIS_IMAGE|REDIS_PUBLISHED_PORT|MINIO_IMAGE|MINIO_MC_IMAGE|MINIO_API_PORT|MINIO_CONSOLE_PORT|MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|NGINX_IMAGE|NGINX_PORT)=/ { next }
    { print }
  ' "${env_file}" | sha256sum | awk '{print $1}'
}

mysql_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|MYSQL_IMAGE|DB_NAME|DB_USER|DB_PASSWORD|DB_ROOT_PASSWORD|DB_PUBLISHED_PORT|BACKUP_DB_USER|BACKUP_DB_PASSWORD)=')"
redis_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|REDIS_IMAGE|REDIS_PASSWORD|REDIS_PUBLISHED_PORT)=')"
minio_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|MINIO_IMAGE|MINIO_MC_IMAGE|MINIO_API_PORT|MINIO_CONSOLE_PORT|MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|OBJECT_STORAGE_ACCESS_KEY|OBJECT_STORAGE_SECRET_KEY|BACKUP_STORAGE_ACCESS_KEY|BACKUP_STORAGE_SECRET_KEY|MINIO_TEMP_BUCKET|MINIO_ASSET_BUCKET|MINIO_EXPORT_BUCKET|MINIO_APP_BUCKET|MINIO_BACKUP_BUCKET)=')"
backend_hash="$(hash_backend)"
nginx_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|NGINX_IMAGE|NGINX_PORT)=')"

changed() {
  [[ "$1" == "$2" ]] && printf '0' || printf '1'
}

cat <<EOF
MYSQL_RUNTIME_ENV_SHA256=${mysql_hash}
REDIS_RUNTIME_ENV_SHA256=${redis_hash}
MINIO_RUNTIME_ENV_SHA256=${minio_hash}
BACKEND_RUNTIME_ENV_SHA256=${backend_hash}
NGINX_RUNTIME_ENV_SHA256=${nginx_hash}
MYSQL_CONFIG_CHANGED=$(changed "${previous_mysql}" "${mysql_hash}")
REDIS_CONFIG_CHANGED=$(changed "${previous_redis}" "${redis_hash}")
MINIO_CONFIG_CHANGED=$(changed "${previous_minio}" "${minio_hash}")
BACKEND_CONFIG_CHANGED=$(changed "${previous_backend}" "${backend_hash}")
NGINX_CONFIG_CHANGED=$(changed "${previous_nginx}" "${nginx_hash}")
EOF
