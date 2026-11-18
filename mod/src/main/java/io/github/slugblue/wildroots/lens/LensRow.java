package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.data.SpeciesInfo;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.journal.JournalTriggers;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The tier-1 reading of an inspected lot as one compact {@code disp} row
 * (review 1, M1): {@value #KEY} with up to three arguments,
 *
 * <ol>
 *   <li>traits: per trait of the species in layout order a code letter
 *   ({@link #CODES}), the step 1–5, an upper-case scale letter if the
 *   species changes the scale ({@link #SCALE_CODES}) and {@code *} if it
 *   renames the trait ({@code s4*y3g2f3Wc3h2d2} for the carrot);</li>
 *   <li>flags: bitterness {@code w} (bitter) / {@code d} / {@code -},
 *   pollination {@code B H N S -}, inbreeding {@code a} / {@code -},
 *   {@code +} when something was left out to fit the lot's 1 KiB, and
 *   optionally {@code ~} with the offspring band (playtest 2, item 6): per
 *   trait token the lowest and the highest step 1–5 that the mother's value
 *   ± one expected child standard deviation reaches ({@link OffspringBand}),
 *   {@code d2~2334...};</li>
 *   <li>the cultivar it looks like ({@code eastern_carrot}, or
 *   {@code ns:path} for data packs), absent if none.</li>
 * </ol>
 * The tooltip expands the row into the same lines the lens shows on a
 * plant, through lang keys only. About 50 bytes instead of about 600, so
 * every tier-1 line fits a lot with 4 fathers and a 50-character name.
 */
public record LensRow(String traits, char bitter, char pollination, boolean ailing, Optional<String> looksLike,
                      boolean more, String bands) {

    public static final String KEY = "wildroots.tooltip.lens";
    public static final String MORE_KEY = "wildroots.tooltip.lens.more";

    /** Trait code letters (fixed; a trait not listed is left out and sets "more"). */
    public static final Map<String, Character> CODES = Map.ofEntries(Map.entry("fruit_size", 's'),
            Map.entry("yield", 'y'), Map.entry("growth_speed", 'g'), Map.entry("flavor", 'f'),
            Map.entry("cold_tolerance", 'c'), Map.entry("heat_tolerance", 'h'), Map.entry("drought_tolerance", 'd'),
            Map.entry("sugar", 'u'), Map.entry("leaf_size", 'l'), Map.entry("terminal_bud", 't'),
            Map.entry("inflorescence", 'i'), Map.entry("stem", 'm'), Map.entry("lateral_buds", 'b'),
            Map.entry("apical_dominance", 'a'));
    /** Scale letters. */
    public static final Map<String, Character> SCALE_CODES = Map.of("size", 'Z', "yield", 'Y', "growth", 'G',
            "flavor", 'F', "sweetness", 'W', "tenderness", 'T', "tolerance", 'O', "expression", 'E');

    public LensRow {
        bands = bands == null ? "" : bands;
    }

    /** A row without the offspring band. */
    public LensRow(String traits, char bitter, char pollination, boolean ailing, Optional<String> looksLike,
                   boolean more) {
        this(traits, bitter, pollination, ailing, looksLike, more, "");
    }

    /** The same row with the band of every trait token ({@code lo, hi} per token, steps 1–5); empty drops it. */
    public LensRow withBands(int[][] perToken) {
        if (perToken == null || perToken.length != tokens(traits).size() || perToken.length == 0) {
            return new LensRow(traits, bitter, pollination, ailing, looksLike, more, "");
        }
        // review 1, m3: the band never makes the row invalid; if the flags would pass one disp argument,
        // the row goes without it
        if (4 + 1 + 2 * perToken.length > DispCodec.MAX_ARG_LENGTH) {
            return new LensRow(traits, bitter, pollination, ailing, looksLike, more, "");
        }
        StringBuilder b = new StringBuilder();
        for (int[] lh : perToken) {
            int lo = Math.max(1, Math.min(5, lh[0]));
            int hi = Math.max(lo, Math.min(5, lh[1]));
            b.append((char) ('0' + lo)).append((char) ('0' + hi));
        }
        return new LensRow(traits, bitter, pollination, ailing, looksLike, more, b.toString());
    }

    /** Band of trait token {@code i}: {@code {lo, hi}}, or empty if the row has none. */
    public Optional<int[]> band(int i) {
        if (bands.isEmpty() || 2 * i + 1 >= bands.length() || i < 0) {
            return Optional.empty();
        }
        return Optional.of(new int[] {bands.charAt(2 * i) - '0', bands.charAt(2 * i + 1) - '0'});
    }

    /** One decoded trait token. */
    public record Token(String trait, int step, String scale, boolean speciesName) {
    }

    // ------------------------------------------------------------------ build (server)

    /** The full row of a reading. */
    public static LensRow of(SpeciesDef def, SpeciesInfo info, Phenotype p, LensReading.Pollination how,
                             Optional<CultivarDef> looks) {
        return of(def, info, p, how, looks, null);
    }

    /**
     * The full row with the offspring band: {@code sd[i]} is the expected
     * child standard deviation of quantitative trait {@code i} of the
     * species ({@link OffspringBand}); {@code null} for no band.
     */
    public static LensRow of(SpeciesDef def, SpeciesInfo info, Phenotype p, LensReading.Pollination how,
                             Optional<CultivarDef> looks, double[] sd) {
        List<int[]> band = new ArrayList<>();
        StringBuilder t = new StringBuilder();
        boolean more = false;
        Map<String, String> dt = info.displayTraits();
        for (QuantTrait q : def.quantitative()) {
            String id = q.id().value();
            Character code = CODES.get(id);
            if (code == null) {
                more = true;
                continue;
            }
            String scale = LensScale.scale(id, dt);
            double v = p.pOr(q.id(), Double.NaN);
            t.append(code).append((char) ('0' + LensScale.step(v)));
            if (sd != null) {
                band.add(OffspringBand.steps(v, def.quantitative().indexOf(q) < sd.length
                        ? sd[def.quantitative().indexOf(q)] : 0));
            }
            if (!scale.equals(LensScale.DEFAULT_SCALE.getOrDefault(id, "tolerance"))) {
                t.append(SCALE_CODES.get(scale));
            }
            if (!LensScale.nameKey(id, "x", dt).equals("wildroots.tooltip.trait_name." + id)) {
                t.append('*');
            }
        }
        char bitter = def.qual(Traits.BITTERNESS) == null ? '-' : p.domestic(Traits.BITTERNESS) ? 'd' : 'w';
        char poll = switch (how) {
            case BEES -> 'B';
            case BUTTERFLIES -> 'F';
            case HAND -> 'H';
            case NEIGHBOURS -> 'N';
            case SELF -> 'S';
            case NONE -> '-';
        };
        Optional<String> l = looks.map(c -> c.id().value()).map(LensRow::shortId)
                .filter(s -> s.codePointCount(0, s.length()) <= DispCodec.MAX_ARG_LENGTH);
        LensRow row = new LensRow(t.toString(), bitter, poll, p.vigor() < GrowthLogic.AILING_BELOW, l,
                more || (looks.isPresent() && l.isEmpty()));
        return sd == null ? row : row.withBands(band.toArray(new int[0][]));
    }

    static String shortId(String id) {
        return id.startsWith("wildroots:") ? id.substring("wildroots:".length()) : id;
    }

    /**
     * This row and smaller ones, in the order to try when the lot would pass
     * its limit: pollination and inbreeding first, then the last traits, then
     * the cultivar it looks like, then bitterness; every smaller row says
     * "and more". The last is an empty "and more" row.
     */
    public List<LensRow> degradations() {
        List<LensRow> out = new ArrayList<>();
        out.add(this);
        if (!bands.isEmpty()) {
            // the band goes first: it is the least needed part of a lot that is full
            out.add(new LensRow(traits, bitter, pollination, ailing, looksLike, more, ""));
        }
        LensRow r = new LensRow(traits, bitter, '-', false, looksLike, true);
        out.add(r);
        String t = traits;
        while (!t.isEmpty()) {
            t = dropLastToken(t);
            out.add(new LensRow(t, bitter, '-', false, looksLike, true));
        }
        out.add(new LensRow("", bitter, '-', false, Optional.empty(), true));
        out.add(new LensRow("", '-', '-', false, Optional.empty(), true));
        return out;
    }

    static String dropLastToken(String t) {
        int i = t.length() - 1;
        while (i > 0 && !Character.isLowerCase(t.charAt(i))) {
            i--;
        }
        return t.substring(0, Math.max(0, i));
    }

    public DispCodec.Line line() {
        String flags = "" + bitter + pollination + (ailing ? 'a' : '-') + (more ? "+" : "")
                + (bands.isEmpty() ? "" : "~" + bands);
        return looksLike.map(l -> DispCodec.Line.of(KEY, traits, flags, l)).orElse(DispCodec.Line.of(KEY, traits, flags));
    }

    // ------------------------------------------------------------------ read and expand (both sides, display only)

    /** Parses a row; empty if it is not a lens row or is malformed. */
    public static Optional<LensRow> parse(DispCodec.Line line) {
        if (!KEY.equals(line.key()) || line.args().size() < 2 || line.args().size() > 3) {
            return Optional.empty();
        }
        String all = line.args().get(1);
        int tilde = all.indexOf('~');
        String f = tilde < 0 ? all : all.substring(0, tilde);
        String bands = tilde < 0 ? "" : all.substring(tilde + 1);
        if (f.length() < 3 || f.length() > 4 || "wd-".indexOf(f.charAt(0)) < 0 || "BFHNS-".indexOf(f.charAt(1)) < 0
                || "a-".indexOf(f.charAt(2)) < 0 || (f.length() == 4 && f.charAt(3) != '+')) {
            return Optional.empty();
        }
        String t = line.args().get(0);
        List<Token> tk = tokens(t);
        if (tk.isEmpty() && !t.isEmpty()) {
            return Optional.empty();
        }
        if (tilde >= 0 && !validBands(bands, tk.size())) {
            return Optional.empty();
        }
        return Optional.of(new LensRow(t, f.charAt(0), f.charAt(1), f.charAt(2) == 'a',
                line.args().size() == 3 ? Optional.of(line.args().get(2)) : Optional.empty(), f.length() == 4, bands));
    }

    /** Two digits 1–5 per token, low ≤ high. */
    static boolean validBands(String b, int tokens) {
        if (tokens == 0 || b.length() != 2 * tokens) {
            return false;
        }
        for (int i = 0; i < b.length(); i += 2) {
            char lo = b.charAt(i);
            char hi = b.charAt(i + 1);
            if (lo < '1' || lo > '5' || hi < '1' || hi > '5' || hi < lo) {
                return false;
            }
        }
        return true;
    }

    /** The trait tokens; empty if malformed. */
    public static List<Token> tokens(String t) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            String trait = trait(c);
            if (trait == null || i + 1 >= t.length() || t.charAt(i + 1) < '1' || t.charAt(i + 1) > '5') {
                return List.of();
            }
            int step = t.charAt(i + 1) - '0';
            i += 2;
            String scale = LensScale.DEFAULT_SCALE.getOrDefault(trait, "tolerance");
            if (i < t.length() && Character.isUpperCase(t.charAt(i))) {
                String s = scale(t.charAt(i));
                if (s == null) {
                    return List.of();
                }
                scale = s;
                i++;
            }
            boolean named = i < t.length() && t.charAt(i) == '*';
            if (named) {
                i++;
            }
            out.add(new Token(trait, step, scale, named));
        }
        return out;
    }

    static String trait(char code) {
        for (Map.Entry<String, Character> e : CODES.entrySet()) {
            if (e.getValue() == code) {
                return e.getKey();
            }
        }
        return null;
    }

    static String scale(char code) {
        for (Map.Entry<String, Character> e : SCALE_CODES.entrySet()) {
            if (e.getValue() == code) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Name key of a trait token of {@code species} (its id): the species name when the token says so. */
    public static String traitNameKey(Token k, String species) {
        String path = JournalTriggers.speciesEntry(species).substring("species.".length());
        return "wildroots.tooltip.trait_name." + k.trait() + (k.speciesName() ? "." + path : "");
    }

    /** The tooltip lines of the row for a lot of {@code species} (its id). */
    public List<Component> expand(String species) {
        List<Component> out = new ArrayList<>();
        for (Token k : tokens(traits)) {
            out.add(Component.translatable(LensReading.TRAIT, Component.translatable(traitNameKey(k, species)),
                    Component.translatable(LensScale.wordKey(k.scale(), k.step()))));
        }
        out.addAll(notes());
        return out;
    }

    /** The lines of the row after the traits: bitterness, pollination, inbreeding, looks like, and more. */
    public List<Component> notes() {
        List<Component> out = new ArrayList<>();
        if (bitter != '-') {
            out.add(Component.translatable("wildroots.tooltip.qual_state.bitterness." + (bitter == 'd' ? "domestic" : "wild")));
        }
        String poll = switch (pollination) {
            case 'B' -> "bees";
            case 'F' -> "butterflies";
            case 'H' -> "hand";
            case 'N' -> "neighbours";
            case 'S' -> "self";
            default -> null;
        };
        if (poll != null) {
            out.add(Component.translatable("wildroots.tooltip.pollination." + poll));
        }
        if (ailing) {
            out.add(Component.translatable(LensReading.AILING));
        }
        looksLike.ifPresent(l -> out.add(Component.translatable(LensReading.LOOKS_LIKE, Component.translatable(
                JournalTriggers.nameKey("cultivar", l.contains(":") ? l : "wildroots:" + l)))));
        if (more) {
            out.add(Component.translatable(MORE_KEY));
        }
        return out;
    }
}
