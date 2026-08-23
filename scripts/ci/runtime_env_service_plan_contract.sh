#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: runtime_env_service_plan_contract.sh <env-file> <mysql-server> <mysql-account> <redis> <minio-server> <minio-init> <backend> <nginx>}"
previous_mysql_server="${2:-}"
previous_mysql_account="${3:-}"
previous_redis="${4:-}"
previous_minio_server="${5:-}"
previous_minio_init="${6:-}"
previous_backend="${7:-}"
previous_nginx="${8:-}"
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

mysql_server_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|MYSQL_IMAGE|DB_ROOT_PASSWORD|DB_PUBLISHED_PORT)=')"
mysql_account_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(DB_NAME|DB_USER|DB_PASSWORD|DB_ROOT_PASSWORD|BACKUP_DB_USER|BACKUP_DB_PASSWORD)=')"
redis_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|REDIS_IMAGE|REDIS_PASSWORD|REDIS_PUBLISHED_PORT)=')"
minio_server_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|MINIO_IMAGE|MINIO_API_PORT|MINIO_CONSOLE_PORT|MINIO_ROOT_USER|MINIO_ROOT_PASSWORD)=')"
minio_init_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(MINIO_MC_IMAGE|MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|OBJECT_STORAGE_ACCESS_KEY|OBJECT_STORAGE_SECRET_KEY|BACKUP_STORAGE_ACCESS_KEY|BACKUP_STORAGE_SECRET_KEY|MINIO_TEMP_BUCKET|MINIO_ASSET_BUCKET|MINIO_EXPORT_BUCKET|MINIO_APP_BUCKET|MINIO_BACKUP_BUCKET)=')"
backend_hash="$(hash_backend)"
nginx_hash="$(hash_regex '^[[:space:]]*WUST_DORMITORY_(TIMEZONE|NGINX_IMAGE|NGINX_PORT)=')"

changed() {
  [[ "$1" == "$2" ]] && printf '0' || printf '1'
}

cat <<EOF
MYSQL_SERVER_ENV_SHA256=${mysql_server_hash}
MYSQL_ACCOUNT_ENV_SHA256=${mysql_account_hash}
REDIS_RUNTIME_ENV_SHA256=${redis_hash}
MINIO_SERVER_ENV_SHA256=${minio_server_hash}
MINIO_INIT_ENV_SHA256=${minio_init_hash}
BACKEND_RUNTIME_ENV_SHA256=${backend_hash}
NGINX_RUNTIME_ENV_SHA256=${nginx_hash}
MYSQL_SERVER_CHANGED=$(changed "${previous_mysql_server}" "${mysql_server_hash}")
MYSQL_ACCOUNT_CHANGED=$(changed "${previous_mysql_account}" "${mysql_account_hash}")
REDIS_CONFIG_CHANGED=$(changed "${previous_redis}" "${redis_hash}")
MINIO_SERVER_CHANGED=$(changed "${previous_minio_server}" "${minio_server_hash}")
MINIO_INIT_CHANGED=$(changed "${previous_minio_init}" "${minio_init_hash}")
BACKEND_CONFIG_CHANGED=$(changed "${previous_backend}" "${backend_hash}")
NGINX_CONFIG_CHANGED=$(changed "${previous_nginx}" "${nginx_hash}")
EOF
