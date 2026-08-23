#!/usr/bin/env bash
set -euo pipefail

requested_build="${1:-0}"
previous_pending="${2:-0}"
skip_app="${3:-0}"

for value in "${requested_build}" "${previous_pending}"; do
  [[ "${value}" == "0" || "${value}" == "1" ]] || { echo "APP build state must be 0/1" >&2; exit 2; }
done
[[ "${skip_app}" == "0" || "${skip_app}" == "1" || "${skip_app}" == "false" || "${skip_app}" == "true" ]] || {
  echo "skip-app must be 0/1/false/true" >&2
  exit 2
}

app_build=0
pending_after="${previous_pending}"
if (( requested_build == 1 || previous_pending == 1 )); then
  if [[ "${skip_app}" == "1" || "${skip_app}" == "true" ]]; then
    app_build=0
    pending_after=1
  else
    app_build=1
    pending_after=0
  fi
elif [[ "${skip_app}" != "1" && "${skip_app}" != "true" ]]; then
  pending_after=0
fi

cat <<EOF
APP_BUILD=${app_build}
APP_BUILD_PENDING_AFTER_SUCCESS=${pending_after}
EOF
