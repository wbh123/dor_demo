#!/usr/bin/env bash
set -euo pipefail

release_tag_mode=0
if [[ "${1:-}" == "--release-tag" ]]; then
  release_tag_mode=1
  shift
fi
root="${1:?usage: source_archive_mode_contract.sh [--release-tag] <root>}"

if (( release_tag_mode == 1 )); then
  tag="${WUST_DORMITORY_RELEASE_TAG:-}"
  if [[ -z "${tag}" ]]; then
    if command -v git >/dev/null 2>&1 && git -C "${root}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
      tag="$(git -C "${root}" rev-parse --short=12 HEAD)"
    else
      tag="archive-$(date -u +%Y%m%dT%H%M%SZ)"
    fi
  fi
  printf '%s\n' "${tag}"
  exit 0
fi

source_git_metadata_present=0
source_git_mode=0
full=0
pull_enabled=1
head_commit=""
[[ -e "${root}/.git" ]] && source_git_metadata_present=1

if (( source_git_metadata_present == 1 )); then
  command -v git >/dev/null 2>&1 || { echo "缺少 git" >&2; exit 1; }
  git -C "${root}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    echo "检测到 .git，但不是有效 Git 工作树" >&2
    exit 1
  }
  source_git_mode=1
  head_commit="$(git -C "${root}" rev-parse HEAD)"
else
  source_git_mode=0
  full=1
  pull_enabled=0
  head_commit="ARCHIVE"
fi

cat <<EOF
MODE=$([[ "${source_git_mode}" == "1" ]] && echo GIT || echo ARCHIVE)
FULL=${full}
PULL=${pull_enabled}
HEAD=${head_commit}
EOF
