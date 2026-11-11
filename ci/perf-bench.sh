#!/usr/bin/env bash
# WR-028 (S9) perf bench, ported in WR-195 to the real plant: 10 000
# wildroots:wild_carrot plants (FieldPlantBlock + PlantBlockEntity, bloom index,
# seed set) in 40 chunks, A/B vs vanilla wheat.
# Runs :mod:runGameTestServer with the bench GameTest enabled (it is off by
# default, so the normal CI GameTest run is unaffected) RUNS times, each in a
# fresh JVM and a fresh world (mod/run/world is moved to mod/run/world.prev),
# and copies the per-run results to OUT_DIR.
#
# Usage: ci/perf-bench.sh [RUNS] [OUT_DIR]
#   env: WILDROOTS_BENCH_REPS (6), WILDROOTS_BENCH_WARM (300),
#        WILDROOTS_BENCH_MEASURE (1200), WILDROOTS_BENCH_WARMUP (1000)
# Gate (WR-195): median delta MSPT <= 2 ms (warning above 0.5 ms), <= 1 KiB
# per plant in the world, <= 400 B per decoded block entity (WR-131); the
# bench GameTest fails otherwise.
# JAVA_HOME must point to a JDK 17 (see CONTRIBUTING.md §8).
set -euo pipefail

RUNS="${1:-3}"
OUT_DIR="${2:-build/perf-bench}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$OUT_DIR"

for i in $(seq 1 "$RUNS"); do
    echo "== perf bench run $i/$RUNS"
    if [ -d "$ROOT/mod/run/world" ]; then
        rm -rf "$ROOT/mod/run/world.prev"
        mv "$ROOT/mod/run/world" "$ROOT/mod/run/world.prev"
    fi
    rm -f "$ROOT/mod/run/wildroots-bench/s9-windows.csv" "$ROOT/mod/run/wildroots-bench/s9-summary.txt"
    WILDROOTS_BENCH=1 "$ROOT/gradlew" -p "$ROOT" :mod:runGameTestServer > "$OUT_DIR/run$i.log" 2>&1 || {
        echo "run $i failed, see $OUT_DIR/run$i.log"
        grep -E '\[S9\]|failed' "$OUT_DIR/run$i.log" | tail -20 || true
        exit 1
    }
    cp "$ROOT/mod/run/wildroots-bench/s9-windows.csv" "$OUT_DIR/run$i-windows.csv"
    cp "$ROOT/mod/run/wildroots-bench/s9-summary.txt" "$OUT_DIR/run$i-summary.txt"
    cat "$OUT_DIR/run$i-summary.txt"
done
