package io.github.slugblue.wildroots.gametest.perf;

import io.github.slugblue.wildroots.WildRoots;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * WR-028 (S9) entry point, ported in WR-195: the 10 000-plant bench as a GameTest, on the real
 * {@code FieldPlantBlock} / {@code PlantBlockEntity} ({@link RealPlants}) instead of the S9 prototype.
 *
 * <p>Off by default, so the normal {@code runGameTestServer} (CI) stays fast. Enable with
 * the environment variable {@code WILDROOTS_BENCH=1} (or system property
 * {@code wildroots.bench=true}). Tunables: {@code WILDROOTS_BENCH_REPS},
 * {@code WILDROOTS_BENCH_WARM}, {@code WILDROOTS_BENCH_MEASURE}, {@code WILDROOTS_BENCH_WARMUP}.
 * Results: log lines {@code [S9]} and {@code mod/run/wildroots-bench/s9-*.{csv,txt}}. The run fails
 * when the median MSPT delta against wheat exceeds {@code wildroots.bench.budgetMs} (2.0 ms) or the
 * in-world memory exceeds 1 KiB per plant or a decoded block entity 400 B (WR-131); above {@code wildroots.bench.warnMs} (0.5 ms) it warns.
 */
@GameTestHolder(WildRoots.MOD_ID)
public final class PlantPerfBench {

    private PlantPerfBench() {
    }

    static boolean enabled() {
        String p = System.getProperty("wildroots.bench");
        String e = System.getenv("WILDROOTS_BENCH");
        return "true".equalsIgnoreCase(p) || "1".equals(e) || "true".equalsIgnoreCase(e);
    }

    @GameTestGenerator
    public static Collection<TestFunction> perfBench() {
        if (!enabled()) {
            return List.of();
        }
        int reps = BenchDriver.intConf("wildroots.bench.reps", 6);
        int window = BenchDriver.intConf("wildroots.bench.warm", 300) + BenchDriver.intConf("wildroots.bench.measure", 1200) + 2;
        int maxTicks = 100 + 3 * BenchDriver.intConf("wildroots.bench.warmup", 1000) + 3 * reps * window + 2000;
        return List.of(new TestFunction("wildroots_perf", "wildroots.perf_bench_10k_plants",
                "wildroots:gametest/floor_3x3x3", maxTicks, 0L, true, PlantPerfBench::run));
    }

    private static void run(GameTestHelper helper) {
        BenchDriver driver = BenchDriver.start(helper.getLevel());
        helper.succeedWhen(() -> {
            if (!driver.finished) {
                throw new GameTestAssertException("bench running");
            }
            if (driver.failure != null) {
                throw new GameTestAssertException(driver.failure);
            }
        });
    }
}
