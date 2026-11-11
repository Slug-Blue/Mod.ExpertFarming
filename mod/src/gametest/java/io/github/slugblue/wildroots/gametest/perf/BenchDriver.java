package io.github.slugblue.wildroots.gametest.perf;

import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.ref.Reference;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BloomIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.FarmlandWaterManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * WR-028 (S9), ported in WR-195: performance bench of 10 000 real {@code wildroots:wild_carrot}
 * plants ({@code FieldPlantBlock} + {@code PlantBlockEntity}, bloom index, seed set) in 40 chunks,
 * A/B against vanilla wheat.
 *
 * <p><b>How random ticks are made realistic without a player.</b> Each of the 40 chunks
 * gets a Forge region ticket {@code addRegionTicket(TicketType.FORCED, pos, 2, pos, true)};
 * the last argument ({@code forceTicks}) puts it into {@code DistanceManager.forcedTickets},
 * and the Forge patch of {@code ServerChunkCache.tickChunks()} then calls the vanilla
 * {@code ServerLevel.tickChunk(chunk, randomTickSpeed)} for it even though
 * {@code ChunkMap.anyPlayerCloseEnoughForSpawning} is false. So random ticks go through the
 * unmodified vanilla loop with {@code randomTickSpeed = 3} (set explicitly).
 *
 * <p><b>What is measured.</b> Per tick: (1) {@code System.nanoTime} between
 * {@code LevelTickEvent} START and END of the overworld (= {@code ServerLevel.tick});
 * (2) the vanilla MSPT sample {@code MinecraftServer.tickTimes[tickCount % 100]}, read in
 * {@code ServerTickEvent} END (written just before it in {@code tickServer}). Autosave ticks
 * ({@code tickCount % 6000 == 0}) are reported separately.
 *
 * <p><b>Schedule.</b> Setup → memory probes → one unrecorded warm-up window per variant →
 * {@code reps} rounds of [EMPTY, WHEAT, REAL] (order rotated per round), each window:
 * fresh layout with uniform random stages (same seed for both crops), {@code warm} ticks,
 * then {@code measure} recorded ticks.
 */
public final class BenchDriver {

    private static final Logger LOG = LogUtils.getLogger();

    enum Variant { EMPTY, WHEAT, REAL }

    // Layout: 8 x 5 chunks, 250 plants per chunk = 10 000 plants
    static final int CX0 = 64;
    static final int CZ0 = 64;
    static final int CW = 8;
    static final int CH = 5;
    static final int PER_CHUNK = 250;
    static final int Y_FARM = 100;
    static final int Y_PLANT = Y_FARM + 1;

    final int reps = intConf("wildroots.bench.reps", 6);
    final int warm = intConf("wildroots.bench.warm", 300);
    final int measure = intConf("wildroots.bench.measure", 1200);
    final int warmupWindow = intConf("wildroots.bench.warmup", 1000);
    final int setupWait = 100;

    private final ServerLevel level;
    private final MinecraftServer server;
    private final List<BlockPos> cells = new ArrayList<>(CW * CH * PER_CHUNK);
    private final Deque<Window> queue = new ArrayDeque<>();
    private final List<Window> done = new ArrayList<>();
    private final StringBuilder memReport = new StringBuilder();
    private double memTypicalBytes;
    private double memInWorldBytes;

    private Window current;
    private int tickInWindow;
    private int setupTicks;
    private long levelStart;
    private long lastLevelNs;

    volatile boolean finished;
    volatile String failure;

    private BenchDriver(ServerLevel level) {
        this.level = level;
        this.server = level.getServer();
    }

    static int intConf(String key, int def) {
        String v = System.getProperty(key);
        if (v == null) {
            v = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        }
        return v == null ? def : Integer.parseInt(v.trim());
    }

    public static BenchDriver start(ServerLevel level) {
        BenchDriver d = new BenchDriver(level);
        d.setup();
        MinecraftForge.EVENT_BUS.register(d);
        return d;
    }

