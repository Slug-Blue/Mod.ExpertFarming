package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.line.LineContent;
import io.github.slugblue.wildroots.data.CultivarInfo;
import io.github.slugblue.wildroots.item.FieldJournalItem;
import io.github.slugblue.wildroots.item.HandLensItem;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.item.SeedLotItem;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Items (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModItems {

    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, WildRoots.MOD_ID);

    /** {@code wildroots:seed_lot}: seeds of one mother plant, every species (WR-135; GDD §5.3). Core content. */
    public static final RegistryObject<SeedLotItem> SEED_LOT = REGISTER.register("seed_lot",
            () -> new SeedLotItem(new Item.Properties()));

    /** {@code wildroots:seed_pouch}: 16 seeds, LIFO, like a bundle (WR-138; GDD §5.3, ADR-0007). Core content. */
    public static final RegistryObject<SeedPouchItem> SEED_POUCH = REGISTER.register("seed_pouch",
            () -> new SeedPouchItem(new Item.Properties()));

    /** {@code wildroots:field_journal}: a window onto the journal capability (WR-180; narrative §4.1). Core content. */
    public static final RegistryObject<FieldJournalItem> FIELD_JOURNAL = REGISTER.register("field_journal",
            () -> new FieldJournalItem(new Item.Properties()));

    /** {@code wildroots:hand_lens}: tier 1 (WR-183; GDD §10). Core content. */
    public static final RegistryObject<HandLensItem> HAND_LENS = REGISTER.register("hand_lens",
            () -> new HandLensItem(new Item.Properties()));

    /** Carrot line (GDD §7.3, economy §2): food reference 3 / 0.6 like the vanilla carrot. */
    public static final String CARROT_LINE = "carrot";
    private static final CultivarInfo.FoodReference CARROT_FOOD = new CultivarInfo.FoodReference(3, 0.6);

    /** Harvest of the wild carrot (step 0, WR-136). */
    public static final RegistryObject<ProduceItem> WILD_CARROT_ROOT = REGISTER.register("wild_carrot_root",
            () -> new ProduceItem(new Item.Properties(), CARROT_FOOD, false));
    /** Harvest of the landrace {@code eastern_carrot} (step 1). */
    public static final RegistryObject<ProduceItem> EASTERN_CARROT = REGISTER.register("eastern_carrot",
            () -> new ProduceItem(new Item.Properties(), CARROT_FOOD, false, true));
    /** Harvest of the cultivar {@code wildroots:carrot} (step 2). */
    public static final RegistryObject<ProduceItem> CARROT = REGISTER.register("carrot",
            () -> new ProduceItem(new Item.Properties(), CARROT_FOOD, false));

    /** Mother root of carrot (and later beet), WR-137; GDD §5.2, narrative §7.4. */
    public static final RegistryObject<MotherItem> MOTHER_ROOT = REGISTER.register("mother_root",
            () -> new MotherItem(new Item.Properties()));

    static {
        LineContent.assign(MOTHER_ROOT, CARROT_LINE);
        LineContent.assign(WILD_CARROT_ROOT, CARROT_LINE);
        LineContent.assign(EASTERN_CARROT, CARROT_LINE);
        LineContent.assign(CARROT, CARROT_LINE);
    }

    private ModItems() {
    }
}
