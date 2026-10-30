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
expect_fail check-workflows bash ci/check-workflows.sh ci/fixtures/bad-workflow.yml.txt
expect_fail check-workflows-release bash ci/check-workflows.sh ci/fixtures/release-bad.yml.txt

expect_fail check-sources bash ci/check-sources.sh ci/fixtures/bad-project
expect_fail offline-gametest-scan bash ci/offline-gametest.sh scan ci/fixtures/network-attempt.log.txt

# a jar with a nested jar, a native library and a class outside the package
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/j/META-INF/jarjar" "$tmp/j/io/github/slugblue/wildroots" "$tmp/j/com/example"
touch "$tmp/j/io/github/slugblue/wildroots/WildRoots.class" "$tmp/j/META-INF/jarjar/lib.jar"       "$tmp/j/libnative.so" "$tmp/j/com/example/Evil.class"
(cd "$tmp/j" && jar cf ../bad.jar .) || { echo "selftest: cannot build the bad jar" >&2; exit 1; }
expect_fail check-jar bash ci/check-jar.sh "$tmp/bad.jar"

exit "$status"
