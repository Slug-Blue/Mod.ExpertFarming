#!/usr/bin/env bash
# WR-013: forbidden constructs and allowlists (SEC-38, SEC-43, SEC-45).
#
#  1. Production sources and resources (*/src/main): no update-check URL key
#     in mods.toml, no Runtime.getRuntime / ProcessBuilder / ScriptEngine /
#     URLClassLoader, no http(s):// except displayURL / issueTrackerURL in
#     mods.toml.
#  2. All Java sources: no @Overwrite (ADR-0014).
#  3. Gradle files: exact versions only, no '+', ranges, latest.*, -SNAPSHOT.
#  4. wildroots.mixins.json: required/minVersion/defaultRequire/package are
#     fixed, every mixin class is in ci/allowlists/mixins.txt.
#  5. accesstransformer.cfg: every entry is in ci/allowlists/accesstransformer.txt.
#
# Usage: ci/check-sources.sh [project-root]   (default: the repository)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="${1:-$HERE/..}"
ROOT="$(cd "$ROOT" && pwd)"
PY="${PYTHON:-python3}"

status=0
fail() { echo "check-sources: $1" >&2; status=1; }

main_dirs=()
for d in "$ROOT"/genetics/src/main "$ROOT"/mod/src/main; do
  [ -d "$d" ] && main_dirs+=("$d")
done

# --- 1. production sources and resources -----------------------------------
if [ "${#main_dirs[@]}" -gt 0 ]; then
  while IFS= read -r hit; do
    fail "$hit: update-check URL key is forbidden (SEC-43)"
  done < <(grep -rnI 'updateJSONURL' "${main_dirs[@]}" || true)

  while IFS= read -r hit; do
    fail "$hit: forbidden API (SEC-43)"
  done < <(grep -rnI --include='*.java' -E 'Runtime\.getRuntime|ProcessBuilder|ScriptEngine|URLClassLoader' "${main_dirs[@]}" || true)

  while IFS= read -r hit; do
    file="${hit%%:*}"
    rest="${hit#*:}"; text="${rest#*:}"
    case "$file" in
      */META-INF/mods.toml)
        if printf '%s' "$text" | grep -qE '^[[:space:]]*(displayURL|issueTrackerURL)[[:space:]]*='; then continue; fi ;;
      */LICENSE*|*/license*) continue ;;
    esac
    fail "$hit: URL outside mod metadata (SEC-43)"
  done < <(grep -rnIE 'https?://' "${main_dirs[@]}" || true)
fi

# --- 2. @Overwrite anywhere in Java sources ---------------------------------
while IFS= read -r hit; do
  fail "$hit: @Overwrite is forbidden (ADR-0014, SEC-45)"
done < <(grep -rnI --include='*.java' -E '@Overwrite\b|asm\.mixin\.Overwrite' "$ROOT"/genetics/src "$ROOT"/mod/src 2>/dev/null || true)

# --- 3. exact versions in Gradle files --------------------------------------
gradle_files=()
for f in "$ROOT"/settings.gradle "$ROOT"/build.gradle "$ROOT"/gradle.properties \
         "$ROOT"/genetics/build.gradle "$ROOT"/mod/build.gradle "$ROOT"/gradle/*.gradle; do
  [ -f "$f" ] && gradle_files+=("$f")
done
if [ "${#gradle_files[@]}" -gt 0 ]; then
  while IFS= read -r hit; do
    fail "$hit: dynamic version, range, latest.* or SNAPSHOT (SEC-38)"
  done < <(grep -nHE '(:|\.)\+([^=]|$)|latest\.|SNAPSHOT|[[(][0-9][0-9.]*,[0-9.]*[])]' "${gradle_files[@]}" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|#)' || true)
fi

# --- 4 and 5. mixin config and access transformer allowlists ----------------
MIXINS="$ROOT/mod/src/main/resources/wildroots.mixins.json"
AT="$ROOT/mod/src/main/resources/META-INF/accesstransformer.cfg"
if [ -f "$MIXINS" ]; then
  if ! "$PY" -I - "$MIXINS" "$HERE/allowlists/mixins.txt" <<'PYEOF'
import json, sys
cfg_path, allow_path = sys.argv[1], sys.argv[2]
cfg = json.load(open(cfg_path, encoding="utf-8"))
allowed = {l.strip() for l in open(allow_path, encoding="utf-8") if l.strip() and not l.lstrip().startswith("#")}
errors = []
expect = {"required": True, "minVersion": "0.8.5", "package": "io.github.slugblue.wildroots.mixin"}
for key, value in expect.items():
    if cfg.get(key) != value:
        errors.append("%s must be %r, found %r" % (key, value, cfg.get(key)))
if (cfg.get("injectors") or {}).get("defaultRequire") != 1:
    errors.append("injectors.defaultRequire must be 1")
for section in ("mixins", "client", "server"):
    for name in cfg.get(section) or []:
        if name not in allowed:
            errors.append("mixin %s.%s is not in ci/allowlists/mixins.txt (ADR-0014)" % (section, name))
for e in errors:
    print("check-sources: %s: %s" % (cfg_path, e), file=sys.stderr)
sys.exit(1 if errors else 0)
PYEOF
  then status=1; fi
fi
if [ -f "$AT" ]; then
  while IFS= read -r line; do
    entry="$(printf '%s' "$line" | sed -E 's/[[:space:]]*#.*$//; s/[[:space:]]+$//')"
    [ -z "$entry" ] && continue
    if ! grep -vE '^[[:space:]]*(#|$)' "$HERE/allowlists/accesstransformer.txt" | grep -qxF -- "$entry"; then
      fail "$AT: '$entry' is not in ci/allowlists/accesstransformer.txt (SEC-45)"
    fi
  done < "$AT"
fi

if [ "$status" = 0 ]; then
  echo "check-sources: OK ($ROOT)"
fi
exit "$status"
