package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Batch 5 test pass: a creative stack can carry any {@code disp} row, so the
 * client's tooltip expansion of a lens row ({@link LensRow#parse} then
 * {@link LensRow#expand}) must never throw, whatever the row and whatever
 * species id the lot names. Malformed rows parse as empty (the tooltip then
 * shows the row literally); every parsed row expands to translatable lines
 * only.
 */
class LensRowFuzzTest {

    static final String ALPHABET = "syfgchduitlmbaqxzZYGFWTOEQ*+-12345609 :/._@\u0001\u001F§éЖ𝐀";
    static final String[] SPECIES = {"wildroots:wild_carrot", "", "x", ":", "wildroots:", "Upper:Case", "a:b:c",
        "minecraft:" + "p".repeat(300), "pack-x:dir/sub/name", "\u0001biome.minecraft.plains", "wildroots:../../x"};

    static String random(Random r, int max) {
        int n = r.nextInt(max + 1);
        StringBuilder b = new StringBuilder();
        while (b.codePointCount(0, b.length()) < n) {
            b.appendCodePoint(ALPHABET.codePointAt(ALPHABET.offsetByCodePoints(0, r.nextInt(
                    ALPHABET.codePointCount(0, ALPHABET.length())))));
        }
        return b.toString();
    }

    /** A plausible-looking row: valid tokens with random corruptions. */
    static String tokens(Random r) {
        StringBuilder b = new StringBuilder();
        List<Character> codes = new ArrayList<>(LensRow.CODES.values());
        int n = r.nextInt(16);
        for (int i = 0; i < n; i++) {
            b.append(codes.get(r.nextInt(codes.size()))).append((char) ('0' + r.nextInt(7)));
            if (r.nextInt(3) == 0) {
                b.append("ZYGFWTOEQ".charAt(r.nextInt(9)));
            }
            if (r.nextInt(4) == 0) {
                b.append('*');
            }
        }
        return b.toString();
    }

    /** A plausible-looking offspring band (item 6): digit pairs with random corruptions. */
    static String bands(Random r) {
        StringBuilder b = new StringBuilder();
        int n = r.nextInt(18);
        for (int i = 0; i < n; i++) {
            b.append((char) ('0' + r.nextInt(7)));
        }
        if (r.nextInt(5) == 0) {
            b.append(random(r, 2));
        }
        return b.toString();
    }

    @Test
    void parseAndExpandNeverThrowOnForgedRows() {
        Random r = new Random(0xB5_1E45L);
        int parsed = 0;
        for (int i = 0; i < 50_000; i++) {
            List<String> args = new ArrayList<>();
            int n = r.nextInt(3) == 0 ? r.nextInt(5) : 2 + r.nextInt(2); // mostly the 2–3 arguments a lens row has
            for (int a = 0; a < n; a++) {
                args.add(switch (a == 0 ? (r.nextBoolean() ? 1 : r.nextInt(4)) : a == 1 && r.nextInt(4) > 0 ? 2 : r.nextInt(4)) {
                    case 0 -> random(r, 70);
                    case 1 -> tokens(r);
                    case 2 -> "" + "wd-x".charAt(r.nextInt(4)) + "BHNS-?".charAt(r.nextInt(6))
                            + "a-!".charAt(r.nextInt(3)) + (r.nextBoolean() ? "+" : r.nextBoolean() ? "" : "++")
                            + (r.nextInt(10) == 0 ? "~" + bands(r) : "");
                    default -> random(r, 3);
                });
            }
            DispCodec.Line line = new DispCodec.Line(r.nextInt(10) == 0 ? "wildroots.tooltip.trait" : LensRow.KEY, args);
            String species = SPECIES[r.nextInt(SPECIES.length)];
            Optional<LensRow> row = assertDoesNotThrow(() -> LensRow.parse(line), line::toString);
            if (row.isPresent()) {
                parsed++;
                List<Component> out = assertDoesNotThrow(() -> row.get().expand(species), () -> line + " / " + species);
                assertTrue(out.size() <= DispCodec.MAX_ARG_LENGTH / 2 + 5, "expanded into " + out.size() + " lines");
                out.forEach(c -> assertDoesNotThrow(() -> { c.getString(); }));
                assertDoesNotThrow(() -> row.get().degradations());
                // item 6: a parsed band always has one valid pair per token, and the row re-parses to itself
                List<LensRow.Token> tk = LensRow.tokens(row.get().traits());
                for (int t = 0; t < tk.size(); t++) {
                    row.get().band(t).ifPresent(lh -> assertTrue(lh[0] >= 1 && lh[0] <= lh[1] && lh[1] <= 5,
                            line::toString));
                }
                assertTrue(row.get().bands().isEmpty() || row.get().bands().length() == 2 * tk.size(), line::toString);
                assertTrue(LensRow.parse(row.get().line()).equals(row), line::toString);
                assertDoesNotThrow(() -> LensSheet.lot(Component.literal("t"), List.of(), row.get(), species)
                        .bars().forEach(b -> b.label().getString()), line::toString);
            } else {
                // the fallback the tooltip uses for anything that is not a lens row
                assertDoesNotThrow(() -> { DispCodec.toComponent(line).getString(); }, line::toString);
            }
        }
        assertTrue(parsed > 1000, "the fuzz reached the expansion only " + parsed + " times");
    }

    /** A row that parses has only known trait codes, steps 1–5 and known scales, whatever junk surrounds it. */
    @Test
    void everyParsedTokenIsKnown() {
        Random r = new Random(11);
        for (int i = 0; i < 20_000; i++) {
            String t = r.nextBoolean() ? tokens(r) : random(r, 20);
            for (LensRow.Token k : LensRow.tokens(t)) {
                assertTrue(LensRow.CODES.containsKey(k.trait()), t);
                assertTrue(k.step() >= 1 && k.step() <= 5, t);
                assertTrue(LensRow.SCALE_CODES.containsKey(k.scale()), t);
            }
        }
    }

    /** Item 6: rows with an offspring band of the right length, then one character changed. */
    @Test
    void bandedRowsParseOrAreRefusedNeverThrow() {
        Random r = new Random(0xBA_4D5L);
        int banded = 0;
        for (int i = 0; i < 20_000; i++) {
            String t = "s3y2g4f1c5h2d3".substring(0, 2 * (1 + r.nextInt(7)));
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < t.length() / 2; k++) {
                int lo = 1 + r.nextInt(5);
                b.append(lo).append(lo + r.nextInt(6 - lo));
            }
            String flags = "dS-" + (r.nextBoolean() ? "+" : "") + "~" + b;
            if (r.nextBoolean()) {
                char[] c = flags.toCharArray();
                c[r.nextInt(c.length)] = ALPHABET.charAt(r.nextInt(30));
                flags = new String(c);
            }
            DispCodec.Line line = DispCodec.Line.of(LensRow.KEY, t, flags);
            Optional<LensRow> row = assertDoesNotThrow(() -> LensRow.parse(line), line::toString);
            if (row.isPresent() && !row.get().bands().isEmpty()) {
                banded++;
                assertTrue(LensRow.parse(row.get().line()).equals(row), line::toString);
                List<LensSheet.Bar> bars = LensSheet.bars(row.get(), "wildroots:wild_carrot");
                assertTrue(bars.stream().allMatch(LensSheet.Bar::hasBand), line::toString);
            }
        }
        assertTrue(banded > 5000, "banded rows parsed " + banded);
    }
}
