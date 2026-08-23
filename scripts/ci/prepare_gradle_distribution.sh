#!/usr/bin/env bash
set -euo pipefail

wrapper_properties="${1:?usage: prepare_gradle_distribution.sh <gradle-wrapper.properties>}"
[[ -f "${wrapper_properties}" ]] || { echo "Gradle wrapper properties not found: ${wrapper_properties}" >&2; exit 1; }

raw_url="$(sed -n 's/^distributionUrl=//p' "${wrapper_properties}" | tail -n 1)"
distribution_url="$(printf '%s' "${raw_url}" | sed 's/\\:/:/g')"
filename="${distribution_url##*/}"
if [[ ! "${filename}" =~ ^gradle-(.+)-(all|bin)\.zip$ ]]; then
  echo "Unsupported Gradle distribution URL: ${distribution_url}" >&2
  exit 1
fi
version="${BASH_REMATCH[1]}"

gradle_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
cache_root="${gradle_home}/wust-distributions"
target_root="${cache_root}/gradle-${version}"
gradle_bin="${target_root}/bin/gradle"

if [[ -x "${gradle_bin}" ]]; then
  printf '%s\n' "${gradle_bin}"
  exit 0
fi

mkdir -p "${cache_root}"
temporary_zip="${cache_root}/.gradle-${version}.$$.zip"
temporary_extract="${cache_root}/.gradle-${version}.$$.extract"
cleanup() {
  rm -f "${temporary_zip}"
  rm -rf "${temporary_extract}"
}
trap cleanup EXIT

mirror_url="https://mirrors.aliyun.com/gradle/distributions/v${version}/gradle-${version}-bin.zip"
official_url="https://services.gradle.org/distributions/gradle-${version}-bin.zip"

if ! curl -fL --retry 3 --retry-delay 2 "${mirror_url}" -o "${temporary_zip}"; then
  echo "Gradle 国内镜像不可用，回退 Gradle 官方分发源。" >&2
  curl -fL --retry 3 --retry-delay 2 "${official_url}" -o "${temporary_zip}"
fi

mkdir -p "${temporary_extract}"
unzip -q "${temporary_zip}" -d "${temporary_extract}"
[[ -x "${temporary_extract}/gradle-${version}/bin/gradle" ]] || {
  echo "Gradle distribution is incomplete: ${version}" >&2
  exit 1
}
rm -rf "${target_root}"
mv "${temporary_extract}/gradle-${version}" "${target_root}"
printf '%s\n' "${gradle_bin}"
