package io.github.slugblue.wildroots.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.worldgen.ChunkBand;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-154: {@code /wildroots locate_patch} (worldgen §4.2; architecture §16; SEC-46). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LocatePatchGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;

    private LocatePatchGameTests() {
    }

    /** A command source that records what it is told. */
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

    static CommandSourceStack source(GameTestHelper helper, Recorder rec, int permission) {
        ServerLevel level = helper.getLevel();
        return new CommandSourceStack(rec, Vec3.atCenterOf(level.getSharedSpawnPos()), Vec2.ZERO, level, permission,
                "wr154", Component.literal("wr154"), level.getServer(), null);
    }

    static int run(GameTestHelper helper, CommandSourceStack src, String cmd) {
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(src, cmd);
    }

    /** The root node requires permission 2: a level-0 source does not see it. */
    @GameTest(template = FLOOR)
    public static void rootNeedsPermission2(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> d = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> root = d.getRoot().getChild("wildroots");
        helper.assertTrue(root != null, "no /wildroots node");
        Recorder rec = new Recorder();
        helper.assertFalse(root.canUse(source(helper, rec, 0)), "permission 0 sees /wildroots");
        helper.assertFalse(root.canUse(source(helper, rec, 1)), "permission 1 sees /wildroots");
        helper.assertTrue(root.canUse(source(helper, rec, 2)), "permission 2 does not see /wildroots");
        helper.assertTrue(run(helper, source(helper, rec, 0), "wildroots locate_patch wildroots:wild_carrot") == 0,
                "permission 0 ran the command");
        helper.succeed();
    }

    /** Arguments are validated: unknown species and a bad radius give a message, never an exception. */
    @GameTest(template = FLOOR)
    public static void badArgumentsGiveAMessage(GameTestHelper helper) {
        Recorder rec = new Recorder();
        CommandSourceStack src = source(helper, rec, 2);
        helper.assertTrue(run(helper, src, "wildroots locate_patch wildroots:no_such_species") == 0, "unknown species");
        helper.assertTrue(rec.lines.stream().anyMatch(l -> l.contains("wildroots:no_such_species")),
                "no message for the unknown species: " + rec.lines);
        rec.lines.clear();
        helper.assertTrue(run(helper, src, "wildroots locate_patch wildroots:wild_carrot 5") == 0, "radius 5");
        helper.assertTrue(run(helper, src, "wildroots locate_patch wildroots:wild_carrot 99999") == 0, "radius 99999");
        helper.assertTrue(run(helper, src, "wildroots locate_patch Not:Valid!") == 0, "bad id");
        helper.assertFalse(rec.lines.isEmpty(), "no message for bad arguments");
        helper.succeed();
    }

    /**
     * A known species: the nearest candidate chunk is found without
     * generating anything (the loaded chunk count does not change), and it
     * is a chunk of the carrot band.
     */
    @GameTest(template = FLOOR)
    public static void findsACandidateWithoutGeneration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recorder rec = new Recorder();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int r = run(helper, source(helper, rec, 2), "wildroots locate_patch wildroots:wild_carrot 4096");
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded, "the search loaded chunks");
        helper.assertTrue(!rec.lines.isEmpty(), "no answer");
        org.slf4j.LoggerFactory.getLogger(LocatePatchGameTests.class).info("WR-154 locate: {} -> {}", r, rec.lines);
        if (r > 0) {
            String line = rec.lines.get(rec.lines.size() - 1);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(-?\\d+), ~, (-?\\d+)\\]").matcher(line);
            helper.assertTrue(m.find(), "no coordinates in " + line);
            int x = Integer.parseInt(m.group(1));
            int z = Integer.parseInt(m.group(2));
            helper.assertTrue(ChunkBand.candidate(level.getSeed(), x >> 4, z >> 4, 0.0, 0.036),
                    "not a carrot band chunk: " + x + ", " + z);
        }
        helper.succeed();
    }
}
