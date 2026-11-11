package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.TreeSet;
import javax.management.ObjectName;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WR-198 (docs/05-architecture.md §15, {@code 00-vision.md} metric 5): on the
 * dedicated server ({@code runGameTestServer}, as in CI) no class of the
 * mod's {@code client} package and no vanilla client or render class
 * ({@code net.minecraft.client}, {@code com.mojang.blaze3d}) is loaded.
 *
 * <p>Checked twice: by the GameTest in its own batch {@code wr198} (it sees
 * whatever the batches before it loaded; batch order is the hash order of
 * the names, {@code WILDROOTS_ORDER_SALT} moves it) and once more when the
 * GameTest server stops, after every batch ({@link #onStopping}). The stop
 * check cannot fail a test any more, so on a finding it logs the classes
 * and ends the JVM with exit code 1: {@code runGameTestServer} and CI go red.
 *
 * <p>The list is the JVM's own ({@code VM.class_hierarchy} through the
 * {@code DiagnosticCommand} MBean), not a class loader we would have to
 * open. Limitation: the hierarchy lists classes and the interfaces loaded
 * classes implement; an interface no loaded class implements is not listed.
 * ArchUnit {@code modClientOnlyFromLambdas} keeps the code shape; this test
 * checks the running server.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID)
public final class DedicatedServerGameTests {

    static final Logger LOG = LoggerFactory.getLogger(DedicatedServerGameTests.class);
    static final String MOD_CLIENT = "io.github.slugblue.wildroots.client.";

    private DedicatedServerGameTests() {
    }

    /** Names of every class the JVM has loaded so far. */
    static Set<String> loadedClasses() {
        try {
            String out = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"), "vmClassHierarchy",
                    new Object[] {new String[0]}, new String[] {String[].class.getName()});
            Set<String> names = new TreeSet<>();
            for (String line : out.split("\\R")) {
                // "|  |--io.github.Foo/0x0000…" or "java.lang.Object/null"
                int start = 0;
                while (start < line.length() && (line.charAt(start) == '|' || line.charAt(start) == '-'
                        || line.charAt(start) == ' ')) {
                    start++;
                }
                int end = line.indexOf('/', start);
                if (end > start) {
                    names.add(line.substring(start, end).replace('/', '.'));
                }
            }
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("cannot list the loaded classes", e);
        }
    }

    /** Client classes loaded so far (the mod's and vanilla's); empty is the expected result. */
    static Set<String> clientClassesLoaded(Set<String> loaded) {
        Set<String> found = new TreeSet<>();
        for (String n : loaded) {
            if (n.startsWith(MOD_CLIENT) || n.startsWith("net.minecraft.client.") || n.startsWith("com.mojang.blaze3d.")) {
                found.add(n);
            }
        }
        return found;
    }

    /** The list must see the mod's common classes, or an empty result means nothing. */
    static boolean listSeesTheMod(Set<String> loaded) {
        return loaded.contains("io.github.slugblue.wildroots.journal.Journals")
                && loaded.contains("io.github.slugblue.wildroots.network.ServerPacketGuard");
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr198")
    public static void dedicatedServerLoadsNoClientClass(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            // GameTests in runClient: client classes are loaded there by design
            LOG.info("[WR-198] not a dedicated server ({}), client class check skipped", FMLEnvironment.dist);
            helper.succeed();
            return;
        }
        Set<String> loaded = loadedClasses();
        helper.assertTrue(listSeesTheMod(loaded),
                "the loaded-class list misses the mod's common classes (" + loaded.size() + " names)");
        Set<String> found = clientClassesLoaded(loaded);
        LOG.info("[WR-198] dedicated server: {} classes loaded, {} client classes", loaded.size(), found.size());
        helper.assertTrue(found.isEmpty(), "client classes loaded on the dedicated server: " + found);
        helper.succeed();
    }

    /** Forge bus: the same check after the last batch, on the GameTest server only. */
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || !(event.getServer() instanceof GameTestServer)) {
            return;
        }
        Set<String> loaded = loadedClasses();
        Set<String> found = clientClassesLoaded(loaded);
        if (listSeesTheMod(loaded) && found.isEmpty()) {
            LOG.info("[WR-198] end of run: {} classes loaded, no client class", loaded.size());
            return;
        }
        LOG.error("[WR-198] end of run: client classes loaded on the dedicated server (or the list is broken, {} names): {}",
                loaded.size(), found);
        org.apache.logging.log4j.LogManager.shutdown();
        // System.exit would wait for the server shutdown hook, which waits for this thread
        Runtime.getRuntime().halt(1);
    }
}
