package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.journal.CultivarBred;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.journal.JournalTriggers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-195, the M1a gate (GDD §7.3, §11.3, §12.4; {@code 00-vision.md}
 * metrics 1 and 3): from the wild plants of a starter patch to a plant
 * recognised as {@code eastern_carrot} and then as {@code carrot}, through
 * the production paths of {@link BreedingLine}, credited to the player who
 * planted it, with the {@code CultivarBred} pages, toasts and the
 * {@code first_landrace} / {@code first_cultivar} hints.
 *
 * <p><b>Why several starter patches.</b> A starter patch is realised from
 * the world seed, which is random in every run. Wave 1 has no analyser and
 * no village material, so the rare {@code y2} (wild frequency 0.12) is
 * invisible on a white root and is lost by drift in most lines (sweep of
 * 2026-11-10: 6 of 20 starter patches reached the cultivar by generation
 * 30; see the WR-195 report and {@code docs/playtest/m1a-checklist.md}). The
 * gate player therefore does what a player can: a line that showed no
 * orange root by generation {@value #ABANDON_WITHOUT_ORANGE} or did not
 * reach the cultivar by {@value #MAX_GEN} is given up and a new line
 * starts from another starter patch (another fixed origin), at most
 * {@value #MAX_LINES} lines. The first generation of the first line has
 * real bees from a hive.
 *
 * <p><b>False failures.</b> Lines are independent (another origin, so another
 * patch seed and starter population; the world seed differs in every run),
 * and the gate passes as soon as one of them reaches the cultivar, so it
 * fails falsely with probability {@code (1 − p)^MAX_LINES}, {@code p} being
 * the share of lines that reach the cultivar under the player's rules
 * ({@link #gateSucceeds}; the sweep logs it as "gate rule ... reached
 * carrot"). Measured with the sweep ({@code WILDROOTS_SWEEP_LINES=120
 * WILDROOTS_SWEEP_GENS=20}, five runs on clean worlds, 2026-11-17): 178 of
 * 600 lines, p = 0.30 (0.26-0.38 per run, a little wider than binomial);
 * the one-sided 99.9 % Clopper-Pearson lower bound is 0.24. With
 * {@value #MAX_LINES} lines the false-failure probability is
 * {@code (1 − 0.24)^120 = 4e-15} at that bound and stays below 1e-4 for every
 * {@code p >= 1 − 1e-4^(1/120) = 0.074}, a quarter of the measured value, which
 * is the margin for run-to-run spread. The earlier limit of 30 lines had
 * {@code 0.70^30 = 2.6e-5} per run, too close: CI run 44041828594 found no
 * carrot in 30 lines. A line takes 0.2-0.5 s and a run needs about three
 * lines, so the margin costs time only in the failure case.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M1aGateGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final int MAX_GEN = 20;
    static final int ABANDON_WITHOUT_ORANGE = 10;
    /**
     * Lines the gate player may start in all: one per starter-patch origin of the grid. The gate is a
     * statistical claim ("a player gets the cultivar from <i>some</i> starter patch"), so its false-failure
     * probability is {@code (1 − p)^MAX_LINES} for the per-line success share {@code p} of the player
     * (see the class comment, "False failures").
     */
    static final int MAX_LINES = 120;
    /** Real bees: at most this many ticks for the first bee visits. */
    static final int BEE_WAIT = 12_000;
    static final int BEES = 8;
    static final int TIMEOUT = 40_000;
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(M1aGateGameTests.class);

    private M1aGateGameTests() {
    }

    /**
     * The grid of starter-patch origins: 12 columns × 10 rows at x −14…−3, z −14…−5, which keeps the
     * patches (spread ≤ 8) off the beds (x ≥ 2) and off the mother cells (x ≥ 6).
     */
    static final int ORIGIN_COLUMNS = 12;
    static final int ORIGINS = ORIGIN_COLUMNS * 10;
    static {
        assert MAX_LINES <= ORIGINS;
    }

    /** Fixed starter-patch origin of line {@code i} on the platform: other origin, other patch seed. */
    static BlockPos origin(SkyPlatform sky, int i) {
        if (i < 0 || i >= ORIGINS) {
            throw new IllegalArgumentException("no starter-patch origin for line " + i);
        }
        return sky.center.offset(-14 + i % ORIGIN_COLUMNS, 1, -14 + i / ORIGIN_COLUMNS);
    }

    /** The gate player gives a line up: the generation limit, or no orange root by the abandon generation. */
    static boolean abandon(int generation, int orangeGen) {
        return generation >= MAX_GEN || (generation >= ABANDON_WITHOUT_ORANGE && orangeGen < 0);
    }

    /**
     * Whether a line with this {@code carrotGen} (planting generation of the first {@code carrot}, -1 if none)
     * and first orange root {@code orangeGen} reaches the cultivar under the gate player's rules; the same rule
     * as {@link Gate#step} (a carrot is checked before the abandon rule), used by the sweep to report the
     * per-line share {@code p} of the gate.
     */
    static boolean gateSucceeds(int carrotGen, int orangeGen) {
        return carrotGen >= 0 && carrotGen <= MAX_GEN
                && (carrotGen <= ABANDON_WITHOUT_ORANGE || (orangeGen >= 0 && orangeGen <= ABANDON_WITHOUT_ORANGE));
    }

    /** {@code climate.wilting} for the gate: {@code WILDROOTS_GATE_WILTING=false} switches it off, otherwise on. */
    static boolean gateWilting() {
        return !"false".equalsIgnoreCase(System.getenv("WILDROOTS_GATE_WILTING"));
    }

    /** Async driver of the gate: one step per tick, so the server keeps ticking. */
    static final class Gate {
        final GameTestHelper helper;
        final ServerLevel level;
        final SkyPlatform sky;
        final JournalGameTests.Recorder recorder = new JournalGameTests.Recorder();
        final long started = System.nanoTime();
        final List<String> lines = new ArrayList<>();
        BreedingLine line;
        int lineNo = -1;
        int beeTicks;
        List<BlockPos> waitingMothers;
        List<Bee> bees = List.of();
        int totalGenerations;
        int realBeeVisited;
        int realBeeDonors;
        /** Undo of the plains biome painted over the platform (closed once). */
        Runnable undoBiome;

        Gate(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.sky = SkyPlatform.build(helper, BreedingLine.PLATFORM_RADIUS);
            level.setDayTime(1000);
            level.setWeatherParameters(20_000, 0, false, false);
            // WR-715 (carrot-colours-climate §4.7): the gate runs with climate.wilting as WILDROOTS_GATE_WILTING says
            // (default true; the claim is that it passes with both), in the climate it is designed for: the platform
            // lies at y 250, where the height takes 0.21 off the biome's temperature, so the biome of this world's seed
            // would decide whether the player's plants are stressed. Plains (0.8, 0.59 up there) is inside the window.
            ServerConfig.overrideForTests(ServerConfig.CLIMATE_WILTING, gateWilting());
            undoBiome = ClimateKit.setBiome(level, sky.center, BreedingLine.PLATFORM_RADIUS + 8, Biomes.PLAINS);
        }

        void newLine() {
            if (line != null) {
                lines.add(line.summary());
                line.close();
            }
            lineNo++;
            if (lineNo >= MAX_LINES) {
                fail("no carrot in " + MAX_LINES + " lines (the gate fails with probability below 1e-4 if the per-line"
                        + " share is 0.074 or more; measured 0.26-0.38 per run): " + lines);
            }
            line = new BreedingLine(level, sky.center, 0x195_0000L + lineNo, "wr195-breeder-" + lineNo);
            line.start(sky, origin(sky, lineNo));
        }

        void fail(String why) {
            close();
            throw new net.minecraft.gametest.framework.GameTestAssertException(why);
        }

        void close() {
            if (undoBiome != null) {
                undoBiome.run();
                undoBiome = null;
                ServerConfig.overrideForTests(ServerConfig.CLIMATE_WILTING, ClimateGameTestDefaults.DEFAULT);
            }
            recorder.close();
            for (Bee b : bees) {
                b.discard();
            }
            if (line != null) {
                line.close();
            }
            sky.clear();
        }

        /** First generation of the first line: mothers in flower, a hive and real bees around them. */
        void startRealBees() {
            waitingMothers = line.plantMothersToFlower();
            level.setBlock(line.hive, Blocks.BEEHIVE.defaultBlockState(), 3);
            List<Bee> spawned = new ArrayList<>();
            for (int i = 0; i < BEES; i++) {
                Bee b = EntityType.BEE.create(level);
                BlockPos at = line.motherCells.get(i % line.motherCells.size()).above(2);
                b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                level.addFreshEntity(b);
                spawned.add(b);
            }
            bees = spawned;
            beeTicks = 0;
        }

        void step() {
            if (waitingMothers != null) {
                beeTicks++;
                int[] st = line.beeState(waitingMothers);
                if ((st[0] >= waitingMothers.size() / 2 && st[1] >= 1) || beeTicks >= BEE_WAIT) {
                    realBeeVisited = st[0];
                    realBeeDonors = st[1];
                    LOG.info("WR-195 real bees: {} of {} mothers visited, {} bee donors after {} ticks",
                            st[0], waitingMothers.size(), st[1], beeTicks);
                    if (st[0] == 0) {
                        fail("real bees visited no mother in " + BEE_WAIT + " ticks");
                    }
                    for (Bee b : bees) {
                        b.discard();
                    }
                    bees = List.of();
                    level.setBlock(line.hive, Blocks.AIR.defaultBlockState(), 3);
                    line.finishGeneration(waitingMothers);
                    totalGenerations++;
                    waitingMothers = null;
                }
                helper.runAfterDelay(1, this::step);
                return;
            }
            if (line.carrot()) {
                done();
                return;
            }
            if (abandon(line.generation, line.orangeGen)) {
                newLine();
            }
            line.generation();
            totalGenerations++;
            helper.runAfterDelay(1, this::step);
        }

        void done() {
            double secs = (System.nanoTime() - started) / 1e9;
            BreedingLine l = line;
            JournalData j = JournalGameTests.journal(l.breeder);
            boolean easternPage = j.has(JournalTriggers.cultivarEntry("wildroots:eastern_carrot"));
            boolean carrotPage = j.has(JournalTriggers.cultivarEntry("wildroots:carrot"));
            boolean firstLandrace = j.has("hint.first_landrace");
            boolean firstCultivar = j.has("hint.first_cultivar");
            long landraceToasts = recorder.toasts.stream().filter(t -> t.title().key().equals(CultivarBred.LANDRACE_TITLE))
                    .count();
            long cultivarToasts = recorder.toasts.stream().filter(t -> t.title().key().equals(CultivarBred.CULTIVAR_TITLE))
                    .count();
            lines.add(l.summary());
            LOG.info("WR-195 gate: carrot in line {} after {} generations (eastern at {}), {} generations in all,"
                    + " {} s; real bees (line 0, generation 1) visited {} mothers ({} donors); lines {}; trace {}",
                    lineNo, l.carrotGen, l.easternGen, totalGenerations, String.format(Locale.ROOT, "%.1f", secs),
                    realBeeVisited, realBeeDonors, lines, l.trace);
            try {
                helper.assertTrue(l.easternGen >= 1 && l.easternGen <= l.carrotGen,
                        "eastern_carrot not recognised before carrot: " + l.summary());
                helper.assertTrue(easternPage && carrotPage, "cultivar pages: eastern " + easternPage + ", carrot "
                        + carrotPage);
                helper.assertTrue(firstLandrace && firstCultivar, "hints: first_landrace " + firstLandrace
                        + ", first_cultivar " + firstCultivar);
                helper.assertTrue(landraceToasts >= 1 && cultivarToasts >= 1, "toasts: landrace " + landraceToasts
                        + ", cultivar " + cultivarToasts);
                helper.assertTrue(realBeeVisited >= 1, "no mother visited by real bees in the first generation");
            } finally {
                close();
            }
            succeedWhenLit(helper, sky, 0);
        }
    }

    /**
     * The gate: a starter patch, generations of the {@link BreedingLine}
     * player, real bees in the first generation, until a plant planted by
     * the player is recognised as {@code carrot}.
     */
    @GameTest(template = FLOOR, batch = "wr195_m1a_gate", timeoutTicks = TIMEOUT)
    public static void fromStarterPatchToCarrot(GameTestHelper helper) {
        Gate g = new Gate(helper);
        whenEntitiesLoaded(helper, g.sky, () -> {
            try {
                g.newLine();
                g.startRealBees();
            } catch (RuntimeException e) {
                g.close();
                throw e;
            }
            helper.runAfterDelay(1, g::step);
        }, 0);
    }

    /**
     * Runs {@code body} once the entity sections of the platform's chunks are
     * loaded (at most 400 ticks). The harvest picks up item entities and the
     * bees are entities; the gate batch may be the first of a run, while the
     * server still loads the area (one run found no drops at all, WR-195).
     */
    static void whenEntitiesLoaded(GameTestHelper helper, SkyPlatform sky, Runnable body, int waited) {
        ServerLevel level = helper.getLevel();
        boolean loaded = true;
        for (int dx = -sky.radius; dx <= sky.radius; dx += 8) {
            for (int dz = -sky.radius; dz <= sky.radius; dz += 8) {
                BlockPos p = sky.center.offset(dx, 1, dz);
                loaded &= level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(p))
                        && level.isPositionEntityTicking(p);
            }
        }
        if (loaded || waited >= 400) {
            if (waited > 0) {
                LOG.info("WR-195 platform entities loaded after {} ticks ({})", waited, loaded);
            }
            body.run();
            return;
        }
        helper.runAfterDelay(1, () -> whenEntitiesLoaded(helper, sky, body, waited + 1));
    }

    // ------------------------------------------------------------------ sweep (opt-in)

    static boolean sweepEnabled() {
        String e = System.getenv("WILDROOTS_SWEEP");
        return "1".equals(e) || "true".equalsIgnoreCase(e) || "true".equalsIgnoreCase(System.getProperty("wildroots.sweep"));
    }

    /**
     * The per-patch sweep (opt-in, {@code WILDROOTS_SWEEP=1}): {@code WILDROOTS_SWEEP_LINES}
     * (20) starter patches at fixed origins, each line run to
     * {@code WILDROOTS_SWEEP_GENS} (30) generations without giving up,
     * accelerated bees in every generation. Log lines {@code [WR-195] sweep}.
     */
    @GameTestGenerator
    public static Collection<TestFunction> sweep() {
        if (!sweepEnabled()) {
            return List.of();
        }
        return List.of(new TestFunction("wr195_sweep", "wildroots.m1a_sweep", "wildroots:" + FLOOR, 20_000, 0L, true,
                M1aGateGameTests::runSweep));
    }

    static int conf(String env, int def) {
        String v = System.getenv(env);
        return v == null ? def : Integer.parseInt(v.trim());
    }

    static void runSweep(GameTestHelper helper) {
        int linesN = conf("WILDROOTS_SWEEP_LINES", 20);
        int gens = conf("WILDROOTS_SWEEP_GENS", 30);
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, BreedingLine.PLATFORM_RADIUS);
        int[] eastern = new int[linesN];
        int[] carrot = new int[linesN];
        int[] orange = new int[linesN];
        int[] gateOk = {0};
        double[] secs = new double[linesN];
        int[] i = {0};
        BreedingLine[] cur = {null};
        Runnable[] step = {null};
        step[0] = () -> {
            if (cur[0] == null) {
                cur[0] = new BreedingLine(level, sky.center, 0x5EE9_0000L + i[0], "wr195-sweep-" + i[0]);
                cur[0].start(sky, origin(sky, i[0]));
                secs[i[0]] = System.nanoTime();
            } else {
                cur[0].generation();
            }
            BreedingLine l = cur[0];
            if (l.carrot() || l.generation >= gens) {
                eastern[i[0]] = l.easternGen;
                carrot[i[0]] = l.carrotGen;
                orange[i[0]] = l.orangeGen;
                gateOk[0] += gateSucceeds(l.carrotGen, l.orangeGen) ? 1 : 0;
                secs[i[0]] = (System.nanoTime() - secs[i[0]]) / 1e9;
                LOG.info("[WR-195] sweep line {}: {}; {} s; trace {}", i[0], l.summary(),
                        String.format(Locale.ROOT, "%.1f", secs[i[0]]), l.trace);
                l.close();
                cur[0] = null;
                i[0]++;
                if (i[0] >= linesN) {
                    long ok = Arrays.stream(carrot).filter(x -> x >= 0).count();
                    int[] okGens = Arrays.stream(carrot).filter(x -> x >= 0).sorted().toArray();
                    int[] eGens = Arrays.stream(eastern).filter(x -> x >= 0).sorted().toArray();
                    LOG.info("[WR-195] sweep SUMMARY: {} lines, gate rule (give up at generation {} without orange,"
                            + " at {}) reached carrot in {}, carrot by gen {} in {} ({}%), carrot generations {}"
                            + " (median {}), eastern generations {} (median {}), first orange root {}, seconds per line {}",
                            linesN, ABANDON_WITHOUT_ORANGE, MAX_GEN, gateOk[0], gens, ok, 100 * ok / linesN,
                            Arrays.toString(okGens), median(okGens),
                            Arrays.toString(eGens), median(eGens), Arrays.toString(orange), Arrays.toString(secs));
                    sky.clear();
                    succeedWhenLit(helper, sky, 0);
                    return;
                }
            }
            helper.runAfterDelay(1, () -> step[0].run()); // a new Runnable: the tick map is keyed by it
        };
        helper.runAfterDelay(1, () -> step[0].run()); // a new Runnable: the tick map is keyed by it
    }

    /**
     * Succeeds once the sky light under the removed platform is back (at
     * most 200 ticks), so the next batch does not start in its shadow.
     */
    static void succeedWhenLit(GameTestHelper helper, SkyPlatform sky, int waited) {
        if (sky.lightSettled() || waited >= 200) {
            if (waited > 0) {
                LOG.info("WR-195 sky light back after {} ticks (settled {})", waited, sky.lightSettled());
            }
            helper.succeed();
            return;
        }
        helper.runAfterDelay(1, () -> succeedWhenLit(helper, sky, waited + 1));
    }

    static double median(int[] sorted) {
        if (sorted.length == 0) {
            return -1;
        }
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
