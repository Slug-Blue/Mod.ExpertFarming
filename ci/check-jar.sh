#!/usr/bin/env bash
# WR-013 / SEC-42: the mod jar may contain only what ADR-0016 lists:
#   io/github/slugblue/wildroots/** (mod and :genetics classes),
#   META-INF/MANIFEST.MF, META-INF/mods.toml, META-INF/accesstransformer.cfg,
#   wildroots.mixins.json, wildroots.refmap.json, pack.mcmeta,
#   assets/wildroots/**, data/**, resourcepacks/lines/**, the logo.
# No nested jars (jarJar), no native libraries, no classes outside the package.
#
# Usage: ci/check-jar.sh [path/to/mod.jar]   (default: mod/build/libs/wildroots-*.jar)
set -euo pipefail
cd "$(dirname "$0")/.."

jar_file="${1:-}"
if [ -z "$jar_file" ]; then
  shopt -s nullglob
  candidates=(mod/build/libs/wildroots-*.jar)
  if [ "${#candidates[@]}" -ne 1 ]; then
    echo "check-jar: expected exactly one mod/build/libs/wildroots-*.jar, found ${#candidates[@]}" >&2
    exit 1
  fi
  jar_file="${candidates[0]}"
fi

if command -v jar >/dev/null 2>&1; then
  entries="$(jar tf "$jar_file")"
else
  entries="$(unzip -Z1 "$jar_file")"
fi

allowed='^('
allowed+='META-INF/|META-INF/MANIFEST\.MF|META-INF/mods\.toml|META-INF/accesstransformer\.cfg'
allowed+='|io/|io/github/|io/github/slugblue/|io/github/slugblue/wildroots/.*'
allowed+='|wildroots\.mixins\.json|wildroots\.refmap\.json|pack\.mcmeta|wildroots_logo\.png'
allowed+='|assets/|assets/wildroots/.*'
allowed+='|data/.*'
allowed+='|resourcepacks/|resourcepacks/lines/.*'
allowed+=')$'

status=0
count=0
while IFS= read -r entry; do
  entry="${entry%$'\r'}"
  [ -z "$entry" ] && continue
  count=$((count + 1))
  if printf '%s' "$entry" | grep -qiE '\.(jar|so|dll|dylib|jnilib)$'; then
    echo "check-jar: $entry: nested jar or native library (SEC-42)" >&2; status=1; continue
  fi
  if ! printf '%s' "$entry" | grep -qE "$allowed"; then
    echo "check-jar: $entry: not in the jar allowlist (SEC-42)" >&2; status=1
  fi
done <<< "$entries"

if [ "$status" = 0 ]; then
  echo "check-jar: OK ($jar_file, $count entries)"
fi
exit "$status"
