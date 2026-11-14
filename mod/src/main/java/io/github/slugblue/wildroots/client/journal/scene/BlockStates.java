package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Block states of a scene from the block registry (journal v2 §5.8):
 * {@code BlockStateParser} without NBT, then the scene rules.
 *
 * <ul>
 *   <li>unknown block or property, or any parse error → air, one log line;</li>
 *   <li>a block with a block entity ({@code EntityBlock}) → air, except the
 *       blocks a scene is about whose look is their block model alone: the
 *       mod's plants and the bee hive and nest (§3.6 names
 *       {@code minecraft:beehive}). The scene world never has a block
 *       entity ({@code getBlockEntity} is {@code null}), so none is
 *       created or ticked;</li>
 *   <li>a plant keeps only {@code stage}, {@code size}, {@code variant} and
 *       {@code ailing} (§3.3); {@code village} and {@code active} stay at
 *       their defaults;</li>
 *   <li>a fluid: a waterloggable block is drained; any other fluid → air.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class BlockStates implements StateResolver<BlockState> {

    /** {@code EntityBlock}s a scene may show: their block entity carries no look. */
    static final Set<Block> ENTITY_BLOCKS_ALLOWED = Set.of(Blocks.BEEHIVE, Blocks.BEE_NEST);
    /** The plant properties a scene may set (§3.3). */
    static final Set<String> PLANT_PROPERTIES = Set.of("stage", "size", "variant", "ailing");

    private final HolderLookup<Block> blocks = BuiltInRegistries.BLOCK.asLookup();

    @Override
    public BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState resolve(String state, String where, List<String> log) {
        BlockStateParser.BlockResult r;
        try {
            r = BlockStateParser.parseForBlock(blocks, state, false);
        } catch (Exception e) {
            log.add(where + ": \"" + state + "\": " + SceneParser.message(e) + ", air instead");
            return air();
        }
        BlockState s = r.blockState();
        Block b = s.getBlock();
        if (b instanceof FieldPlantBlock) {
            BlockState clean = b.defaultBlockState();
            for (Map.Entry<Property<?>, Comparable<?>> e : r.properties().entrySet()) {
                if (PLANT_PROPERTIES.contains(e.getKey().getName())) {
                    clean = with(clean, e.getKey(), e.getValue());
                }
            }
            return clean;
        }
        if (b instanceof EntityBlock && !ENTITY_BLOCKS_ALLOWED.contains(b)) {
            log.add(where + ": " + state + " has a block entity, air instead");
            return air();
        }
        if (!s.getFluidState().isEmpty()) {
            if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return s.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            log.add(where + ": " + state + " is a fluid, air instead");
            return air();
        }
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState s, Property p, Comparable v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }
}
