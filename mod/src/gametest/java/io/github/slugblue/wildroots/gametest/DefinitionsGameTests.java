package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.DefinitionLoader;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.resource.PathPackResources;

/** WR-125: species and cultivar loading through the reload listeners; /reload swaps thresholds. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefinitionsGameTests {

    private DefinitionsGameTests() {
    }

    /** Test data pack shipped with the GameTest sources. */
    static Path testPack(String name) {
        return ModList.get().getModFileById(WildRoots.MOD_ID).getFile().findResource("wr_testpacks", name);
    }

    /** Runs the server's species and cultivar listeners over one pack, without touching the live snapshot. */
    static DefinitionLoader.LoadResult loadPack(Path dir) {
        try (MultiPackResourceManager rm = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources("wr_test", true, dir)))) {
            return DefinitionLoader.assemble(Definitions.SPECIES.read(rm), Definitions.CULTIVARS.read(rm));
        }
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void typeErrorSkipsSpeciesAndDependents(GameTestHelper helper) {
        DefinitionLoader.LoadResult r = loadPack(testPack("wr125_bad"));
        List<String> lines = r.lines().stream().map(DefinitionLoader.LogLine::text).toList();
        helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith(
                "wildroots:crop_species/gt_bad.json: $.quantitative.yield.loci: ")), "no path line: " + lines);
        helper.assertTrue(lines.contains(
                "wildroots:cultivars/gt_dependent.json: skipped: depends on rejected species wildroots:gt_bad"),
                "no line for the dependent cultivar: " + lines);
        helper.assertTrue(r.snapshot().species("wildroots:gt_good").isPresent(), "the good species was dropped");
        helper.assertTrue(r.snapshot().species("wildroots:gt_bad").isEmpty(), "the broken species was loaded");
        helper.assertTrue(r.snapshot().cultivar("wildroots:gt_good_landrace").isPresent(), "good cultivar dropped");
        helper.assertTrue("Wild Roots: species 1 (1 skipped), cultivars 1 (1 skipped)".equals(r.summary()),
                "summary: " + r.summary());
        helper.assertTrue(Definitions.current().stats().summary().startsWith("Wild Roots: species "),
                "the live load has no summary");
        helper.succeed();
    }

    static final String PACK = "wr125_threshold";
    static final String CULTIVAR = "wrtest:test_cultivar";

    static String cultivarJson(double min) {
        return """
                {
                  "id": "wrtest:test_cultivar",
                  "species": "wrtest:test_plant",
                  "tier": 1,
                  "requires": { "quantitative": { "fruit_size": { "min": %s } } },
                  "hysteresis": 0.05,
                  "produce": { "quality_axes": { "fruit_size": 0.5, "flavor": 0.5 } }
                }
                """.formatted(Double.toString(min));
    }

    static void writePack(Path root, double min) throws IOException {
        Path data = root.resolve("data").resolve("wrtest");
        Files.createDirectories(data.resolve("crop_species"));
        Files.createDirectories(data.resolve("cultivars"));
        Files.writeString(root.resolve("pack.mcmeta"),
                "{ \"pack\": { \"description\": \"Wild Roots GameTest\", \"pack_format\": 15 } }");
        try (InputStream in = Files.newInputStream(ModList.get().getModFileById(WildRoots.MOD_ID).getFile()
                .findResource("wr_testpacks", "wr125_reload_species.json"))) {
            Files.write(data.resolve("crop_species").resolve("test_plant.json"), in.readAllBytes());
        }
        Files.writeString(data.resolve("cultivars").resolve("test_cultivar.json"), cultivarJson(min),
                StandardCharsets.UTF_8);
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Status of a fixed wild genome under the current snapshot. */
    static Optional<String> statusOfProbe() {
        DefinitionsSnapshot s = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = s.species("wrtest:test_plant");
        if (e.isEmpty()) {
            return Optional.of("<no species>");
        }
        SpeciesDef def = e.get().def();
        Genome g = s.genetics().wildPlant(def, new PatchPassport(def.id(), "test", false, 0L, false, 42L), 7L)
                .orElseThrow();
        Classification c = s.genetics().classify(g, def, s.profiles(def.id()), Optional.empty()).orElseThrow();
        return c.cultivar().map(Object::toString);
    }

    /**
     * {@code /reload} after editing a cultivar threshold applies it at the next
     * check: the snapshot is swapped, nothing in the world is scanned.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr125_reload", timeoutTicks = 1_000_000)
    public static void reloadAppliesNewThreshold(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        PackRepository repo = server.getPackRepository();
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        AtomicReference<Double> minA = new AtomicReference<>();
        AtomicReference<Double> minB = new AtomicReference<>();
        AtomicReference<Optional<String>> statusA = new AtomicReference<>();
        AtomicReference<Optional<String>> statusB = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CompletableFuture<Void> chain;
        try {
            deleteTree(root);
            writePack(root, 0.0);
            repo.reload();
            List<String> with = new ArrayList<>(before);
            with.add("file/" + PACK);
            chain = server.reloadResources(with).thenCompose(v -> {
                minA.set(threshold());
                statusA.set(statusOfProbe());
                try {
                    writePack(root, 1.0);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return server.reloadResources(with); // what /reload does with the same pack list
            }).thenCompose(v -> {
                minB.set(threshold());
                statusB.set(statusOfProbe());
                return server.reloadResources(before);
            }).whenComplete((v, t) -> {
                if (t != null) {
                    failure.set(t);
                }
                try {
                    deleteTree(root);
                    repo.reload();
                } catch (IOException e) {
                    failure.compareAndSet(null, e);
                }
            });
        } catch (IOException e) {
            helper.fail("cannot write the test data pack: " + e);
            return;
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(chain.isDone(), "reloading");
            helper.assertTrue(failure.get() == null, "reload chain failed: " + failure.get());
            helper.assertTrue(Double.valueOf(0.0).equals(minA.get()), "first threshold " + minA.get());
            helper.assertTrue(Double.valueOf(1.0).equals(minB.get()), "threshold after /reload " + minB.get());
            helper.assertTrue(Optional.of(CULTIVAR).equals(statusA.get()), "probe before: " + statusA.get());
            helper.assertTrue(Optional.empty().equals(statusB.get()), "probe after /reload: " + statusB.get());
            helper.assertTrue(Definitions.current().species("wrtest:test_plant").isEmpty(), "test pack not removed");
        });
    }

    private static Double threshold() {
        return Definitions.current().cultivar(CULTIVAR)
                .map(c -> c.def().quantitative().get(TraitId.of("fruit_size")).min()).orElse(null);
    }
}
