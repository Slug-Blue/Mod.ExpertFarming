#!/usr/bin/env bash
# WR-008 / SEC-37: Gradle build files may only use the official repositories
# from docs/05-architecture.md §18, over https.
#
# Fails on: mavenLocal, JitPack, flatDir, any http:// URL, and any repository
# URL whose host is not in the allowlist below.
#
# Usage: ci/check-repositories.sh [file ...]
#   Without arguments it checks every Gradle file of the build.
set -euo pipefail

cd "$(dirname "$0")/.."

ALLOWED_HOSTS=(
  'maven.minecraftforge.net'
  'maven.parchmentmc.org'
  'repo.spongepowered.org'
  'maven.blamejared.com'
  'maven.terraformersmc.com'
  'api.modrinth.com'
)

if [ "$#" -gt 0 ]; then
  files=("$@")
else
  mapfile -t files < <(git ls-files --cached --others --exclude-standard -- '*.gradle' '*.gradle.kts' 'gradle.properties' '*/gradle.properties' | sort -u)
fi

status=0
fail() { echo "check-repositories: $1" >&2; status=1; }

for f in "${files[@]}"; do
  [ -f "$f" ] || continue
  while IFS= read -r hit; do
    fail "$f:$hit: forbidden repository (mavenLocal / JitPack / flatDir)"
  done < <(grep -niE 'mavenLocal|jitpack|flatDir' "$f" | cut -d: -f1 || true)
  while IFS= read -r hit; do
    fail "$f:$hit: plain http:// URL"
  done < <(grep -nE 'http://' "$f" | cut -d: -f1 || true)
  # every https URL in a Gradle file must point to an allowed repository host
  while IFS=: read -r line url; do
    host=$(printf '%s' "$url" | sed -E 's#^https://([^/:]+).*#\1#')
    ok=0
    for h in "${ALLOWED_HOSTS[@]}"; do
      [ "$host" = "$h" ] && ok=1
    done
    [ "$ok" = 1 ] || fail "$f:$line: repository host '$host' is not in the allowlist"
  done < <(grep -noE "https://[^'\" )]+" "$f" || true)
done

if [ "$status" = 0 ]; then
  echo "check-repositories: OK (${#files[@]} files)"
fi
exit "$status"
