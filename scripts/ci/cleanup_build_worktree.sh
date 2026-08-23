#!/usr/bin/env bash
set -euo pipefail
repository_root="${1:?usage: cleanup_build_worktree.sh <repository-root> [frontend] [app]}"
shift
frontend_scope=0
app_scope=0
for scope in "$@"; do
  case "${scope}" in
    frontend) frontend_scope=1 ;;
    app) app_scope=1 ;;
    *) echo "unknown scope: ${scope}" >&2; exit 2 ;;
  esac
done
mapfile -d '' staged_paths < <(git -C "${repository_root}" diff --cached --name-only -z)
if (( ${#staged_paths[@]} > 0 )); then
  printf 'staged change: %s\n' "${staged_paths[@]}" >&2
  exit 1
fi
mapfile -d '' dirty_paths < <(git -C "${repository_root}" diff --name-only -z)
(( ${#dirty_paths[@]} > 0 )) || exit 0
allowed_paths=()
unexpected_paths=()
for path in "${dirty_paths[@]}"; do
  allowed=0
  if (( frontend_scope == 1 )); then
    case "${path}" in
      frontend/src/api/schema.d.ts|frontend/src/api/systemReadinessSchema.d.ts|frontend/public/fonts/*) allowed=1 ;;
    esac
  fi
  if (( app_scope == 1 )); then
    case "${path}" in
      app/src/api/schema.d.ts|app/src/generated/school-profile.ts|app/capacitor.config.ts|app/src/assets/icon.png|app/android/*) allowed=1 ;;
    esac
  fi
  if (( allowed == 1 )); then allowed_paths+=("${path}"); else unexpected_paths+=("${path}"); fi
done
if (( ${#unexpected_paths[@]} > 0 )); then
  printf 'unexpected tracked change: %s\n' "${unexpected_paths[@]}" >&2
  exit 1
fi
(( ${#allowed_paths[@]} == 0 )) || git -C "${repository_root}" restore --worktree -- "${allowed_paths[@]}"
