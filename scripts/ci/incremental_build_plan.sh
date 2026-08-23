#!/usr/bin/env bash
set -euo pipefail

base="${1:?usage: incremental_build_plan.sh <base> <head>}"
head="${2:?usage: incremental_build_plan.sh <base> <head>}"

BACKEND_COMPILE=0
BACKEND_RUNTIME=0
FRONTEND_BUILD=0
APP_BUILD=0
NGINX_RESTART=0
MYSQL_RECREATE=0
REDIS_RECREATE=0
MINIO_RECREATE=0
TOOLCHAIN_REBUILD=0

while IFS= read -r path; do
  [[ -z "${path}" ]] && continue

  case "${path}" in
    backend-java/pom.xml|backend-java/build-support/*|backend-java/*/pom.xml|backend-java/*/src/main/java/*|backend-java/*/src/main/resources/*|scripts/deploy/build-backend.sh)
      BACKEND_COMPILE=1
      BACKEND_RUNTIME=1
      ;;
  esac

  case "${path}" in
    backend-java/model/src/main/resources/*|scripts/deploy/build-frontend.sh)
      FRONTEND_BUILD=1
      ;;
  esac

  case "${path}" in
    backend-java/model/src/main/resources/mobile/*|scripts/deploy/build-app.sh|scripts/deploy/prepare-gradle-distribution.sh)
      APP_BUILD=1
      ;;
  esac

  case "${path}" in
    frontend/*)
      FRONTEND_BUILD=1
      ;;
    app/*|config/*|assert/*|scripts/mobile/*|version.json)
      APP_BUILD=1
      ;;
    scripts/ops/*|backend-java/docs/sql/*|docker/backend/*)
      BACKEND_RUNTIME=1
      ;;
    .dockerignore|docker/common/*)
      BACKEND_RUNTIME=1
      TOOLCHAIN_REBUILD=1
      ;;
    docker/nginx/*)
      NGINX_RESTART=1
      ;;
    docker/mysql/*)
      MYSQL_RECREATE=1
      ;;
    docker/redis/*)
      REDIS_RECREATE=1
      ;;
    docker/minio/*)
      MINIO_RECREATE=1
      ;;
    docker/toolchain/*|deploy/maven/*|scripts/deploy/toolchain.sh)
      TOOLCHAIN_REBUILD=1
      ;;
    docker/docker-compose.yml)
      BACKEND_RUNTIME=1
      NGINX_RESTART=1
      MYSQL_RECREATE=1
      REDIS_RECREATE=1
      MINIO_RECREATE=1
      ;;
  esac
done < <(git diff --name-only "${base}" "${head}")

cat <<EOF
BACKEND_COMPILE=${BACKEND_COMPILE}
BACKEND_RUNTIME=${BACKEND_RUNTIME}
FRONTEND_BUILD=${FRONTEND_BUILD}
APP_BUILD=${APP_BUILD}
NGINX_RESTART=${NGINX_RESTART}
MYSQL_RECREATE=${MYSQL_RECREATE}
REDIS_RECREATE=${REDIS_RECREATE}
MINIO_RECREATE=${MINIO_RECREATE}
TOOLCHAIN_REBUILD=${TOOLCHAIN_REBUILD}
EOF
