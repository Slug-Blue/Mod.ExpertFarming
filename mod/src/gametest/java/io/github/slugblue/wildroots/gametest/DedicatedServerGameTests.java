package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.TreeSet;
import javax.management.ObjectName;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WR-198 (docs/05-architecture.md §15, {@code 00-vision.md} metric 5): on the
 * dedicated server ({@code runGameTestServer}, as in CI) no class of the
 * mod's {@code client} package is loaded — at server start and after every
 * batch that ran before this one (its own batch {@code wr198}; batch order
 * follows the hash order of the names, {@code WILDROOTS_ORDER_SALT} moves
 * it). A client class loaded on a real dedicated server crashes it (§15), so
 * every run of the suite that passes is the strong check; this test names
 * the class instead of a crash, also where the joined dev jar would let it
 * load. Read from
 * the JVM's own list of loaded classes ({@code VM.class_hierarchy} through
 * the {@code DiagnosticCommand} MBean), not from a class loader we would
 * have to open. ArchUnit {@code modClientOnlyFromLambdas} keeps the code
 * shape that makes this true; this test checks the running server.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
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

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr198")
    public static void dedicatedServerLoadsNoModClientClass(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            // GameTests in runClient: the client package is loaded there by design
            LOG.info("[WR-198] not a dedicated server ({}), client class check skipped", FMLEnvironment.dist);
            helper.succeed();
            return;
        }
        Set<String> loaded = loadedClasses();
        helper.assertTrue(loaded.contains("io.github.slugblue.wildroots.journal.Journals")
                        && loaded.contains("io.github.slugblue.wildroots.network.ServerPacketGuard"),
                "the loaded-class list misses the mod's common classes (" + loaded.size() + " names)");
        Set<String> ours = new TreeSet<>();
        int vanillaClient = 0;
        for (String n : loaded) {
            if (n.startsWith(MOD_CLIENT)) {
                ours.add(n);
            } else if (n.startsWith("net.minecraft.client.") || n.startsWith("com.mojang.blaze3d.")) {
                vanillaClient++;
            }
        }
        LOG.info("[WR-198] dedicated server: {} classes loaded, {} of the mod's client package, {} vanilla client/blaze3d",
                loaded.size(), ours.size(), vanillaClient);
        helper.assertTrue(ours.isEmpty(), "client classes loaded on the dedicated server: " + ours);
        helper.succeed();
    }
}
