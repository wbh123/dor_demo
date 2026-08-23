#!/usr/bin/env bash
set -euo pipefail

env_file="${1:?usage: build_env_hash_plan_contract.sh <env-file> <frontend-hash> <app-hash> <skip-app>}"
previous_frontend_hash="${2:-}"
previous_app_hash="${3:-}"
skip_app="${4:-0}"

[[ -f "${env_file}" ]] || { echo "missing env file: ${env_file}" >&2; exit 1; }

build_env_sha256() {
  # Vite only exposes VITE_* variables to client builds. Preserve file order so
  # duplicate-key edits and value changes are both visible to the build state.
  sed -n -E '/^[[:space:]]*#/d; /^[[:space:]]*VITE_[A-Za-z0-9_]*=/p' "${env_file}" \
    | sha256sum \
    | awk '{print $1}'
}

current_hash="$(build_env_sha256)"
frontend_build=0
app_build=0
[[ "${previous_frontend_hash}" == "${current_hash}" ]] || frontend_build=1
[[ "${previous_app_hash}" == "${current_hash}" ]] || app_build=1
if [[ "${skip_app}" == "1" || "${skip_app}" == "true" ]]; then
  app_build=0
fi

frontend_state_after="${previous_frontend_hash}"
app_state_after="${previous_app_hash}"
(( frontend_build == 1 )) && frontend_state_after="${current_hash}"
(( app_build == 1 )) && app_state_after="${current_hash}"

cat <<EOF
CURRENT_BUILD_ENV_SHA256=${current_hash}
FRONTEND_BUILD=${frontend_build}
APP_BUILD=${app_build}
FRONTEND_STATE_AFTER_SUCCESS=${frontend_state_after}
APP_STATE_AFTER_SUCCESS=${app_state_after}
EOF
