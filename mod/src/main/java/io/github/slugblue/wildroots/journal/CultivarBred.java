package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code CultivarBred} (narrative §4.4; GDD §6.2 item 4): the first time a
 * plant of a cultivar is recognised for the player who planted it, the
 * cultivar page opens with the day, the line and the path of steps, the
 * "Cultivar bred!" toast goes to that player (not throttled), and the
 * {@code first_landrace} / {@code first_cultivar} hint for its tier.
 */
public final class CultivarBred {

    public static final String LANDRACE_TITLE = "wildroots.message.toast.landrace_bred.title";
    public static final String CULTIVAR_TITLE = "wildroots.message.toast.cultivar_bred.title";
    public static final String BODY = "wildroots.message.toast.bred.body";
    /** Longest path of steps shown (species + up to three cultivars). */
    static final int MAX_PATH = 4;

    private CultivarBred() {
    }

    /** {@code true} if this was the first time for the player. */
    public static boolean bred(ServerLevel level, ServerPlayer player, PlantBlockEntity.Live live) {
        PlantState d = live.data();
        String cv = d.cultivar();
        Optional<JournalData> j = Journals.of(player);
        if (cv == null || j.isEmpty() || j.get().has(JournalTriggers.cultivarEntry(cv))) {
            return false;
        }
        long day = level.getDayTime() / HintTriggers.DAY + 1;
        List<DispCodec.Line> rows = new ArrayList<>();
        rows.add(DispCodec.Line.of("wildroots.journal.cultivar.bred_on", Long.toString(day)));
        if (d.lineName() != null && !d.lineName().isEmpty()) {
            String name = d.lineName().length() > DispCodec.MAX_ARG_LENGTH
                    ? d.lineName().substring(0, DispCodec.MAX_ARG_LENGTH) : d.lineName();
            rows.add(DispCodec.Line.of("wildroots.journal.cultivar.line", name, Integer.toString(d.generation())));
        }
        rows.add(new DispCodec.Line("wildroots.journal.cultivar.ancestry_step", path(live.snapshot(), d.species(), cv)));
        if ((d.lotFlags() & LotFlags.FROM_WILD) != 0) {
            rows.add(DispCodec.Line.of("wildroots.journal.mark.from_the_wild"));
        }
        String tierKey = "wildroots.tooltip.tier." + Math.max(0, Math.min(2, d.tier()));
        JournalData.Unlock u = Journals.unlock(player, JournalTriggers.cultivarEntry(cv),
                List.of(DispCodec.keyArg(tierKey)), rows);
        if (u != JournalData.Unlock.NEW) {
            return false;
        }
        // the page is committed: the toast cannot undo it (a long id gets a literal name, review 1, m2)
        ToastPacket t = new ToastPacket(DispCodec.Line.of(d.tier() >= 2 ? CULTIVAR_TITLE : LANDRACE_TITLE),
                DispCodec.Line.of(BODY, JournalTriggers.nameArg("cultivar", cv)));
        io.github.slugblue.wildroots.core.safety.Guarded.run("bred toast", player.getUUID(), () -> Journals.toast(player, t));
        HintTriggers.hint(player, d.tier() >= 2 ? "first_cultivar" : "first_landrace");
        return true;
    }

    /** Wild species, then the parent chain of the cultivar up to it (key arguments, ≤ {@value #MAX_PATH}). */
    static List<String> path(DefinitionsSnapshot snap, String species, String cultivar) {
        List<String> steps = new ArrayList<>();
        Optional<String> at = Optional.of(cultivar);
        while (at.isPresent() && steps.size() < MAX_PATH - 1) {
            String id = at.get();
            steps.add(DispCodec.keyArg(JournalTriggers.nameKey("cultivar", id)));
            at = snap.cultivar(id).map(DefinitionsSnapshot.CultivarEntry::def).flatMap(CultivarDef::parent)
                    .map(CultivarId::value).filter(p -> !steps.contains(p));
        }
        steps.add(DispCodec.keyArg(JournalTriggers.nameKey("species", species)));
        Collections.reverse(steps);
        return steps;
    }
}
