package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Server events that unlock journal entries (narrative §4.1, §4.3). Here:
 * {@code SpeciesDiscovered} by harvest, the {@code welcome} entry and the
 * journal handed out at the first harvest of a wild plant.
 */
public final class JournalTriggers {

    public static final String RECEIVED_KEY = "wildroots.message.journal.received";
    /** Found-at rows kept per species page (narrative §4.3). */
    public static final int MAX_FOUND_AT = 8;

    private JournalTriggers() {
    }

    /** Entry id of a species page: {@code species.wild_carrot}, {@code species.<ns>.<path>} for data packs. */
    public static String speciesEntry(String speciesId) {
        return entry("species", speciesId);
    }

    public static String cultivarEntry(String cultivarId) {
        return entry("cultivar", cultivarId);
    }

    static String entry(String kind, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return kind + ".unknown";
        }
        return kind + "." + (rl.getNamespace().equals("wildroots") ? rl.getPath()
                : rl.getNamespace() + "." + rl.getPath());
    }

    /** Translation key behind an entry id: {@code species.wild_carrot} → {@code wildroots.species.wild_carrot}. */
    public static String nameKey(String kind, String id) {
        return "wildroots." + entry(kind, id);
    }

    /**
     * Forge bus, lowest priority: a real (not fake) player breaks a mature
     * plant of the mod. {@code BreakEvent} fires before the block goes, so
     * the block entity is still there.
     */
    @EntryPoint
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getState().getBlock() instanceof FieldPlantBlock)) {
            return;
        }
        Guarded.run("journal harvest", event.getPos(), () -> harvested(level, event.getPos(), event.getState(), player));
    }

    /** The harvest step for {@code player}; public for GameTests. */
    public static void harvested(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (FieldPlantBlock.stage(state) < FieldPlantBlock.HARVEST_STAGE) {
            return;
        }
        Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        if (be.isEmpty() || be.get().status() == PlantBlockEntity.Status.INERT || be.get().data() == null) {
            return;
        }
        String species = be.get().data().species();
        Passport passport = be.get().data().passport();
        boolean wild = passport.origin() == Passport.Origin.WILD;
        Optional<JournalData> j = Journals.of(player);
        if (j.isEmpty()) {
            return;
        }
        if (wild) {
            giveJournal(player, j.get());
        }
        boolean first = !j.get().has(speciesEntry(species));
        discoverSpecies(player, species, wild ? Optional.of(foundAt(level, pos, BlockPos.of(passport.patchOrigin())))
                : Optional.empty());
        if (first && !j.get().has("hint.welcome")) {
            Journals.unlock(player, "hint.welcome", DispCodec.keyArg(nameKey("species", species)));
        }
        HintTriggers.harvested(level, pos, state, be.get(), player, j.get());
    }

    /** First wild harvest: the journal item (unless the config says no) and the "given" mark. */
    static void giveJournal(ServerPlayer player, JournalData j) {
        if (!j.markGiven() || !ServerConfig.GIVE_JOURNAL_ON_FIRST_HARVEST.get()) {
            return;
        }
        ItemStack book = new ItemStack(ModItems.FIELD_JOURNAL.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        if (player.connection != null) {
            player.sendSystemMessage(Component.translatable(RECEIVED_KEY));
        }
    }

    /** A found-at row: biome of the harvested plant (a loaded cell), x and z of its patch (narrative §4.3, §9.7). */
    static DispCodec.Line foundAt(ServerLevel level, BlockPos plant, BlockPos patch) {
        String biome = level.getBiome(plant).unwrapKey().map(ResourceKey::location)
                .map(rl -> "biome." + rl.getNamespace() + "." + rl.getPath()).orElse("biome.minecraft.plains");
        return DispCodec.Line.of("wildroots.journal.species.found_at", DispCodec.keyArg(biome),
                Integer.toString(patch.getX()), Integer.toString(patch.getZ()));
    }

    /**
     * {@code SpeciesDiscovered} (narrative §4.3): the species page with the
     * goals of its cultivars and the found-at rows (a new patch adds a row,
     * the last {@value #MAX_FOUND_AT} are kept), plus a silhouette for every
     * cultivar of the species not bred yet (§4.4).
     */
    public static JournalData.Unlock discoverSpecies(ServerPlayer player, String species,
                                                     Optional<DispCodec.Line> patchRow) {
        Optional<JournalData> j = Journals.of(player);
        if (j.isEmpty()) {
            return JournalData.Unlock.NONE;
        }
        DefinitionsSnapshot snap = Definitions.current();
        String id = speciesEntry(species);
        List<DispCodec.Line> found = new ArrayList<>();
        j.get().entry(id).ifPresent(e -> e.rows().stream()
                .filter(r -> r.key().equals("wildroots.journal.species.found_at")).forEach(found::add));
        patchRow.filter(r -> !found.contains(r)).ifPresent(found::add);
        while (found.size() > MAX_FOUND_AT) {
            found.remove(0);
        }
        List<DispCodec.Line> rows = new ArrayList<>();
        List<String> cultivars = snap.cultivars().values().stream()
                .filter(c -> c.def().species().equals(species))
                .sorted(java.util.Comparator.comparingInt((DefinitionsSnapshot.CultivarEntry c) -> c.def().tier())
                        .thenComparing(c -> c.def().id().toString()))
                .map(c -> c.def().id().toString()).toList();
        for (String cv : cultivars) {
            rows.add(DispCodec.Line.of(goalKey(cv)));
        }
        rows.addAll(found);
        JournalData.Unlock u = Journals.unlock(player, id, List.of(), rows);
        if (u == JournalData.Unlock.NEW) {
            for (String cv : cultivars) {
                if (j.get().lock(cultivarEntry(cv), List.of(DispCodec.keyArg(goalKey(cv))))) {
                    j.get().entry(cultivarEntry(cv)).ifPresent(e -> Journals.sender().sync(player,
                            new JournalSyncPacket(false, false, Journals.owner(player), List.of(e))));
                }
            }
        }
        return u;
    }

    static String goalKey(String cultivarId) {
        return "wildroots.journal." + entry("cultivar", cultivarId) + ".goal";
    }
}
