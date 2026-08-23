#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${WUST_DORMITORY_DB_USER:?WUST_DORMITORY_DB_USER is required}"
: "${WUST_DORMITORY_DB_PASSWORD:?WUST_DORMITORY_DB_PASSWORD is required}"
: "${WUST_DORMITORY_BACKUP_DB_USER:?WUST_DORMITORY_BACKUP_DB_USER is required}"
: "${WUST_DORMITORY_BACKUP_DB_PASSWORD:?WUST_DORMITORY_BACKUP_DB_PASSWORD is required}"

for identifier in "${MYSQL_DATABASE}" "${WUST_DORMITORY_DB_USER}" "${WUST_DORMITORY_BACKUP_DB_USER}"; do
  [[ "${identifier}" =~ ^[A-Za-z0-9_]+$ ]] || { echo "invalid identifier" >&2; exit 1; }
done

escaped_app_password="${WUST_DORMITORY_DB_PASSWORD//\'/\'\'}"
escaped_backup_password="${WUST_DORMITORY_BACKUP_DB_PASSWORD//\'/\'\'}"
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql --protocol=TCP --host "${MYSQL_HOST}" --port 3306 --user root <<SQL
CREATE USER IF NOT EXISTS '${WUST_DORMITORY_DB_USER}'@'%' IDENTIFIED BY '${escaped_app_password}';
ALTER USER '${WUST_DORMITORY_DB_USER}'@'%' IDENTIFIED BY '${escaped_app_password}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${WUST_DORMITORY_DB_USER}'@'%';
CREATE USER IF NOT EXISTS '${WUST_DORMITORY_BACKUP_DB_USER}'@'%' IDENTIFIED BY '${escaped_backup_password}';
ALTER USER '${WUST_DORMITORY_BACKUP_DB_USER}'@'%' IDENTIFIED BY '${escaped_backup_password}';
GRANT SELECT, SHOW VIEW, TRIGGER ON \`${MYSQL_DATABASE}\`.* TO '${WUST_DORMITORY_BACKUP_DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
