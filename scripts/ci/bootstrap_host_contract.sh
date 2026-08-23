#!/usr/bin/env bash
set -euo pipefail

dry_run="${WUST_DORMITORY_BOOTSTRAP_DRY_RUN:-0}"
os_release_file="${WUST_DORMITORY_OS_RELEASE_FILE:-/etc/os-release}"
bootstrap_user="${WUST_DORMITORY_BOOTSTRAP_USER:-${SUDO_USER:-${USER:-}}}"
continue_script=""
continue_args=()

if [[ "${1:-}" == "--exec" ]]; then
  continue_script="${2:?--exec requires a script path}"
  shift 2
  [[ "${1:-}" == "--" ]] && shift
  continue_args=("$@")
elif (( $# > 0 )); then
  echo "用法：bootstrap_host_contract.sh [--exec SCRIPT -- ARGS...]" >&2
  exit 2
fi

[[ -f "${os_release_file}" ]] || { echo "缺少系统信息：${os_release_file}" >&2; exit 1; }
# shellcheck disable=SC1090
source "${os_release_file}"
distro="${ID:-}"
case "${distro}" in
  ubuntu|debian) ;;
  *) echo "宿主机自动安装仅支持 Ubuntu/Debian，当前系统：${distro:-unknown}" >&2; exit 1 ;;
esac

codename="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
[[ -n "${codename}" ]] || { echo "无法识别系统 codename" >&2; exit 1; }

if [[ "${dry_run}" == "1" ]]; then
  architecture="${WUST_DORMITORY_BOOTSTRAP_ARCH:-amd64}"
else
  command -v dpkg >/dev/null 2>&1 || { echo "缺少 dpkg，无法自动安装 Docker" >&2; exit 1; }
  architecture="$(dpkg --print-architecture)"
fi

sudo_prefix=()
if [[ "$(id -u)" != "0" ]]; then
  if [[ "${dry_run}" == "1" ]]; then
    sudo_prefix=(sudo)
  else
    command -v sudo >/dev/null 2>&1 || { echo "当前用户不是 root，且系统没有 sudo，无法自动安装 Docker" >&2; exit 1; }
    sudo -v
    sudo_prefix=(sudo)
  fi
fi

print_command() {
  printf '  '
  printf '%q ' "$@"
  printf '\n'
}

run_root() {
  if [[ "${dry_run}" == "1" ]]; then
    print_command "${sudo_prefix[@]}" "$@"
    return 0
  fi
  "${sudo_prefix[@]}" "$@"
}

run_user() {
  if [[ "${dry_run}" == "1" ]]; then
    print_command "$@"
    return 0
  fi
  "$@"
}

apt_root="${WUST_DORMITORY_APT_ROOT:-/etc/apt}"
host_apt_backup_root=""
host_apt_modified=0

backup_host_apt_sources() {
  [[ "${dry_run}" == "1" ]] && return 0
  host_apt_backup_root="$(mktemp -d)"
  if [[ -f "${apt_root}/sources.list" ]]; then
    cp -a "${apt_root}/sources.list" "${host_apt_backup_root}/sources.list"
  fi
  if [[ -d "${apt_root}/sources.list.d" ]]; then
    cp -a "${apt_root}/sources.list.d" "${host_apt_backup_root}/sources.list.d"
  fi
}