    static final class Window {
        final Variant variant;
        final int rep;
        final int warm;
        final int measure;
        final boolean record;
        final long[] levelNs;
        final long[] serverNs;
        final boolean[] autosave;
        String counters = "";
        int stageHistogramStart;

        Window(Variant variant, int rep, int warm, int measure, boolean record) {
            this.variant = variant;
            this.rep = rep;
            this.warm = warm;
            this.measure = measure;
            this.record = record;
            this.levelNs = new long[measure];
            this.serverNs = new long[measure];
            this.autosave = new boolean[measure];
        }
    }

    // ------------------------------------------------------------------ setup

    /** Day time at the start: restored at the end, so later bee tests do not run at night (WR-195). */
    private long dayTimeAtStart;
    private int randomTickSpeedAtStart;

    private void setup() {
        dayTimeAtStart = level.getDayTime();
        randomTickSpeedAtStart = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        logEnvironment();
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3, server);
        for (int i = 0; i < CW; i++) {
            for (int j = 0; j < CH; j++) {
                ChunkPos cp = new ChunkPos(CX0 + i, CZ0 + j);
                level.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 2, cp, true);
                level.getChunk(cp.x, cp.z);
                for (int idx = 0; idx < PER_CHUNK; idx++) {
                    cells.add(new BlockPos(cp.getMinBlockX() + (idx & 15), Y_PLANT, cp.getMinBlockZ() + (idx >> 4)));
                }
                // farmland under the whole chunk (16 x 16), moisture 7
                BlockState farm = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        level.setBlock(new BlockPos(cp.getMinBlockX() + lx, Y_FARM, cp.getMinBlockZ() + lz), farm, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // Keep farmland hydrated without water blocks (same for all variants)
        FarmlandWaterManager.addAABBTicket(level, new AABB(CX0 * 16, Y_FARM - 1, CZ0 * 16,
                (CX0 + CW) * 16, Y_FARM + 2, (CZ0 + CH) * 16));
        LOG.info("[S9] setup: {} chunks forced with forceTicks, {} plant cells, randomTickSpeed={}",
                CW * CH, cells.size(), level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));

        for (Variant v : Variant.values()) {
            queue.add(new Window(v, 0, warmupWindow, 0, false));
        }
        for (int r = 1; r <= reps; r++) {
            Variant[] order = Variant.values();
            for (int k = 0; k < order.length; k++) {
                queue.add(new Window(order[(k + r) % order.length], r, warm, measure, true));
            }
        }
    }

