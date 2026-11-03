package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.sync.DefinitionsSync;
import io.github.slugblue.wildroots.data.sync.DefinitionsSyncPacket;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-123, SEC-25 (TAE): the world secret never reaches a client — not in the
 * bytes of the definitions sync a joining player gets, not in the player's
 * own NBT — and {@code /reload} does not change it.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldSecretLeakGameTests {

    private WorldSecretLeakGameTests() {
    }

    static byte[] bytes(long v, ByteOrder order) {
        return ByteBuffer.allocate(8).order(order).putLong(v).array();
    }

    static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    static List<String> textForms(long secret) {
        return List.of(Long.toString(secret), Long.toUnsignedString(secret), Long.toHexString(secret));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void secretIsNotInTheSyncOrPlayerNbt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long secret = WorldSecret.get(server);
        List<DefinitionsSyncPacket> sent = new ArrayList<>();
        ServerPlayer joining = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "wr123_leak"));
        BiConsumer<ServerPlayer, DefinitionsSyncPacket> old = DefinitionsSync.setSender((p, m) -> sent.add(m));
        try {
            MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(), joining));
        } finally {
            DefinitionsSync.setSender(old);
        }
        helper.assertTrue(!sent.isEmpty(), "nothing was synced to the joining player");
        for (DefinitionsSyncPacket p : sent) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            p.encode(buf);
            byte[] wire = new byte[buf.readableBytes()];
            buf.readBytes(wire);
            helper.assertTrue(!contains(wire, bytes(secret, ByteOrder.BIG_ENDIAN))
                    && !contains(wire, bytes(secret, ByteOrder.LITTLE_ENDIAN)), "the secret is in a sync packet");
            String text = new String(wire, java.nio.charset.StandardCharsets.ISO_8859_1);
            for (String t : textForms(secret)) {
                helper.assertTrue(!text.contains(t), "the secret as text is in a sync packet");
            }
        }
        String nbt = joining.saveWithoutId(new CompoundTag()).toString();
        for (String t : textForms(secret)) {
            helper.assertTrue(!nbt.contains(t), "the secret is in the player NBT");
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr123_reload", timeoutTicks = 1_000_000)
    public static void reloadKeepsTheSecret(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long before = WorldSecret.get(server);
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "reloading");
            helper.assertTrue(!reload.isCompletedExceptionally(), "reload failed");
            helper.assertTrue(Definitions.current() != null, "no definitions after reload");
            helper.assertTrue(WorldSecret.get(server) == before, "/reload changed the world secret");
        });
    }
}
