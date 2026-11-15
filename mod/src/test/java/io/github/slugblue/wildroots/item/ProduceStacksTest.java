package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** WR-136: {@code ProduceStacks} (docs/05-architecture.md §6.3; ADR-0007; GDD §6.4). */
class ProduceStacksTest {

    @Test
    void bitterAboveTwoIsTwoAndFalseIsNotWritten() {
        assertEquals(2, new CompoundTag().merge(ProduceStacks.tag(4, true)).getByte(ProduceStacks.GRADE));
        assertEquals(2, ProduceStacks.tag(3, true).getByte(ProduceStacks.GRADE));
        assertEquals(1, ProduceStacks.tag(1, true).getByte(ProduceStacks.GRADE));
        assertFalse(ProduceStacks.tag(3, false).contains(ProduceStacks.BITTER));
        assertTrue(ProduceStacks.tag(1, true).contains(ProduceStacks.BITTER));
    }

    @Test
    void noOtherKeysAndAtMostSixVariants() {
        Set<CompoundTag> variants = new HashSet<>();
        for (int g = -5; g <= 10; g++) {
            for (boolean b : new boolean[] {false, true}) {
                CompoundTag t = ProduceStacks.tag(g, b);
                assertTrue(Set.of(ProduceStacks.GRADE, ProduceStacks.BITTER).containsAll(t.getAllKeys()), t::toString);
                variants.add(t);
            }
        }
        assertEquals(6, variants.size(), variants::toString);
    }