configure_mainland_host_apt() {
  echo "宿主机基础 APT 临时优先使用阿里云镜像。"
  if [[ "${distro}" == "ubuntu" ]]; then
    echo "  https://mirrors.aliyun.com/ubuntu"
  else
    echo "  https://mirrors.aliyun.com/debian"
    echo "  https://mirrors.aliyun.com/debian-security"
  fi
  [[ "${dry_run}" == "1" ]] && return 0

  backup_host_apt_sources
  local source
  while IFS= read -r -d '' source; do
    run_root sed -i \
      -e 's#http://archive.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g' \
      -e 's#https://archive.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g' \
      -e 's#http://security.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g' \
      -e 's#https://security.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g' \
      -e 's#http://deb.debian.org/debian-security#https://mirrors.aliyun.com/debian-security#g' \
      -e 's#https://deb.debian.org/debian-security#https://mirrors.aliyun.com/debian-security#g' \
      -e 's#http://security.debian.org/debian-security#https://mirrors.aliyun.com/debian-security#g' \
      -e 's#https://security.debian.org/debian-security#https://mirrors.aliyun.com/debian-security#g' \
      -e 's#http://deb.debian.org/debian#https://mirrors.aliyun.com/debian#g' \
      -e 's#https://deb.debian.org/debian#https://mirrors.aliyun.com/debian#g' \
      "${source}"
  done < <(find "${apt_root}" -type f \( -name '*.list' -o -name '*.sources' \) -print0)
  host_apt_modified=1
}

restore_host_apt_sources() {
  if [[ "${dry_run}" == "1" ]]; then
    echo "恢复宿主机原 APT 配置。"
    return 0
  fi
  (( host_apt_modified == 1 )) || return 0
  run_root rm -f "${apt_root}/sources.list"
  run_root rm -rf "${apt_root}/sources.list.d"
  if [[ -f "${host_apt_backup_root}/sources.list" ]]; then
    run_root cp -a "${host_apt_backup_root}/sources.list" "${apt_root}/sources.list"
  fi
  if [[ -d "${host_apt_backup_root}/sources.list.d" ]]; then
    run_root cp -a "${host_apt_backup_root}/sources.list.d" "${apt_root}/sources.list.d"
  else
    run_root mkdir -p "${apt_root}/sources.list.d"
  fi
  host_apt_modified=0
  rm -rf "${host_apt_backup_root}"
  host_apt_backup_root=""
}

cleanup_host_apt() {
  local status=$?
  trap - EXIT
  restore_host_apt_sources || true
  exit "${status}"
}
trap cleanup_host_apt EXIT

need_prerequisites=0
for command_name in curl git sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 || need_prerequisites=1
done
if [[ "${dry_run}" == "1" ]]; then
  need_prerequisites=1
fi

if (( need_prerequisites == 1 )); then
  echo "安装宿主机基础依赖（ca-certificates/curl/git/coreutils/login）。"
  configure_mainland_host_apt
  if run_root apt-get update && run_root apt-get install -y --no-install-recommends ca-certificates curl git coreutils login; then
    restore_host_apt_sources
  else
    echo "宿主机 APT 国内镜像不可用，恢复原 APT 配置并重试。" >&2
    restore_host_apt_sources
    run_root apt-get update
    run_root apt-get install -y --no-install-recommends ca-certificates curl git coreutils login
  fi
fi

mirror_repo_base="https://mirrors.aliyun.com/docker-ce/linux/${distro}"
mirror_gpg_url="https://mirrors.aliyun.com/docker-ce/linux/${distro}/gpg"
official_repo_base="https://download.docker.com/linux/${distro}"
official_gpg_url="https://download.docker.com/linux/${distro}/gpg"
keyring_path="/etc/apt/keyrings/docker.asc"
repo_path="/etc/apt/sources.list.d/docker.sources"

download_to() {
  local url="$1" destination="$2"
  run_user curl -fsSL --retry 3 --retry-delay 2 "${url}" -o "${destination}"
}

install_docker_key() {
  local temporary
  temporary="$(mktemp)"
  if [[ "${dry_run}" == "1" ]]; then
    echo "优先 Docker CE GPG：${mirror_gpg_url}"
    download_to "${mirror_gpg_url}" "${temporary}"
    run_root install -m 0755 -d /etc/apt/keyrings
    run_root install -m 0644 "${temporary}" "${keyring_path}"
    rm -f "${temporary}"
    return 0
  fi
  if ! download_to "${mirror_gpg_url}" "${temporary}"; then
    echo "Docker CE 国内 GPG 镜像不可用，回退官方 GPG。" >&2
    download_to "${official_gpg_url}" "${temporary}"
  fi
  run_root install -m 0755 -d /etc/apt/keyrings
  run_root install -m 0644 "${temporary}" "${keyring_path}"
  rm -f "${temporary}"
}

