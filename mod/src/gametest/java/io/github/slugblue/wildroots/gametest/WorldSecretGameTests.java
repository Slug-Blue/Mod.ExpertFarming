package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-123, SEC-25: the world secret exists from the first overworld load, survives a reload from disk, stays out of logs. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldSecretGameTests {

    private WorldSecretGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void secretIsCreatedOnOverworldLoadAndPersists(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Optional<Long> stored = WorldSecret.peek(server);
        helper.assertTrue(stored.isPresent(), "wildroots_secret was not created when the overworld loaded");
        long secret = stored.get();
        helper.assertTrue(WorldSecret.get(server) == secret, "second access changed the secret");

        // "Restart": write the overworld data to disk and read it back with a fresh storage.
        server.overworld().getDataStorage().save();
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        DimensionDataStorage fresh = new DimensionDataStorage(data.toFile(), server.getFixerUpper());
        WorldSecret reloaded = fresh.get(WorldSecret::load, WorldSecret.NAME);
        helper.assertTrue(reloaded != null, "wildroots_secret.dat was not written");
        long reread = reloaded.save(new net.minecraft.nbt.CompoundTag()).getLong("s");
        helper.assertTrue(reread == secret, "the secret read back from disk differs");
        helper.assertTrue(!reloaded.toString().contains(Long.toString(secret)), "toString leaks the secret");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void secretNeverAppearsInLogs(GameTestHelper helper) {
        long secret = WorldSecret.get(helper.getLevel().getServer());
        List<String> needles = List.of(Long.toString(secret), Long.toUnsignedString(secret),
                Long.toHexString(secret), Long.toHexString(secret).toUpperCase());
        for (String file : List.of("logs/latest.log", "logs/debug.log")) {
            Path p = Path.of(file);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            String log;
            try {
                log = Files.readString(p, StandardCharsets.ISO_8859_1);
            } catch (IOException e) {
                helper.fail("cannot read " + file + ": " + e.getMessage());
                return;
            }
            for (String n : needles) {
                // the message never prints the value itself
                helper.assertTrue(!log.contains(n), "the world secret appears in " + file);
            }
        }
        helper.succeed();
    }
}
