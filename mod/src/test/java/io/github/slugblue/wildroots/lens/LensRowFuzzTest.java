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
                            + "a-!".charAt(r.nextInt(3)) + (r.nextBoolean() ? "+" : r.nextBoolean() ? "" : "++");
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
}
