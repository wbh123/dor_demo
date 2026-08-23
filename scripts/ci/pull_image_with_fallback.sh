#!/usr/bin/env bash
set -euo pipefail

image="${1:?usage: pull_image_with_fallback.sh <image>}"

if docker image inspect "${image}" >/dev/null 2>&1; then
  exit 0
fi

original="${image}"
case "${image}" in
  m.daocloud.io/docker.io/*)
    dockerhub_path="${image#m.daocloud.io/docker.io/}"
    ;;
  docker.io/*)
    dockerhub_path="${image#docker.io/}"
    [[ "${dockerhub_path}" == */* ]] || dockerhub_path="library/${dockerhub_path}"
    ;;
  */*)
    first_component="${image%%/*}"
    if [[ "${first_component}" == *.* || "${first_component}" == *:* || "${first_component}" == "localhost" ]]; then
      docker pull "${image}"
      exit 0
    fi
    dockerhub_path="${image}"
    ;;
  *)
    dockerhub_path="library/${image}"
    ;;
esac

mirror="m.daocloud.io/docker.io/${dockerhub_path}"
official="docker.io/${dockerhub_path}"

if docker pull "${mirror}"; then
  [[ "${original}" == "${mirror}" ]] || docker tag "${mirror}" "${original}"
  exit 0
fi

docker pull "${official}"
[[ "${original}" == "${official}" ]] || docker tag "${official}" "${original}"
[[ "${original}" == "${mirror}" ]] || docker tag "${official}" "${mirror}"
