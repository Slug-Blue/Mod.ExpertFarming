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
    if grep -nE "$pattern" "$log"; then
      echo "offline-gametest: the server tried to open a network connection (SEC-44)" >&2
      exit 1
    fi
    echo "offline-gametest: no network attempts in $log"
    ;;
  *)
    echo "usage: $0 prepare | scan [log]" >&2
    exit 2
    ;;
esac
