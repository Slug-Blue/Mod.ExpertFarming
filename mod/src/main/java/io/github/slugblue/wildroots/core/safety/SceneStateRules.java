package io.github.slugblue.wildroots.core.safety;

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

/**
 * The block-state rules of a journal scene (journal v2 §5.8; WR-181k,
 * AppSec-reviewed): a scene file may come from a server resource pack, so
 * its states are parsed from the block registry without NBT and then
 * cleaned. Plain game code with no client class, so a GameTest can check
 * the real rules over the real registry; the client's scene loader
 * ({@code client.journal.scene.BlockStates}) only delegates here.
 *
 * <ul>
 *   <li>unknown block or property, NBT, or any parse error → air, one log line;</li>
 *   <li>a block with a block entity ({@code EntityBlock}) → air, except the
 *       mod's plants and the bee hive and nest, whose look is their model
 *       alone (the scene world never creates a block entity);</li>
 *   <li>a plant keeps only {@code stage}, {@code size}, {@code variant} and
 *       {@code ailing} (§3.3);</li>
 *   <li>a fluid: a waterloggable block is drained, any other fluid → air.</li>
 * </ul>
 */
public final class SceneStateRules {

    /** {@code EntityBlock}s a scene may show. */
    public static final Set<Block> ENTITY_BLOCKS_ALLOWED = Set.of(Blocks.BEEHIVE, Blocks.BEE_NEST);
    /** The plant properties a scene may set (§3.3). */
    public static final Set<String> PLANT_PROPERTIES = Set.of("stage", "size", "variant", "ailing");

    private SceneStateRules() {
    }

    public static HolderLookup<Block> blocks() {
        return BuiltInRegistries.BLOCK.asLookup();
    }

    /** Never throws; anything refused is air and one line in {@code log}. */
    public static BlockState resolve(HolderLookup<Block> blocks, String state, String where, List<String> log) {
        BlockStateParser.BlockResult r;
        try {
            r = BlockStateParser.parseForBlock(blocks, state, false);
        } catch (Exception e) {
            log.add(where + ": \"" + state + "\": " + message(e) + ", air instead");
            return Blocks.AIR.defaultBlockState();
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
            return Blocks.AIR.defaultBlockState();
        }
        if (!s.getFluidState().isEmpty()) {
            if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return s.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            log.add(where + ": " + state + " is a fluid, air instead");
            return Blocks.AIR.defaultBlockState();
        }
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState s, Property p, Comparable v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }

    static String message(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        m = m.replaceAll("[\r\n\t]", " ");
        return m.length() > 160 ? m.substring(0, 160) + "…" : m;
    }
}
