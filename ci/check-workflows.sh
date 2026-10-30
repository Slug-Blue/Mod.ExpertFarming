#!/usr/bin/env bash
# SEC-41: GitHub Actions hardening checks (WR-010, WR-016).
# - every 'uses:' is pinned to a full 40-char commit SHA (local ./ actions ok);
# - no pull_request_target trigger;
# - every workflow sets a top-level 'permissions:' block;
# - release workflows (release*.yml) trigger only on workflow_dispatch.
#
# Usage: ci/check-workflows.sh [workflow.yml ...]
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "$#" -gt 0 ]; then
  files=("$@")
else
  mapfile -t files < <(ls .github/workflows/*.yml .github/workflows/*.yaml 2>/dev/null || true)
fi

status=0
fail() { echo "check-workflows: $1" >&2; status=1; }

for f in "${files[@]}"; do
  while IFS=: read -r line ref; do
    ref=$(printf '%s' "$ref" | sed -E 's/^[[:space:]]*-?[[:space:]]*uses:[[:space:]]*//; s/[[:space:]]+#.*$//; s/["'\'']//g')
    case "$ref" in
      ./*) continue ;;
    esac
    if ! printf '%s' "$ref" | grep -qE '^[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}$'; then
      fail "$f:$line: 'uses: $ref' is not pinned to a full commit SHA"
    fi
  done < <(grep -nE '^[[:space:]]*-?[[:space:]]*uses:' "$f" || true)

  if grep -nE '^[^#]*pull_request_target' "$f" >/dev/null; then
    fail "$f: pull_request_target is forbidden"
  fi
  if ! grep -qE '^permissions:' "$f"; then
    fail "$f: no top-level 'permissions:' block"
  fi
  case "$(basename "$f")" in
    release*)
      # the 'on:' block may contain only workflow_dispatch
      triggers=$(awk '/^on:/{flag=1; next} /^[^[:space:]#]/{flag=0} flag && /^  [a-z_]+:?/{gsub(/[: ]/,""); print}' "$f" | sort -u | tr '\n' ' ')
      if [ "$triggers" != "workflow_dispatch " ]; then
        fail "$f: a release workflow may only trigger on workflow_dispatch (found: $triggers)"
      fi
      ;;
  esac
done

if [ "$status" = 0 ]; then
  echo "check-workflows: OK (${#files[@]} files)"
fi
exit "$status"