    /**
     * WR-136a, WR-702, ADR-0007 revision 5 (parametrised invariant): every grade (also out-of-range ones) ×
     * bitterness × colour number (also out-of-range ones) through the colour-variant factory gives exactly 12
     * different tags: 4 grades × yellow / purple / red, and {@code bitter} is never written.
     */
    @Test
    void colourVariantsAreExactlyTwelveAndNeverBitter() {
        Set<CompoundTag> variants = new HashSet<>();
        for (int g = -5; g <= 10; g++) {
            for (boolean b : new boolean[] {false, true}) {
                for (int c : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, 100, Integer.MAX_VALUE}) {
                    CompoundTag t = ProduceStacks.tag(g, b, c);
                    assertTrue(Set.of(ProduceStacks.GRADE, ProduceStacks.COLOUR).containsAll(t.getAllKeys()), t::toString);
                    assertFalse(t.contains(ProduceStacks.BITTER), "a colour item is never bitter: " + t);
                    assertTrue(t.getByte(ProduceStacks.GRADE) >= 1 && t.getByte(ProduceStacks.GRADE) <= 4, t::toString);
                    variants.add(t);
                }
            }
        }
        assertEquals(12, variants.size(), variants::toString);
        // a bitter harvest keeps its grade cap even though the key is gone
        assertEquals(2, ProduceStacks.tag(4, true, ProduceStacks.PURPLE).getByte(ProduceStacks.GRADE));
        assertEquals(4, ProduceStacks.tag(4, false, ProduceStacks.PURPLE).getByte(ProduceStacks.GRADE));
    }

    /** The other two kinds of produce: no colour key ever; 6 variants with bitterness (wild root), 4 without (carrot). */
    @Test
    void plainProduceHasAtMostSixAndNoColour() {
        Set<CompoundTag> withBitter = new HashSet<>();
        Set<CompoundTag> without = new HashSet<>();
        for (int g = -5; g <= 10; g++) {
            for (boolean b : new boolean[] {false, true}) {
                CompoundTag t = ProduceStacks.tag(g, b);
                assertFalse(t.contains(ProduceStacks.COLOUR), t::toString);
                withBitter.add(t);
                if (!b) {
                    without.add(t);
                }
            }
        }
        assertEquals(6, withBitter.size(), "wild_carrot_root");
        assertEquals(4, without.size(), "carrot never has bitterness (the cultivar requires it domestic)");
    }

    @Test
    void colourTagsAndNumbers() {
        assertFalse(ProduceStacks.tag(3, false, ProduceStacks.YELLOW).contains(ProduceStacks.COLOUR));
        assertEquals(1, ProduceStacks.tag(3, false, ProduceStacks.PURPLE).getByte(ProduceStacks.COLOUR));
        assertEquals(2, ProduceStacks.tag(3, false, ProduceStacks.RED).getByte(ProduceStacks.COLOUR));
        assertEquals(ProduceStacks.tag(2, false), ProduceStacks.tag(3, true, ProduceStacks.YELLOW),
                "yellow is the tag without a colour key; a bitter harvest is capped at 2 and the key is dropped");
        assertEquals(ProduceStacks.PURPLE, ProduceStacks.colour(ProduceStacks.tag(2, false, ProduceStacks.PURPLE)));
        assertEquals(ProduceStacks.RED, ProduceStacks.colour(ProduceStacks.tag(2, false, ProduceStacks.RED)));
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(null));
        assertEquals(ProduceStacks.PURPLE, ProduceStacks.colourOfVariant(3));
        assertEquals(ProduceStacks.RED, ProduceStacks.colourOfVariant(4));
        // white, yellow and orange: the yellow item
        for (int v : new int[] {0, 1, 2}) {
            assertEquals(ProduceStacks.YELLOW, ProduceStacks.colourOfVariant(v));
        }
        assertEquals("yellow", ProduceStacks.colourName(ProduceStacks.YELLOW));
        assertEquals("purple", ProduceStacks.colourName(ProduceStacks.PURPLE));
        assertEquals("red", ProduceStacks.colourName(ProduceStacks.RED));
    }

    @Test
    void readBackNormalisesAndDefaultsToThree() {
        assertEquals(3, ProduceStacks.grade(null));
        assertEquals(3, ProduceStacks.grade(new CompoundTag()));
        CompoundTag forged = new CompoundTag();
        forged.putByte(ProduceStacks.GRADE, (byte) 4);
        forged.putByte(ProduceStacks.BITTER, (byte) 1);
        assertEquals(2, ProduceStacks.grade(forged), "a forged bitter grade 4 reads as 2");
    }

    @Test
    void carrotFoodTableOfGdd64() {
        int[] n = {0, 1, 2, 3, 4};
        float[] s = {0, 0.24f, 0.42f, 0.6f, 0.72f};
        for (int g = 1; g <= 4; g++) {
            assertEquals(n[g], ProduceStacks.nutrition(3, g));
            assertEquals(s[g], ProduceStacks.saturation(0.6, g, false), 1e-6);
            assertEquals(s[g] / 2, ProduceStacks.saturation(0.6, g, true), 1e-6);
        }
    }
    /**
     * Batch 5 test pass, owner decision (WR-702: three colours, orange counts as yellow): a forged colour key
     * reads as purple for the byte 1 and as red for the byte 2 only; 0, 3, -1, a string, a list or a compound
     * read as yellow; every block-state variant maps to one of the three colours.
     */
    @Test
    void forgedColourKeysReadAsYellowUnlessOneOrTwo() {
        CompoundTag t = new CompoundTag();
        for (byte b : new byte[] {0, 3, 4, -1, Byte.MAX_VALUE, Byte.MIN_VALUE}) {
            t.putByte(ProduceStacks.COLOUR, b);
            assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "byte " + b);
        }
        t.putByte(ProduceStacks.COLOUR, (byte) 1);
        assertEquals(ProduceStacks.PURPLE, ProduceStacks.colour(t));
        t.putByte(ProduceStacks.COLOUR, (byte) 2);
        assertEquals(ProduceStacks.RED, ProduceStacks.colour(t));
        t.putString(ProduceStacks.COLOUR, "1");
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "string");
        t.put(ProduceStacks.COLOUR, new net.minecraft.nbt.ListTag());
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "list");
        t.put(ProduceStacks.COLOUR, new CompoundTag());
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "compound");
        // a numeric tag of another width is read as its byte value (vanilla getByte)
        t.putInt(ProduceStacks.COLOUR, 2);
        assertEquals(ProduceStacks.RED, ProduceStacks.colour(t), "int 2");
        t.putInt(ProduceStacks.COLOUR, 3);
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "int 3");
        for (int v = -4; v <= 8; v++) {
            int c = ProduceStacks.colourOfVariant(v);
            assertTrue(c == ProduceStacks.YELLOW || c == ProduceStacks.PURPLE || c == ProduceStacks.RED, "variant " + v);
            assertEquals(v == 3 ? ProduceStacks.PURPLE : v == 4 ? ProduceStacks.RED : ProduceStacks.YELLOW, c, "variant " + v);
        }
        // the tag builder writes only purple and red, whatever colour number it is given
        for (int c : new int[] {Integer.MIN_VALUE, -1, 0, 3, 4, Integer.MAX_VALUE}) {
            assertFalse(ProduceStacks.tag(3, false, c).contains(ProduceStacks.COLOUR), "colour " + c);
        }
    }

    /** D3: grade 1 eats slowest, 32 ticks x 1.5 / 1.25 / 1.0 / 0.9; out-of-range grades clamp. */
    @Test
    void eatTicksByGrade() {
        org.junit.jupiter.api.Assertions.assertEquals(48, ProduceStacks.eatTicks(1));
        org.junit.jupiter.api.Assertions.assertEquals(40, ProduceStacks.eatTicks(2));
        org.junit.jupiter.api.Assertions.assertEquals(32, ProduceStacks.eatTicks(3));
        org.junit.jupiter.api.Assertions.assertEquals(29, ProduceStacks.eatTicks(4));
        org.junit.jupiter.api.Assertions.assertEquals(48, ProduceStacks.eatTicks(0));
        org.junit.jupiter.api.Assertions.assertEquals(29, ProduceStacks.eatTicks(9));
    }
}
