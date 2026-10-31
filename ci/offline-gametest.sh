#!/usr/bin/env bash
# WR-014 / SEC-44: helpers for the gametest-offline job.
#
#   ci/offline-gametest.sh prepare   - write versionCheck = false into the
#                                      run's FML config (mod/run/config/fml.toml)
#   ci/offline-gametest.sh scan [log] - fail if the server log shows any
#                                      network attempt (default mod/run/logs/debug.log)
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_DIR="mod/run"
FML="$RUN_DIR/config/fml.toml"

case "${1:-}" in
  prepare)
    mkdir -p "$RUN_DIR/config"
    if [ -f "$FML" ] && grep -qE '^[[:space:]]*versionCheck[[:space:]]*=' "$FML"; then
      sed -i -E 's/^([[:space:]]*versionCheck[[:space:]]*=).*/\1 false/' "$FML"
    else
      printf '#Enable forge global version checking\nversionCheck = false\n' >> "$FML"
    fi
    grep -E '^[[:space:]]*versionCheck' "$FML"
    ;;
  scan)
    log="${2:-$RUN_DIR/logs/debug.log}"
    [ -f "$log" ] || { echo "offline-gametest: no log at $log" >&2; exit 1; }
    pattern='java\.net\.(ConnectException|UnknownHostException|NoRouteToHostException|SocketException|SocketTimeoutException)|Network is unreachable|Starting version check|java\.net\.http\.HttpConnectTimeoutException'
    # Known vanilla attempt, not ours: authlib's "Yggdrasil Key Fetcher"
    # thread asks api.minecraftservices.com for the services public key on
    # every server start (YggdrasilServicesKeyInfo). Lines of that one log
    # entry (its header and stack trace) are skipped; anything else counts.
    allowed='\[Yggdrasil Key Fetcher/[A-Z]+\] \[com\.mojang\.authlib\.yggdrasil\.YggdrasilServicesKeyInfo/'
    # patterns go through ENVIRON: awk -v would eat the backslashes
    rc=0
    PAT="$pattern" OK="$allowed" awk '
        /^\[/ { skip = ($0 ~ ENVIRON["OK"]) }
        !skip && $0 ~ ENVIRON["PAT"] { print FNR ":" $0; hit = 1 }
        END { exit (hit ? 0 : 1) }' "$log" || rc=$?
    if [ "$rc" -eq 0 ]; then
      echo "offline-gametest: the server tried to open a network connection (SEC-44)" >&2
      exit 1
    elif [ "$rc" -ne 1 ]; then
      echo "offline-gametest: scan failed (awk exit $rc)" >&2
      exit 1
    fi
    echo "offline-gametest: no network attempts in $log"
    ;;
  *)
    echo "usage: $0 prepare | scan [log]" >&2
    exit 2
    ;;
esac
