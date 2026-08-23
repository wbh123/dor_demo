#!/usr/bin/env bash
set -euo pipefail

repository_root="${1:?usage: release_build_cleanup_contract.sh <repo> <success|fail>}"
mode="${2:-success}"
cleanup_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/cleanup_build_worktree.sh"

[[ -f "${cleanup_script}" ]] || { echo "missing cleanup helper" >&2; exit 1; }
git -C "${repository_root}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "not a git worktree" >&2
  exit 1
}

dirty="$(git -C "${repository_root}" status --porcelain --untracked-files=no)"
if [[ -n "${dirty}" ]]; then
  echo "tracked worktree is dirty before release build" >&2
  exit 1
fi

cleanup_enabled=1
cleanup_on_exit() {
  local status=$?
  if (( cleanup_enabled == 1 )); then
    bash "${cleanup_script}" "${repository_root}" frontend || status=1
  fi
  trap - EXIT
  exit "${status}"
}
trap cleanup_on_exit EXIT

printf 'generated\n' > "${repository_root}/frontend/src/api/schema.d.ts"

if [[ "${mode}" == "fail" ]]; then
  exit 42
fi

cleanup_enabled=0
bash "${cleanup_script}" "${repository_root}" frontend
trap - EXIT
