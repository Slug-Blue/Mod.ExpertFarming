package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * What a plant drops when its block goes (docs/05-architecture.md §10.2,
 * §6.3; GDD §5.1–5.3, §6.4, §14). Drops are computed on the server from the
 * block entity, before the block becomes air ({@code Level#destroyBlock},
 * S3); an inert or sleeping plant drops nothing, so its data is never
 * copied (§6.1).
 *
 * <ul>
 *   <li>stage 5 (seeds ripe): one seed lot with the fathers and
 *   {@code p_out} of the pending lot, {@code (3 + 9·P_yield)·seed_set}
 *   seeds rounded at random;</li>
 *   <li>stages 3–4: the mother root with genome, noise and the grade of the
 *   phenotype (GDD §5.2).</li>
 * </ul>
 */
public final class HarvestService {

    private HarvestService() {
    }

    /** {@code getDrops} of a plant block. */
    public static List<ItemStack> drops(BlockState state, LootParams.Builder params) {
        BlockEntity raw = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ServerLevel level = params.getLevel();
        if (!(raw instanceof PlantBlockEntity be)) {
            return List.of();
        }
        Optional<PlantBlockEntity.Live> live = be.live(level);
        if (live.isEmpty()) {
            return List.of();
        }
        BlockPos pos = be.getBlockPos();
        Environment env = GrowthLogic.environment(level, pos);
        Optional<Phenotype> p = GrowthLogic.phenotype(live.get(), env);
        if (p.isEmpty()) {
            return List.of();
        }
        int stage = FieldPlantBlock.stage(state);
        List<ItemStack> out = new ArrayList<>(3);
        if (stage >= FieldPlantBlock.SEED_STAGE) {
            seeds(level, be, live.get(), p.get(), level.random).ifPresent(out::add);
        } else if (stage >= FieldPlantBlock.HARVEST_STAGE) {
            out.add(MotherItem.of(live.get(), grade(live.get(), p.get()), p.get().bitter()));
        }
        return out;
    }

    /** Grade 1–4 by the plant's cultivar axes, by the line's axes for wild plants (GDD §6.4). */
    public static int grade(PlantBlockEntity.Live live, Phenotype p) {
        GeneticsService gs = live.genetics();
        String cv = live.data().cultivar();
        return Optional.ofNullable(cv).flatMap(live.snapshot()::cultivar)
                .map(c -> gs.grade(p, c.def()))
                .orElseGet(() -> gs.grade(p, live.def()));
    }

    /** Probabilistic rounding: {@code floor(x)} plus one with chance {@code frac(x)}. */
    public static int roundProb(double x, RandomSource random) {
        double e = Double.isFinite(x) ? Math.max(0, x) : 0;
        int base = (int) Math.floor(e);
        return base + (random.nextDouble() < e - base ? 1 : 0);
    }

    /** The seed lot of a plant at stage 5, with or without the fathers of its pending lot. */
    static Optional<ItemStack> seeds(ServerLevel level, PlantBlockEntity be, PlantBlockEntity.Live live, Phenotype p,
                                     RandomSource random) {
        return seeds(level, be, live, p, random, true);
    }

    static Optional<ItemStack> seeds(ServerLevel level, PlantBlockEntity be, PlantBlockEntity.Live live, Phenotype p,
                                     RandomSource random, boolean withFathers) {
        PendingLot pending = withFathers ? live.data().pending() : null;
        List<LotFathersNbt.Father> fathers = pending == null ? List.of() : pending.fathers();
        int donors = (int) fathers.stream().filter(LotFathersNbt.Father::donor).count();
        Brush brush = pending == null ? Brush.NONE : pending.brush();
        boolean bees = pending != null && pending.bees();
        FatherSelector.Selection sel = new FatherSelector.Selection(List.of(), 0, fathers.size() - donors, donors);
        double expected = live.genetics().expectedSeeds(live.def(), p, sel, bees || brush != Brush.NONE, brush);
        int count = roundProb(expected, random);
        if (count <= 0) {
            return Optional.empty();
        }
        int flags = (bees ? LotFlags.BEES : 0) | (brush != Brush.NONE ? LotFlags.BRUSH : 0);
        return Optional.of(LotStacks.seeds(level, live, fathers, pending == null ? 0 : pending.pOut(), flags, be,
                count));
    }
}
