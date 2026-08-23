#!/usr/bin/env bash
set -euo pipefail
image="${1:?usage: pull_image_with_fallback.sh <image>}"
if docker image inspect "${image}" >/dev/null 2>&1; then
  exit 0
fi
if docker pull "${image}"; then
  exit 0
fi
case "${image}" in
  m.daocloud.io/docker.io/*)
    official="docker.io/${image#m.daocloud.io/docker.io/}"
    docker pull "${official}"
    docker tag "${official}" "${image}"
    ;;
  *) exit 1 ;;
esac
