#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${repository_root}/.env"
compose_file="${repository_root}/docker/docker-compose.yml"
validate_script="${repository_root}/scripts/dev/validate-env.sh"
action="${1:-up}"

compose() {
  docker compose --env-file "${env_file}" -f "${compose_file}" "$@"
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "未找到 Docker，请先安装 Docker Engine 或 Docker Desktop。" >&2
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    echo "未找到 Docker Compose 插件。" >&2
    exit 1
  fi
}

prepare() {
  require_docker
  "${validate_script}"
  mkdir -p "${repository_root}/data/mysql" "${repository_root}/data/redis"
  compose config >/dev/null
}

wait_for_health() {
  local service container_id status attempt
  for service in mysql redis; do
    container_id="$(compose ps -q "${service}")"
    if [[ -z "${container_id}" ]]; then
      echo "服务未创建：${service}" >&2
      exit 1
    fi

    for attempt in $(seq 1 60); do
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
      case "${status}" in
        healthy)
          echo "${service} 已健康"
          break
          ;;
        unhealthy|exited|dead)
          echo "${service} 启动失败，当前状态：${status}" >&2
          compose logs --no-color "${service}" >&2
          exit 1
          ;;
      esac
      if (( attempt == 60 )); then
        echo "等待 ${service} 健康检查超时，当前状态：${status}" >&2
        compose logs --no-color "${service}" >&2
        exit 1
      fi
      sleep 2
    done
  done
}

case "${action}" in
  up)
    prepare
    compose up -d
    wait_for_health
    compose ps
    ;;
  down)
    require_docker
    if [[ -f "${env_file}" ]]; then
      compose down
    else
      echo "缺少根目录 .env，无法确定 Compose 配置。" >&2
      exit 1
    fi
    ;;
  restart)
    prepare
    compose up -d --force-recreate mysql redis
    wait_for_health
    compose ps
    ;;
  status)
    prepare
    compose ps
    ;;
  logs)
    prepare
    compose logs -f mysql redis
    ;;
  config)
    prepare
    compose config
    ;;
  *)
    echo "用法：$0 {up|down|restart|status|logs|config}" >&2
    exit 2
    ;;
esac
