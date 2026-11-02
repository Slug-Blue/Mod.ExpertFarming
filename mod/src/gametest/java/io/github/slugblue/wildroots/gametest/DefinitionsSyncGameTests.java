package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.sync.DefinitionsSync;
import io.github.slugblue.wildroots.data.sync.DefinitionsSyncPacket;
import io.github.slugblue.wildroots.data.sync.SyncAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-128: the sync goes to a joining player and to everybody after /reload, and decodes. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefinitionsSyncGameTests {

    private DefinitionsSyncGameTests() {
    }

    record Sent(ServerPlayer to, DefinitionsSyncPacket packet) {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr128_reload", timeoutTicks = 1_000_000)
    public static void syncOnJoinAndAfterReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Sent> sent = new ArrayList<>();
        BiConsumer<ServerPlayer, DefinitionsSyncPacket> old = DefinitionsSync.setSender((p, m) -> sent.add(new Sent(p, m)));
        ServerPlayer joining = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "wr128"));
        // PlayerList#placeNewPlayer posts this event for a joining player (Forge patch)
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(), joining));
        int onJoin = sent.size();
        boolean toJoining = !sent.isEmpty() && sent.stream().allMatch(s -> s.to() == joining);
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "reloading");
            DefinitionsSync.setSender(old);
            helper.assertTrue(!reload.isCompletedExceptionally(), "reload failed");
            helper.assertTrue(onJoin >= 1 && toJoining, "nothing sent to the joining player: " + onJoin);
            List<Sent> afterReload = sent.subList(onJoin, sent.size());
            helper.assertTrue(!afterReload.isEmpty() && afterReload.stream().allMatch(s -> s.to() == null),
                    "nothing broadcast after /reload: " + afterReload.size());
            SyncAssembler assembler = new SyncAssembler();
            SyncAssembler.Step step = null;
            for (Sent s : afterReload) {
                step = assembler.accept(s.packet());
            }
            helper.assertTrue(step instanceof SyncAssembler.Done done
                    && done.definitions().species().size() == Definitions.current().species().size(),
                    "the broadcast sync does not decode to the current definitions: " + step);
        });
    }
}
