package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.CultivarInfo;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * {@code wildroots:mother_root} (GDD §5.2; narrative §7.4): the root of a
 * biennial harvested at ripeness, with the whole genome and noise of its
 * plant. Does not stack. Edible by the grade table of GDD §6.4 from its
 * plant's phenotype; planted on farmland it flowers within
 * {@code seed_stage_min} and gives a seed lot, genome and noise unchanged.
 */
public class MotherItem extends Item {

    static final CultivarInfo.FoodReference DEFAULT_FOOD = new CultivarInfo.FoodReference(3, 0.6);

    public MotherItem(Properties properties) {
        super(properties.stacksTo(1).food(new FoodProperties.Builder().nutrition(DEFAULT_FOOD.nutrition())
                .saturationMod((float) DEFAULT_FOOD.saturation()).build()));
    }

    /** The mother root of a live plant with the grade of its phenotype. */
    public static ItemStack of(PlantBlockEntity.Live live, int grade, boolean bitter) {
        PlantState d = live.data();
        MotherNbt.Mother m = new MotherNbt.Mother(live.genetics().encode(live.genome()), live.noise().bytes(),
                Optional.ofNullable(d.cultivar()), d.tier(), Optional.ofNullable(d.lineName()), d.generation(),
                d.lotFlags() & LotFlags.INHERITED, grade, bitter);
        ItemStack s = new ItemStack(ModItems.MOTHER_ROOT.get());
        s.getOrCreateTag().put(MotherNbt.KEY, MotherNbt.write(m));
        return s;
    }

    public static Optional<MotherNbt.Mother> read(ItemStack stack) {
        return stack.getTag() == null ? Optional.empty() : MotherNbt.read(stack.getTag().get(MotherNbt.KEY));
    }

    @Override
    @EntryPoint
    public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        return Guarded.call("mother food", "mother", () -> read(stack).map(m -> {
            DefinitionsSnapshot snap = Definitions.current();
            CultivarInfo.FoodReference ref = m.cultivar().flatMap(snap::cultivar)
                    .flatMap(c -> c.info().foodReference()).orElse(DEFAULT_FOOD);
            boolean toxic = snap.species(m.species()).map(e -> e.def().qual(Traits.BITTERNESS))
                    .map(QualTrait::toxic).orElse(false);
            return GradedFood.of(ref, m.grade(), m.bitter(), toxic);
        }).orElse(super.getFoodProperties(stack, entity)), super.getFoodProperties(stack, entity));
    }

    /** Planting on the top of farmland: the plant starts at harvest ripeness as a flowering mother. */
    @Override
    @EntryPoint
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().above();
        return Guarded.call("mother planting", pos, () -> {
            ItemStack stack = ctx.getItemInHand();
            Optional<MotherNbt.Mother> m = read(stack);
            Player p = ctx.getPlayer();
            if (m.isEmpty() || !PlantService.canPlantAt(level, pos)) {
                return InteractionResult.PASS;
            }
            if (!PlantService.plantMother(level, pos, m.get(), p == null ? null : p.getUUID())) {
                return InteractionResult.FAIL;
            }
            if (p == null || !p.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }, InteractionResult.FAIL);
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("mother tooltip", "mother", () -> {
            Optional<MotherNbt.Mother> m = read(stack);
            if (m.isEmpty()) {
                tooltip.add(Component.translatable("wildroots.tooltip.lot.damaged"));
                return;
            }
            tooltip.add(Component.translatable(m.get().cultivar().map(c -> LotStacks.nameKey("cultivar", c))
                    .orElse(LotStacks.nameKey("species", m.get().species()))));
            tooltip.add(Component.translatable("wildroots.grade." + m.get().grade()));
            m.get().lineName().ifPresent(n -> tooltip.add(Component.translatable("wildroots.tooltip.lot.line_name",
                    Component.literal(n))));
        });
    }
}
