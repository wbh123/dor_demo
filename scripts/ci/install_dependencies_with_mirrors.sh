#!/usr/bin/env bash
set -euo pipefail

requirements_file=""
if [[ "${1:-}" == "--requirements" ]]; then
  requirements_file="${2:?--requirements requires a path}"
  shift 2
fi

(( $# > 0 )) || { echo "usage: install_dependencies_with_mirrors.sh [--requirements FILE] <apt-package...>" >&2; exit 2; }
packages=("$@")
apt_root="${WUST_APT_ROOT:-/etc/apt}"
marker="${apt_root}/mirror-active"
backup_root="$(mktemp -d)"

cleanup() {
  rm -rf "${backup_root}"
  rm -f "${marker}"
}
trap cleanup EXIT

mkdir -p "${apt_root}/sources.list.d"
if [[ -f "${apt_root}/sources.list" ]]; then
  cp -a "${apt_root}/sources.list" "${backup_root}/sources.list"
fi
if [[ -d "${apt_root}/sources.list.d" ]]; then
  cp -a "${apt_root}/sources.list.d" "${backup_root}/sources.list.d"
fi

restore_sources() {
  rm -f "${apt_root}/sources.list"
  rm -rf "${apt_root}/sources.list.d"
  if [[ -f "${backup_root}/sources.list" ]]; then
    cp -a "${backup_root}/sources.list" "${apt_root}/sources.list"
  fi
  if [[ -d "${backup_root}/sources.list.d" ]]; then
    cp -a "${backup_root}/sources.list.d" "${apt_root}/sources.list.d"
  else
    mkdir -p "${apt_root}/sources.list.d"
  fi
  rm -f "${marker}"
}

configure_mainland_apt() {
  while IFS= read -r -d '' source; do
    sed -i \
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
  touch "${marker}"
}

install_apt_packages() {
  apt-get update && apt-get install -y --no-install-recommends "${packages[@]}"
}

configure_mainland_apt
if ! install_apt_packages; then
  echo "APT 国内镜像不可用，恢复镜像配置并回退系统官方源。" >&2
  restore_sources
  rm -rf /var/lib/apt/lists/* 2>/dev/null || true
  install_apt_packages
fi
rm -rf /var/lib/apt/lists/* 2>/dev/null || true

if [[ -n "${requirements_file}" ]]; then
  [[ -f "${requirements_file}" ]] || { echo "requirements file not found: ${requirements_file}" >&2; exit 1; }
  if ! python3 -m pip install --no-cache-dir --index-url https://mirrors.aliyun.com/pypi/simple/ -r "${requirements_file}"; then
    echo "PyPI 国内镜像不可用，回退官方 PyPI。" >&2
    python3 -m pip install --no-cache-dir -r "${requirements_file}"
  fi
fi
