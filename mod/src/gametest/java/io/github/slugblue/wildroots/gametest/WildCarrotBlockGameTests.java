package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-130: the {@code wild_carrot} block of the {@code field} archetype (docs/05-architecture.md §7, ADR-0004). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildCarrotBlockGameTests {

    private WildCarrotBlockGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void wildCarrotHas768StatesAndNoAge(GameTestHelper helper) {
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        helper.assertTrue(block instanceof CropBlock, "field archetype must extend CropBlock (ADR-0010)");
        int states = block.getStateDefinition().getPossibleStates().size();
        helper.assertTrue(states == 768, "states: " + states);
        helper.assertTrue(!block.getStateDefinition().getProperties().contains(CropBlock.AGE), "vanilla AGE is in the state");
        for (Property<?> p : new Property<?>[] {FieldPlantBlock.STAGE, FieldPlantBlock.SIZE, FieldPlantBlock.VARIANT,
                FieldPlantBlock.AILING, FieldPlantBlock.VILLAGE, FieldPlantBlock.ACTIVE}) {
            helper.assertTrue(block.getStateDefinition().getProperties().contains(p), "missing " + p.getName());
        }
        helper.assertTrue(block.species().equals("wildroots:wild_carrot"), "species " + block.species());
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void randomTicksOnlyWhenActiveAndNotFinal(GameTestHelper helper) {
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            boolean expected = s.getValue(FieldPlantBlock.ACTIVE) && s.getValue(FieldPlantBlock.STAGE) < 5;
            helper.assertTrue(block.isRandomlyTicking(s) == expected, "isRandomlyTicking " + s);
            helper.assertTrue(s.isRandomlyTicking() == expected, "state flag " + s);
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void noTickerAndFarmerView(GameTestHelper helper) {
        FieldPlantBlock block = ModBlocks.WILD_CARROT.get();
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            helper.assertTrue(block.getTicker(helper.getLevel(), s, BlockEntityType.CHEST) == null, "ticker " + s);
            int stage = s.getValue(FieldPlantBlock.STAGE);
            boolean ripeVillage = s.getValue(FieldPlantBlock.VILLAGE) && stage >= FieldPlantBlock.HARVEST_STAGE;
            helper.assertTrue(block.isMaxAge(s) == ripeVillage, "isMaxAge " + s);
        }
        // getStateForAge keeps the block's defaults (S3: vanilla rebuilds from defaultBlockState)
        BlockState s3 = block.getStateForAge(3);
        helper.assertTrue(s3.getValue(FieldPlantBlock.STAGE) == 3 && s3.getValue(FieldPlantBlock.ACTIVE), "stateForAge");
        // bone meal never on flowering or on the final stage
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(!block.isValidBonemealTarget(helper.getLevel(), pos, block.getStateForAge(4), false), "bonemeal 4");
        helper.assertTrue(!block.isValidBonemealTarget(helper.getLevel(), pos, block.getStateForAge(5), false), "bonemeal 5");
        helper.assertTrue(block.isValidBonemealTarget(helper.getLevel(), pos, block.getStateForAge(2), false), "bonemeal 2");
        helper.succeed();
    }
}