configure_docker_repo() {
  local base_url="$1" temporary
  temporary="$(mktemp)"
  cat > "${temporary}" <<EOF
Types: deb
URIs: ${base_url}
Suites: ${codename}
Components: stable
Architectures: ${architecture}
Signed-By: ${keyring_path}
EOF
  echo "配置 Docker CE 仓库：${base_url}"
  run_root install -m 0644 "${temporary}" "${repo_path}"
  rm -f "${temporary}"
}

install_docker_packages() {
  local -a packages=("$@")
  run_root apt-get update && run_root apt-get install -y "${packages[@]}"
}

need_docker=0
need_compose=0
if [[ "${dry_run}" == "1" ]]; then
  need_docker=1
  need_compose=1
else
  command -v docker >/dev/null 2>&1 || need_docker=1
  if (( need_docker == 0 )); then
    docker compose version >/dev/null 2>&1 || need_compose=1
  else
    need_compose=1
  fi
fi

if (( need_docker == 1 || need_compose == 1 )); then
  install_docker_key
  configure_docker_repo "${mirror_repo_base}"
  if (( need_docker == 1 )); then
    docker_packages=(docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin)
  else
    docker_packages=(docker-buildx-plugin docker-compose-plugin)
  fi
  if ! install_docker_packages "${docker_packages[@]}"; then
    echo "Docker CE 国内镜像安装失败，回退 Docker 官方仓库。" >&2
    configure_docker_repo "${official_repo_base}"
    install_docker_packages "${docker_packages[@]}"
  fi
fi

if [[ "${dry_run}" == "1" ]]; then
  echo "启动 Docker daemon。"
  run_root systemctl enable --now docker
elif ! "${sudo_prefix[@]}" docker info >/dev/null 2>&1; then
  if command -v systemctl >/dev/null 2>&1; then
    run_root systemctl enable --now docker
  elif command -v service >/dev/null 2>&1; then
    run_root service docker start
  else
    echo "Docker 已安装，但无法自动启动 daemon（缺少 systemctl/service）。" >&2
    exit 1
  fi
fi

if [[ -n "${bootstrap_user}" && "${bootstrap_user}" != "root" ]]; then
  echo "授权部署用户访问 Docker：${bootstrap_user}"
  run_root usermod -aG docker "${bootstrap_user}"
fi

if [[ "${dry_run}" == "1" ]]; then
  echo "宿主机 bootstrap dry-run 完成。"
  trap - EXIT
  restore_host_apt_sources
  exit 0
fi

command -v docker >/dev/null 2>&1 || { echo "Docker 安装后仍不可用" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || "${sudo_prefix[@]}" docker compose version >/dev/null 2>&1 || {
  echo "Docker Compose 插件安装后仍不可用" >&2
  exit 1
}

trap - EXIT
restore_host_apt_sources

if [[ -n "${continue_script}" ]]; then
  [[ -f "${continue_script}" ]] || { echo "继续执行脚本不存在：${continue_script}" >&2; exit 1; }
  continue_command=(env WUST_DORMITORY_HOST_BOOTSTRAPPED=1 bash "${continue_script}" "${continue_args[@]}")
  if docker info >/dev/null 2>&1; then
    exec "${continue_command[@]}"
  fi

  if [[ -n "${bootstrap_user}" && "${bootstrap_user}" != "root" ]] && command -v sg >/dev/null 2>&1; then
    if getent group docker | grep -Eq "(^|[:,])${bootstrap_user}([,:]|$)"; then
      printf -v quoted_command '%q ' "${continue_command[@]}"
      exec sg docker -c "${quoted_command}"
    fi
  fi

  echo "Docker 已安装，但当前会话尚未获得 Docker Socket 权限；请重新登录后再次执行部署。" >&2
  exit 1
fi

echo "宿主机 Docker/Compose bootstrap 完成。"
