package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.line.LineManifest;
import io.github.slugblue.wildroots.core.line.LinePackFinder;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** WR-124: line packs, the line_loaded condition, existing worlds (S1). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LineGameTests {

    static final String CARROT_PACK = LinePackFinder.packId("carrot");

    private LineGameTests() {
    }

    /** Collects command output. */
    static final class Recorder implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    static Recorder run(MinecraftServer server, String command) {
        Recorder r = new Recorder();
        CommandSourceStack source = new CommandSourceStack(r, Vec3.ZERO, Vec2.ZERO, server.overworld(), 4,
                "wildroots-gametest", Component.literal("wildroots-gametest"), server, null);
        server.getCommands().performPrefixedCommand(source, command);
        return r;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void carrotPackIsActiveRequiredAndOnTop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PackRepository repo = server.getPackRepository();
        List<String> order = repo.getSelectedPacks().stream().map(Pack::getId).toList();
        int line = order.indexOf(CARROT_PACK);
        helper.assertTrue(line >= 0, "line pack not selected: " + order);
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).startsWith("mod:")) {
                helper.assertTrue(i < line, "line pack is below " + order.get(i) + ": " + order);
            }
        }
        Pack pack = repo.getPack(CARROT_PACK);
        helper.assertTrue(pack != null && pack.isRequired(), "line pack is not required");

        Recorder list = run(server, "datapack list enabled");
        helper.assertTrue(String.join("\n", list.lines).contains(CARROT_PACK), "/datapack list misses it: " + list.lines);

        // What /datapack disable does to the selection: the required pack comes back.
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        List<String> without = new ArrayList<>(before);
        without.remove(CARROT_PACK);
        repo.setSelected(without);
        boolean stillSelected = repo.getSelectedIds().contains(CARROT_PACK);
        repo.setSelected(before);
        helper.assertTrue(stillSelected, "a player could deselect the line pack");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void disabledLineHasNoPack(GameTestHelper helper) {
        LineManifest manifest = LineManifest.fromClasspath();
        LineRegistry off = LineRegistry.build(manifest, List.of("carrot"), s -> { });
        Path dir = ModList.get().getModFileById(WildRoots.MOD_ID).getFile().findResource("resourcepacks", "lines", "carrot");
        helper.assertTrue(Files.isDirectory(dir), "carrot pack folder missing in the mod file");
        helper.assertTrue(LinePackFinder.packsFor(off, l -> dir).isEmpty(), "disabled line still gets a pack");
        LineRegistry on = LineRegistry.build(manifest, List.of(), s -> { });
        List<Pack> packs = LinePackFinder.packsFor(on, l -> dir);
        helper.assertTrue(packs.size() == 1 && packs.get(0).getId().equals(CARROT_PACK), "enabled line has no pack");
        helper.assertTrue(LineRegistry.get().isEnabled("carrot"), "carrot is disabled in this run");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void lineLoadedConditionInRecipesAndRegistries(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var recipes = server.getRecipeManager();
        helper.assertTrue(recipes.byKey(Ids.mod("gametest_line_loaded_yes")).isPresent(),
                "recipe with line_loaded(carrot) is missing");
        helper.assertTrue(recipes.byKey(Ids.mod("gametest_line_loaded_no")).isEmpty(),
                "recipe with line_loaded(nope) is loaded");
        var modifiers = server.registryAccess().registryOrThrow(ForgeRegistries.Keys.BIOME_MODIFIERS);
        helper.assertTrue(modifiers.containsKey(Ids.mod("gametest_line_loaded_yes")),
                "biome modifier with forge:conditions line_loaded(carrot) is missing");
        helper.assertTrue(!modifiers.containsKey(Ids.mod("gametest_line_loaded_no")),
                "biome modifier with forge:conditions line_loaded(nope) is loaded");
        helper.succeed();
    }

    /**
     * An existing world whose level.dat does not know the pack, or lists it
     * as disabled, gets it selected on top with the golden carrot override;
     * a mod installed after the pack sits above it (S1 s1ExistingWorldGetsRequiredPack).
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void existingWorldGetsTheLinePack(GameTestHelper helper) {
        try {
            check(helper, new DataPackConfig(List.of("vanilla", "mod:forge", "mod:wildroots"), List.of()), "unknown", true);
            check(helper, new DataPackConfig(List.of("vanilla", "mod:forge", "mod:wildroots"), List.of(CARROT_PACK)),
                    "disabled", true);
            check(helper, new DataPackConfig(List.of("vanilla", "mod:forge", CARROT_PACK), List.of()),
                    "mod-installed-later", false);
        } catch (Exception e) {
            helper.fail("exception: " + e);
            return;
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, DataPackConfig levelDat, String label, boolean lineWins)
            throws Exception {
        Path datapacks = Files.createTempDirectory("wr-wr124-datapacks");
        PackRepository repo = ServerPacksSource.createPackRepository(datapacks);
        WorldDataConfiguration result = MinecraftServer.configurePackRepository(repo, levelDat, false,
                FeatureFlags.DEFAULT_FLAGS);
        List<String> selected = repo.getSelectedPacks().stream().map(Pack::getId).toList();
        helper.assertTrue(result.dataPacks().getEnabled().contains(CARROT_PACK), label + ": not in the saved config");
        helper.assertTrue(selected.contains(CARROT_PACK), label + ": not selected " + selected);
        try (MultiPackResourceManager rm = new MultiPackResourceManager(PackType.SERVER_DATA, repo.openAllSelected())) {
            Resource r = rm.getResource(Ids.of("minecraft", "recipes/golden_carrot.json")).orElseThrow();
            if (lineWins) {
                helper.assertTrue(CARROT_PACK.equals(r.sourcePackId()),
                        label + ": golden_carrot comes from " + r.sourcePackId());
                helper.assertTrue(selected.indexOf(CARROT_PACK) == selected.size() - 1, label + ": not on top " + selected);
            } else {
                helper.assertTrue(selected.indexOf("mod:wildroots") > selected.indexOf(CARROT_PACK),
                        label + ": order from level.dat changed " + selected);
            }
        }
    }
}
