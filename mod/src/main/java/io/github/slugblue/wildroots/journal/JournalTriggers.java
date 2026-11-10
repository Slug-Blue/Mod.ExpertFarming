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

    /** Longest part of an entry id after the kind (JournalData.ID). */
    static final int MAX_REST = 64;
    private static final io.github.slugblue.wildroots.core.safety.LogThrottle ID_WARN =
            new io.github.slugblue.wildroots.core.safety.LogThrottle(System::currentTimeMillis, 5 * 60 * 1000L);

    /**
     * Entry id of a data id (review 1, m2): {@code /} becomes {@code .},
     * {@code -} becomes {@code _}; a rest over {@value #MAX_REST} characters
     * is cut to 47 and gets a hash, so any species of a data pack still has
     * a page (its name key may then not resolve on the client: one throttled
     * warning in the log).
     */
    static String entry(String kind, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return kind + ".unknown";
        }
        String rest = (rl.getNamespace().equals("wildroots") ? rl.getPath() : rl.getNamespace() + "." + rl.getPath())
                .replace('/', '.').replace('-', '_');
        if (rest.length() > MAX_REST) {
            if (ID_WARN.allow(id)) {
                org.slf4j.LoggerFactory.getLogger(JournalTriggers.class)
                        .warn("Wild Roots: id {} is too long for a journal entry, shortened", id);
            }
            rest = rest.substring(0, 47) + "." + String.format("%016x",
                    io.github.slugblue.wildroots.genetics.random.Hash64.str(id));
        }
        return kind + "." + rest;
    }

    /** A name as a key argument; a literal of the id if the key is too long for an argument. */
    static String nameArg(String kind, String id) {
        String a = DispCodec.keyArg(nameKey(kind, id));
        if (DispCodec.valid(DispCodec.Line.of("wildroots.journal", a))) {
            return a;
        }
        String lit = id.codePointCount(0, id.length()) > DispCodec.MAX_ARG_LENGTH
                ? id.substring(0, id.offsetByCodePoints(0, DispCodec.MAX_ARG_LENGTH)) : id;
        return DispCodec.valid(DispCodec.Line.of("wildroots.journal", lit)) ? lit : "?";
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
        discoverSpecies(player, species, wild ? foundAt(level, pos, BlockPos.of(passport.patchOrigin()))
                : Optional.empty());
        if (first && !j.get().has("hint.welcome")) {
            Journals.unlock(player, "hint.welcome", nameArg("species", species));
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
    static Optional<DispCodec.Line> foundAt(ServerLevel level, BlockPos plant, BlockPos patch) {
        return foundAtRow(level.getBiome(plant).unwrapKey().map(ResourceKey::location)
                .orElse(new ResourceLocation("minecraft", "plains")), patch);
    }

    /**
     * The found-at row of a biome and a patch, with the vanilla description
     * key ({@code Util.makeDescriptionId}); empty if the key is too long for
     * a row argument (the species page still opens, without this row).
     */
    public static Optional<DispCodec.Line> foundAtRow(ResourceLocation biome, BlockPos patch) {
        DispCodec.Line l = DispCodec.Line.of("wildroots.journal.species.found_at",
                DispCodec.keyArg(net.minecraft.Util.makeDescriptionId("biome", biome)),
                Integer.toString(patch.getX()), Integer.toString(patch.getZ()));
        return DispCodec.valid(l) ? Optional.of(l) : Optional.empty();
    }

    /**
     * Rows of a species page (review 1, m2): at most
     * {@value JournalData#MAX_ROWS} - {@value #MAX_FOUND_AT} goals, then the
     * found-at rows; invalid rows are left out, never the whole page.
     */
    public static List<DispCodec.Line> speciesRows(List<String> cultivars, List<DispCodec.Line> found) {
        List<DispCodec.Line> rows = new ArrayList<>();
        for (String cv : cultivars) {
            DispCodec.Line g = DispCodec.Line.of(goalKey(cv));
            if (rows.size() < JournalData.MAX_ROWS - MAX_FOUND_AT && DispCodec.valid(g)) {
                rows.add(g);
            }
        }
        for (DispCodec.Line f : found) {
            if (rows.size() < JournalData.MAX_ROWS && DispCodec.valid(f)) {
                rows.add(f);
            }
        }
        return rows;
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
        List<String> cultivars = snap.cultivars().values().stream()
                .filter(c -> c.def().species().equals(species))
                .sorted(java.util.Comparator.comparingInt((DefinitionsSnapshot.CultivarEntry c) -> c.def().tier())
                        .thenComparing(c -> c.def().id().toString()))
                .map(c -> c.def().id().toString()).toList();
        List<DispCodec.Line> rows = speciesRows(cultivars, found);
        JournalData.Unlock u = Journals.unlock(player, id, List.of(), rows);
        if (u == JournalData.Unlock.NEW) {
            for (String cv : cultivars) {
                String goal = DispCodec.keyArg(goalKey(cv));
                List<String> args = DispCodec.valid(DispCodec.Line.of("wildroots.journal", goal)) ? List.of(goal) : List.of();
                if (j.get().lock(cultivarEntry(cv), args)) {
                    j.get().entry(cultivarEntry(cv)).ifPresent(e -> Journals.delta(player, j.get(), e));
                }
            }
        }
        return u;
    }

    static String goalKey(String cultivarId) {
        return "wildroots.journal." + entry("cultivar", cultivarId) + ".goal";
    }
}