    private void logEnvironment() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Runtime r = Runtime.getRuntime();
        StringBuilder gcs = new StringBuilder();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.append(gc.getName()).append(';');
        }
        LOG.info("[S9] jvm: {} {} ({}), cpus={}, maxHeap={} MiB, gc={}", rt.getVmName(), rt.getVmVersion(),
                System.getProperty("java.vendor"), r.availableProcessors(), r.maxMemory() >> 20, gcs);
        LOG.info("[S9] jvm args: {}", rt.getInputArguments());
        LOG.info("[S9] config: reps={} warm={} measure={} warmupWindow={}", reps, warm, measure, warmupWindow);
    }

    // ----------------------------------------------------------------- layout

    private void clearPlants() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : cells) {
            level.setBlock(p, air, Block.UPDATE_CLIENTS);
        }
        BloomIndex.forget(level);
        // The flat GameTest world generates villages (also around spawn, whose chunks tick
        // entities): drop every non-player entity so that mob AI does not add noise to the
        // bench (same for every variant; doMobSpawning is already false in GameTestServer).
        List<Entity> mobs = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e != null && !(e instanceof Player)) {
                mobs.add(e);
            }
        }
        mobs.forEach(Entity::discard);
        entitiesRemoved += mobs.size();
    }

    private int entitiesRemoved;

    /**
     * Farmland back to moisture 7 under every cell. Needed because the Forge
     * {@code FarmlandWaterManager} AABB ticket did not keep it hydrated in practice: in the
     * EMPTY variant bare farmland dried and turned to dirt, and the next layout's plants
     * broke on neighbour shape updates (seen as "no BE" + item drops).
     */
    private void resetFarmland() {
        BlockState farm = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int i = 0; i < CW; i++) {
            for (int j = 0; j < CH; j++) {
                int x0 = (CX0 + i) << 4;
                int z0 = (CZ0 + j) << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        level.setBlock(new BlockPos(x0 + lx, Y_FARM, z0 + lz), farm, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
    private int missingBe;

    /**
     * Uniform random stages, the same seed for both crops: wheat ages 0..6,
     * the real {@code wildroots:wild_carrot} stages 0..4 (both: final stage
     * excluded). A carrot is a player plant placed by {@code PlantService.place}
     * (genome, noise, look, growth chance; flowering plants enter the bloom
     * index through {@code onPlace}); a flowering plant carries a pending lot of
     * {@code RealPlants.DONORS} bee donors, as after a bee visit.
     */
    private void layout(Variant v, long seed) {
        clearPlants();
        resetFarmland();
        if (v == Variant.EMPTY) {
            return;
        }
        RandomSource stages = RandomSource.create(seed);
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            double u = stages.nextDouble();
            if (v == Variant.WHEAT) {
                level.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, (int) (u * 7)), Block.UPDATE_CLIENTS);
            } else {
                int stage = (int) (u * 5);
                if (!RealPlants.place(level, p, stage, seed * 31 + i) && missingBe++ < 5) {
                    LOG.warn("[S9] no BE at {}: state={} below={}", p, level.getBlockState(p), level.getBlockState(p.below()));
                }
            }
        }
    }

    // ----------------------------------------------------------------- memory

    private static long usedAfterGc() {
        Runtime r = Runtime.getRuntime();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            best = Math.min(best, r.totalMemory() - r.freeMemory());
        }
        return best;
    }

    /** WR-131: a decoded block entity without a pending lot (owner, 2026-11-11: 400 B, was 300 B). */
    static final int BE_BUDGET_BYTES = 400;

    private void measureMemory() {
        final int n = 10_000;
        double[] typical = new double[3];
        double[] lazy = new double[3];
        double[] worst = new double[3];
        // rep -1 is a discarded warm-up (first-GC noise gave negative deltas in early runs)
        for (int rep = -1; rep < 3; rep++) {
            long key = 100_000L * (rep + 2);
            PlantBlockEntity[] arr = new PlantBlockEntity[n];
            long before = usedAfterGc();
            for (int i = 0; i < n; i++) {
                arr[i] = RealPlants.offWorld(new BlockPos(i, 64, -i), 2, key + i, false);
            }
            long after = usedAfterGc();
            // keep arr strongly reachable across the GC (an OSR-compiled loop may treat it as dead)
            Reference.reachabilityFence(arr);
            if (rep >= 0) {
                typical[rep] = (after - before) / (double) n;
            }
            arr = null;
            PlantBlockEntity[] arr3 = new PlantBlockEntity[n];
            before = usedAfterGc();
            for (int i = 0; i < n; i++) {
                arr3[i] = RealPlants.lazy(new BlockPos(i, 64, -i), 2, key + i);
            }
            after = usedAfterGc();
            Reference.reachabilityFence(arr3);
            if (rep >= 0) {
                lazy[rep] = (after - before) / (double) n;
            }
            arr3 = null;
            PlantBlockEntity[] arr2 = new PlantBlockEntity[n];
            before = usedAfterGc();
            for (int i = 0; i < n; i++) {
                arr2[i] = RealPlants.offWorld(new BlockPos(i, 64, -i), 4, key + i, true);
            }
            after = usedAfterGc();
            Reference.reachabilityFence(arr2);
            if (rep >= 0) {
                worst[rep] = (after - before) / (double) n;
            }
            if (arr2.length != n) {
                throw new IllegalStateException();
            }
        }
        // In-world: same 10 000 cells, wheat layout vs real layout (BE object + chunk BE map + index + lots)
        double[] inWorld = new double[3];
        for (int rep = 0; rep < 3; rep++) {
            layout(Variant.WHEAT, 100 + rep);
            long wheat = usedAfterGc();
            layout(Variant.REAL, 100 + rep);
            long real = usedAfterGc();
            inWorld[rep] = (real - wheat) / (double) n;
        }
        clearPlants();
        // NBT size of one BE (uncompressed, as written into the chunk)
        int nbtTypical = nbtSize(RealPlants.offWorld(new BlockPos(1, 2, 3), 2, 7, false).saveWithFullMetadata());
        int nbtWorst = nbtSize(RealPlants.offWorld(new BlockPos(1, 2, 3), 4, 8, true).saveWithFullMetadata());

        memTypicalBytes = median(typical);
        memInWorldBytes = median(inWorld);
        memReport.append(String.format(Locale.ROOT,
                "[S9] memory/plant bytes: offworld typical(decoded, no lot)=%s median=%.1f; offworld lazy(undecoded)=%s"
                        + " median=%.1f; offworld worst(lot of 4 fathers + 4 donors)=%s median=%.1f;"
                        + " in-world real-minus-wheat=%s median=%.1f; NBT uncompressed typical=%d worst=%d",
                fmt(typical), memTypicalBytes, fmt(lazy), median(lazy), fmt(worst), median(worst), fmt(inWorld),
                memInWorldBytes, nbtTypical, nbtWorst));
        LOG.info(memReport.toString());
    }

    private static int nbtSize(CompoundTag tag) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.size();
    }

    // ------------------------------------------------------------------ ticks

    /**
     * Keep the level free of mobs while the bench runs: villages of the flat GameTest world
     * (incl. spawn chunks, which tick entities) keep re-adding villagers/golems/cats when
     * their entity sections load, and their AI dominates the noise (~2 ms swings).
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() == level && !(event.getEntity() instanceof Player)) {
            event.setCanceled(true);
            entitiesBlocked++;
        }
    }

    private int entitiesBlocked;

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level != level) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            levelStart = System.nanoTime();
        } else {
            lastLevelNs = System.nanoTime() - levelStart;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        try {
            step();
        } catch (RuntimeException e) {
            LOG.error("[S9] bench crashed", e);
            failure = "bench crashed: " + e;
            finish();
        }
    }

    private void step() {
        if (setupTicks < setupWait) {
            setupTicks++;
            if (setupTicks == setupWait) {
                measureMemory();
            }
            return;
        }
        if (current == null) {
            current = queue.poll();
            if (current == null) {
                summarize();
                finish();
                return;
            }
            layout(current.variant, current.rep);
            BenchCounters.reset();
            tickInWindow = 0;
            return;
        }
        tickInWindow++;
        if (tickInWindow == current.warm) {
            BenchCounters.reset();
        }
        int m = tickInWindow - current.warm - 1;
        if (m >= 0 && m < current.measure) {
            int tc = server.getTickCount();
            current.levelNs[m] = lastLevelNs;
            current.serverNs[m] = server.tickTimes[tc % 100];
            current.autosave[m] = tc % 6000 == 0;
        }
        if (tickInWindow >= current.warm + current.measure) {
            current.counters = BenchCounters.snapshot() + " bloomIndexSize=" + bloomSize()
                    + " entitiesRemovedTotal=" + entitiesRemoved
                    + " loadedChunks=" + level.getChunkSource().getLoadedChunksCount()
                    + " entities=" + countEntities() + " missingBe=" + missingBe + " entitiesBlocked=" + entitiesBlocked + " " + layerStats();
            if (current.record) {
                LOG.info("[S9] window {} rep={} {}", current.variant, current.rep, describe(current));
                done.add(current);
            }
            current = null;
        }
    }

    private int bloomSize() {
        BloomIndex idx = BloomIndex.peek(level);
        return idx == null ? 0 : idx.size();
    }

    /** Farmland/dirt and plant counts in the bench layer at window end (stage histogram of the real plants). */
    private String layerStats() {
        int farm = 0;
        int dry = 0;
        int dirt = 0;
        int plants = 0;
        int ticking = 0;
        int[] byStage = new int[6];
        for (BlockPos p : cells) {
            BlockState below = level.getBlockState(p.below());
            if (below.is(Blocks.FARMLAND)) {
                farm++;
                if (below.getValue(FarmBlock.MOISTURE) == 0) {
                    dry++;
                }
            } else if (below.is(Blocks.DIRT)) {
                dirt++;
            }
            BlockState st = level.getBlockState(p);
            if (!st.isAir()) {
                plants++;
                if (st.isRandomlyTicking()) {
                    ticking++;
                }
                if (st.getBlock() instanceof FieldPlantBlock) {
                    byStage[FieldPlantBlock.stage(st)]++;
                }
            }
        }
        return "farmland=" + farm + " dryFarmland=" + dry + " dirt=" + dirt + " plants=" + plants + " tickingPlants=" + ticking
                + " realStages=" + Arrays.toString(byStage);
    }

    private int countEntities() {
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            n++;
        }
        return n;
    }

    private void finish() {
        finished = true;
        MinecraftForge.EVENT_BUS.unregister(this);
        clearPlants();
        // leave the world as found: day time, random tick speed, forced chunks (later batches, WR-195)
        level.setDayTime(dayTimeAtStart);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(randomTickSpeedAtStart, server);
        for (int i = 0; i < CW; i++) {
            for (int j = 0; j < CH; j++) {
                ChunkPos cp = new ChunkPos(CX0 + i, CZ0 + j);
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, 2, cp, true);
            }
        }
    }

    // ------------------------------------------------------------------ stats

    static final class Stats {
        double levelMean;
        double levelMedian;
        double levelP95;
        double serverMean;
        int autosaves;
        double autosaveMaxMs;
    }

    private static Stats stats(Window w) {
        Stats s = new Stats();
        long[] lv = new long[w.measure];
        long[] sv = new long[w.measure];
        int k = 0;
        double asMax = 0;
        for (int i = 0; i < w.measure; i++) {
            if (w.autosave[i]) {
                s.autosaves++;
                asMax = Math.max(asMax, w.serverNs[i] / 1e6);
                continue;
            }
            lv[k] = w.levelNs[i];
            sv[k] = w.serverNs[i];
            k++;
        }
        lv = Arrays.copyOf(lv, k);
        sv = Arrays.copyOf(sv, k);
        s.levelMean = Arrays.stream(lv).average().orElse(0) / 1e6;
        s.serverMean = Arrays.stream(sv).average().orElse(0) / 1e6;
        Arrays.sort(lv);
        s.levelMedian = lv[k / 2] / 1e6;
        s.levelP95 = lv[(int) (k * 0.95)] / 1e6;
        s.autosaveMaxMs = asMax;
        return s;
    }

    private static String describe(Window w) {
        Stats s = stats(w);
        return String.format(Locale.ROOT,
                "levelTick mean=%.4f median=%.4f p95=%.4f ms; serverTick(MSPT) mean=%.4f ms; autosaves=%d (max %.1f ms); %s",
                s.levelMean, s.levelMedian, s.levelP95, s.serverMean, s.autosaves, s.autosaveMaxMs, w.counters);
    }

    private void summarize() {
        StringBuilder csv = new StringBuilder("variant,rep,level_mean_ms,level_median_ms,level_p95_ms,server_mean_ms,autosaves,counters\n");
        double[][] levelMean = new double[3][reps];
        double[][] serverMean = new double[3][reps];
        for (Window w : done) {
            Stats s = stats(w);
            levelMean[w.variant.ordinal()][w.rep - 1] = s.levelMean;
            serverMean[w.variant.ordinal()][w.rep - 1] = s.serverMean;
            csv.append(String.format(Locale.ROOT, "%s,%d,%.4f,%.4f,%.4f,%.4f,%d,\"%s\"%n", w.variant, w.rep,
                    s.levelMean, s.levelMedian, s.levelP95, s.serverMean, s.autosaves, w.counters));
        }
        StringBuilder out = new StringBuilder();
        for (Variant v : Variant.values()) {
            out.append(String.format(Locale.ROOT, "%n[S9] %-5s levelTick mean per rep %s -> median %.4f ms; MSPT per rep %s -> median %.4f ms",
                    v, fmt(levelMean[v.ordinal()]), median(levelMean[v.ordinal()]),
                    fmt(serverMean[v.ordinal()]), median(serverMean[v.ordinal()])));
        }
        double[] dRealWheat = diff(serverMean[2], serverMean[1]);
        double[] dRealWheatLevel = diff(levelMean[2], levelMean[1]);
        double[] dWheatEmpty = diff(serverMean[1], serverMean[0]);
        double[] dRealEmpty = diff(serverMean[2], serverMean[0]);
        out.append(String.format(Locale.ROOT, "%n[S9] delta MSPT REAL-WHEAT per rep %s -> median %.4f ms (min %.4f, max %.4f)",
                fmt(dRealWheat), median(dRealWheat), min(dRealWheat), max(dRealWheat)));
        out.append(String.format(Locale.ROOT, "%n[S9] delta levelTick REAL-WHEAT per rep %s -> median %.4f ms (min %.4f, max %.4f)",
                fmt(dRealWheatLevel), median(dRealWheatLevel), min(dRealWheatLevel), max(dRealWheatLevel)));
        out.append(String.format(Locale.ROOT, "%n[S9] delta MSPT WHEAT-EMPTY per rep %s -> median %.4f ms",
                fmt(dWheatEmpty), median(dWheatEmpty)));
        out.append(String.format(Locale.ROOT, "%n[S9] delta MSPT REAL-EMPTY per rep %s -> median %.4f ms",
                fmt(dRealEmpty), median(dRealEmpty)));
        out.append('\n').append(memReport);
        LOG.info("[S9] SUMMARY{}", out);
        try {
            Path dir = Path.of("wildroots-bench");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("s9-windows.csv"), csv, StandardCharsets.UTF_8);
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dir.resolve("s9-summary.txt"), StandardCharsets.UTF_8))) {
                pw.println(out.toString().trim());
            }
        } catch (IOException e) {
            LOG.warn("[S9] could not write results", e);
        }
        double budget = Double.parseDouble(System.getProperty("wildroots.bench.budgetMs", "2.0"));
        double warn = Double.parseDouble(System.getProperty("wildroots.bench.warnMs", "0.5"));
        if (median(dRealWheat) > warn) {
            LOG.warn("[S9] WARN: MSPT delta {} ms > warning threshold {} ms (WR-195)",
                    String.format(Locale.ROOT, "%.3f", median(dRealWheat)), warn);
        }
        if (median(dRealWheat) > budget) {
            failure = String.format(Locale.ROOT, "MSPT delta %.3f ms > budget %.1f ms", median(dRealWheat), budget);
        } else if (memInWorldBytes > 1024) {
            failure = String.format(Locale.ROOT, "memory %.0f B/plant > 1 KiB", memInWorldBytes);
        } else if (memTypicalBytes > BE_BUDGET_BYTES) {
            failure = String.format(Locale.ROOT, "block entity %.0f B > %d B (WR-131)", memTypicalBytes, BE_BUDGET_BYTES);
        }
    }

    private static double[] diff(double[] a, double[] b) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            d[i] = a[i] - b[i];
        }
        return d;
    }

    static double median(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        int n = c.length;
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2;
    }

    private static double min(double[] v) {
        return Arrays.stream(v).min().orElse(0);
    }

    private static double max(double[] v) {
        return Arrays.stream(v).max().orElse(0);
    }

    private static String fmt(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.4f", v[i]));
        }
        return sb.append(']').toString();
    }
}
