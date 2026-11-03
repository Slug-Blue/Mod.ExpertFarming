package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-125 / WR-126, SEC-27 (TAE): a real {@code /reload} with a data pack of
 * crooked JSON next to a good definition: the reload completes, every bad file
 * is skipped, the good one is live, and removing the pack restores the
 * previous snapshot.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReloadCrookedJsonGameTests {

    static final String PACK = "wr126_crooked";

    private ReloadCrookedJsonGameTests() {
    }

    static final Map<String, String> CROOKED = Map.of(
            "garbage", "\u0000\u0001 not json {{{",
            "empty", "",
            "deep", "{\"id\": \"wrtest:deep\", \"x\": " + "[".repeat(40) + "]".repeat(40) + "}",
            "very_deep", "[".repeat(100_000) + "]".repeat(100_000),
            "not_object", "[1, 2, 3]",
            "nan", "{\"id\": \"wrtest:nan\", \"archetype\": NaN}",
            "trailing", "{\"id\": \"wrtest:trailing\"} }");

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr126_reload", timeoutTicks = 1_000_000)
    public static void reloadWithCrookedFilesKeepsTheGoodOnes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        PackRepository repo = server.getPackRepository();
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        int speciesBefore = Definitions.current().species().size();
        AtomicReference<DefinitionsSnapshot> during = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CompletableFuture<Void> chain;
        try {
            DefinitionsGameTests.deleteTree(root);
            DefinitionsGameTests.writePack(root, 0.0);
            Path species = root.resolve("data/wrtest/crop_species");
            for (Map.Entry<String, String> e : CROOKED.entrySet()) {
                Files.writeString(species.resolve(e.getKey() + ".json"), e.getValue());
            }
            repo.reload();
            List<String> with = new ArrayList<>(before);
            with.add("file/" + PACK);
            chain = server.reloadResources(with).thenCompose(v -> {
                during.set(Definitions.current());
                return server.reloadResources(before);
            }).whenComplete((v, t) -> {
                if (t != null) {
                    failure.set(t);
                }
                try {
                    DefinitionsGameTests.deleteTree(root);
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
            DefinitionsSnapshot s = during.get();
            helper.assertTrue(s.species("wrtest:test_plant").isPresent(), "the good species did not load");
            helper.assertTrue(s.cultivar(DefinitionsGameTests.CULTIVAR).isPresent(), "the good cultivar did not load");
            for (String bad : List.of("wrtest:deep", "wrtest:nan", "wrtest:trailing")) {
                helper.assertTrue(s.species(bad).isEmpty(), bad + " was loaded");
            }
            helper.assertTrue(s.stats().speciesSkipped() >= CROOKED.size(),
                    "skipped " + s.stats().speciesSkipped() + " < " + CROOKED.size());
            helper.assertTrue(s.species().size() == speciesBefore + 1, "species " + s.species().size());
            helper.assertTrue(Definitions.current().species("wrtest:test_plant").isEmpty(), "test pack not removed");
            helper.assertTrue(Definitions.current().species().size() == speciesBefore, "snapshot not restored");
        });
    }
}
