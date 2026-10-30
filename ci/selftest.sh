#!/usr/bin/env bash
# Runs every CI check script against its negative fixture and expects a
# failure. Proves that the checks are not silently green (WR-008, WR-013).
set -uo pipefail
cd "$(dirname "$0")/.."

status=0
expect_fail() {
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "selftest: FAIL — '$name' accepted a bad fixture" >&2
    status=1
  else
    echo "selftest: ok — '$name' rejects its bad fixture"
  fi
}

expect_fail check-repositories bash ci/check-repositories.sh ci/fixtures/bad-repositories.gradle.txt

exit "$status"
