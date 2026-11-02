package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.CultivarManager;
import io.github.slugblue.wildroots.data.DefinitionLoader;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.data.OperatorNotice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import java.util.function.BiConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-126: 500 broken files through the reload listeners; one chat message to operators only. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RobustLoaderGameTests {

    private RobustLoaderGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void fiveHundredErrorsGiveAtMostTwoHundredLines(GameTestHelper helper) {
        Path dir;
        try {
            dir = Files.createTempDirectory("wr126-pack");
            Path species = Files.createDirectories(dir.resolve("data/wildroots/crop_species"));
            Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"x\",\"pack_format\":15}}");
            for (int i = 0; i < 500; i++) {
                Files.writeString(species.resolve("bad_" + i + ".json"), "{\"id\": \"wildroots:bad_" + i + "\"}");
            }
        } catch (IOException e) {
            helper.fail("cannot write the test pack: " + e);
            return;
        }
        DefinitionLoader.LoadResult r = DefinitionsGameTests.loadPack(dir);
        List<DefinitionLoader.LogLine> logged = CultivarManager.limited(r.lines());
        helper.assertTrue(r.lines().size() >= 500, "expected at least 500 problems, got " + r.lines().size());
        helper.assertTrue(logged.size() == 201, "logged " + logged.size() + " lines");
        helper.assertTrue(logged.get(200).text().equals("Wild Roots: ... and " + (r.lines().size() - 200)
                + " more problems"), "last line: " + logged.get(200).text());
        helper.assertTrue(r.snapshot().stats().speciesSkipped() == 500, "skipped " + r.snapshot().stats().speciesSkipped());
        helper.succeed();
    }

    /**
     * Players are built directly: {@code makeMockServerPlayerInLevel} fails in
     * Forge 47 (its mock connection has no channel when the player list sends
     * packets). Join is the sync event with that player, {@code /reload} is
     * the event without one over the online list.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void onlyOperatorsGetOneMessage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PlayerList list = server.getPlayerList();
        List<String> received = new ArrayList<>();
        BiConsumer<ServerPlayer, Component> old = OperatorNotice.setSink((p, c) -> received.add(p.getUUID() + ":" + c.getString()));
        DefinitionsSnapshot live = Definitions.current();
        // GameTestServer gives ops level 0 (getOperatorUserPermissionLevel), so the operator overrides the check
        ServerPlayer op = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "wr126_op")) {
            @Override
            public boolean hasPermissions(int level) {
                return level <= 4;
            }
        };
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "wr126_player"));
        try {
            helper.assertTrue(op.hasPermissions(2) && !player.hasPermissions(2), "op setup failed");

            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(list, op));
            helper.assertTrue(received.isEmpty() || live.stats().errors() > 0, "message without errors: " + received);
            received.clear();

            DefinitionsSnapshot.Stats bad = new DefinitionsSnapshot.Stats(3, 1, 2, 1, 4, 0);
            Definitions.publish(new DefinitionsSnapshot(Map.of(), Map.of(), bad));
            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(list, op)); // the operator joins
            helper.assertTrue(received.size() == 1, "operator join: " + received);
            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(list, player)); // a player joins
            helper.assertTrue(received.size() == 1, "plain player got a message: " + received);
            OperatorNotice.notify(List.of(op, player), null, bad); // /reload with both online
            long toOp = received.stream().filter(s -> s.startsWith(op.getUUID() + ":")).count();
            helper.assertTrue(received.size() == 2 && toOp == 2, "after /reload: " + received);
            helper.assertTrue(received.get(0).endsWith(OperatorNotice.message(bad).getString()), received.get(0));
        } finally {
            Definitions.publish(live);
            OperatorNotice.setSink(old);
        }
        helper.succeed();
    }
}
