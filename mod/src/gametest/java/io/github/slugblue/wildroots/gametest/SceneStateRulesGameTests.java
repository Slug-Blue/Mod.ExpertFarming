package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.core.safety.SceneStateRules;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Review M4 (WR-181k, journal v2 §5.8): the real block-state rules of a
 * journal scene over the real block registry, mod blocks included. A
 * scene file may come from a server resource pack: block entities, NBT
 * and fluids never reach the scene, and a plant shows only its look.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SceneStateRulesGameTests {

    private SceneStateRulesGameTests() {
    }

    static BlockState resolve(String state, List<String> log) {
        return SceneStateRules.resolve(SceneStateRules.blocks(), state, "test", log);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void sceneStatesFollowTheRules(GameTestHelper helper) {
        List<String> log = new ArrayList<>();
        for (String s : List.of("minecraft:chest", "minecraft:spawner", "minecraft:water", "minecraft:lava",
                "minecraft:chest{Items:[]}", "minecraft:no_such_block", "minecraft:oak_stairs[nope=1]")) {
            log.clear();
            BlockState got = resolve(s, log);
            helper.assertTrue(got.isAir(), s + " gave " + got);
            helper.assertTrue(log.size() == 1, s + ": one log line, got " + log);
        }

        log.clear();
        BlockState stairs = resolve("minecraft:oak_stairs[waterlogged=true,facing=east]", log);
        helper.assertTrue(stairs.is(Blocks.OAK_STAIRS) && !stairs.getValue(BlockStateProperties.WATERLOGGED)
                && stairs.getValue(StairBlock.FACING) == net.minecraft.core.Direction.EAST && log.isEmpty(),
                "waterlogged stairs are drained, the rest kept: " + stairs + " " + log);

        BlockState hive = resolve("minecraft:beehive[facing=south]", log);
        helper.assertTrue(hive.is(Blocks.BEEHIVE) && hive.getValue(BlockStateProperties.HORIZONTAL_FACING)
                == net.minecraft.core.Direction.SOUTH && log.isEmpty(), "the hive is allowed: " + hive);
        helper.assertTrue(resolve("minecraft:bee_nest", log).is(Blocks.BEE_NEST), "the nest is allowed");

        BlockState plant = resolve("wildroots:wild_carrot[stage=3,size=2,variant=1,ailing=true,village=true,active=true]",
                log);
        BlockState clean = ModBlocks.WILD_CARROT.get().defaultBlockState();
        helper.assertTrue(plant.is(ModBlocks.WILD_CARROT.get()) && log.isEmpty(), "the plant stays: " + plant + log);
        helper.assertTrue(plant.getValue(FieldPlantBlock.STAGE) == 3 && plant.getValue(FieldPlantBlock.SIZE) == 2
                && plant.getValue(FieldPlantBlock.VARIANT) == 1 && plant.getValue(FieldPlantBlock.AILING),
                "the look is kept: " + plant);
        helper.assertTrue(plant.getValue(FieldPlantBlock.VILLAGE) == clean.getValue(FieldPlantBlock.VILLAGE)
                && plant.getValue(FieldPlantBlock.ACTIVE) == clean.getValue(FieldPlantBlock.ACTIVE),
                "village and active stay at their defaults: " + plant);

        helper.assertTrue(resolve("minecraft:farmland[moisture=7]", log).is(Blocks.FARMLAND), "plain blocks pass");
        helper.succeed();
    }
}
