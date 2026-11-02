package io.github.slugblue.wildroots.data;

import static io.github.slugblue.wildroots.data.codec.PathCodecs.ID;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.STR;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.list;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.object;
import static io.github.slugblue.wildroots.data.codec.PathCodecs.string;

import com.mojang.serialization.Codec;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * History of genome layouts of a species, {@code data/<ns>/genome_layouts/<id>.json}
 * (docs/05-architecture.md §5.3; WR-129): every layout a released version of
 * the species had, so that old genomes migrate after the JSON changes.
 *
 * <pre>{"species": "wildroots:wild_carrot",
 *  "history": [{"lh": "-3539622408563365149", "canonical": "q:cold_tolerance:3|…", "since": "1.20.1-0.1.0"}]}</pre>
 *
 * <p>{@code lh} is a decimal string (a 64-bit value does not survive JSON
 * numbers in every reader) and must equal the FNV-1a 64 of {@code canonical}.
 */
public final class LayoutHistorySchema {

    private LayoutHistorySchema() {
    }

    public record Entry(String lh, String canonical, Optional<String> since) {
    }

    public record Raw(ResourceLocation species, List<Entry> history) {
    }

    static final Codec<Entry> ENTRY = object(f -> new Entry(f.req("lh", STR),
            f.req("canonical", string(GenomeLayout.MAX_CANONICAL_LENGTH)), f.opt("since", STR)));

    public static final Codec<Raw> CODEC = object(f -> new Raw(f.req("species", ID), f.req("history", list(ENTRY))));

    /** Descriptors of one file, with an ERROR per entry whose hash or canonical string is wrong. */
    public record Mapped(List<LayoutDescriptor> layouts, List<Problem> problems) {
    }

    public static Mapped toDescriptors(Raw raw) {
        List<LayoutDescriptor> out = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (int i = 0; i < raw.history().size(); i++) {
            Entry e = raw.history().get(i);
            String path = "history[" + i + "]";
            long lh;
            try {
                lh = Long.parseLong(e.lh());
            } catch (NumberFormatException ex) {
                problems.add(Problem.error(path + ".lh", "64-bit decimal expected"));
                continue;
            }
            Optional<GenomeLayout> layout = GenomeLayout.parse(e.canonical());
            if (layout.isEmpty()) {
                problems.add(Problem.error(path + ".canonical", "not a canonical layout string"));
                continue;
            }
            if (layout.get().hash() != lh) {
                problems.add(Problem.error(path + ".lh", "does not match the hash of the canonical string"));
                continue;
            }
            out.add(new LayoutDescriptor(raw.species().toString(), lh, e.canonical()));
        }
        return new Mapped(out, problems);
    }
}
