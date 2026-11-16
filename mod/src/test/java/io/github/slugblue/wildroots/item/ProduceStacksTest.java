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
     * WR-706, ADR-0007 revision 6 (parametrised invariant): every grade (also out-of-range ones) x bitterness x
     * colour number (also out-of-range ones) through the colour-variant factory gives exactly 16 different tags:
     * 4 grades x yellow / orange / purple / red, and {@code bitter} is never written.
     */
    @Test
    void colourVariantsAreExactlySixteenAndNeverBitter() {
        for (int def : new int[] {ProduceStacks.YELLOW, ProduceStacks.ORANGE}) {
            Set<CompoundTag> variants = new HashSet<>();
            for (int g = -5; g <= 10; g++) {
                for (boolean b : new boolean[] {false, true}) {
                    for (int c : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, 100, Integer.MAX_VALUE}) {
                        CompoundTag t = ProduceStacks.tag(g, b, c, def);
                        assertTrue(Set.of(ProduceStacks.GRADE, ProduceStacks.COLOUR).containsAll(t.getAllKeys()), t::toString);
                        assertFalse(t.contains(ProduceStacks.BITTER), "a colour item is never bitter: " + t);
                        variants.add(t);
                    }
                }
            }
            assertEquals(16, variants.size(), "default " + def + ": " + variants);
        }
        assertEquals(2, ProduceStacks.tag(4, true, ProduceStacks.PURPLE).getByte(ProduceStacks.GRADE));
        assertEquals(4, ProduceStacks.tag(4, false, ProduceStacks.PURPLE).getByte(ProduceStacks.GRADE));
    }

    /**
     * The other kinds of produce: no colour key ever; 6 variants with bitterness (wild root). The 4 of the
     * cultivar carrot are NOT enforced by the factory: a carrot is never bitter because its profile requires
     * bitterness domestic (the classifier), so here the bitter-free subset is only counted.
     */
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
        assertEquals(4, without.size(), "the bitter-free subset: 4 grades");
    }

    @Test
    void colourTagsAndNumbers() {
        // the default colour of an item is not written; every other one is its number
        assertFalse(ProduceStacks.tag(3, false, ProduceStacks.YELLOW, ProduceStacks.YELLOW).contains(ProduceStacks.COLOUR));
        assertFalse(ProduceStacks.tag(3, false, ProduceStacks.ORANGE, ProduceStacks.ORANGE).contains(ProduceStacks.COLOUR));
        assertEquals(1, ProduceStacks.tag(3, false, ProduceStacks.PURPLE).getByte(ProduceStacks.COLOUR));
        assertEquals(2, ProduceStacks.tag(3, false, ProduceStacks.RED).getByte(ProduceStacks.COLOUR));
        assertEquals(3, ProduceStacks.tag(3, false, ProduceStacks.ORANGE).getByte(ProduceStacks.COLOUR));
        assertEquals(0, ProduceStacks.tag(3, false, ProduceStacks.YELLOW, ProduceStacks.ORANGE).getByte(ProduceStacks.COLOUR),
                "a yellow carrot (orange by default) says so");
        assertEquals(ProduceStacks.tag(2, false), ProduceStacks.tag(3, true, ProduceStacks.YELLOW),
                "yellow is the tag without a colour key; a bitter harvest is capped at 2 and the key is dropped");
        for (int c = 0; c < ProduceStacks.COLOURS; c++) {
            for (int def : new int[] {ProduceStacks.YELLOW, ProduceStacks.ORANGE}) {
                assertEquals(c, ProduceStacks.colour(ProduceStacks.tag(2, false, c, def), def));
            }
        }
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(null));
        assertEquals(ProduceStacks.ORANGE, ProduceStacks.colour(null, ProduceStacks.ORANGE), "an old carrot stack is orange");
        // block variant: 1 yellow, 2 orange, 3 purple, 4 red; white takes the item's default
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colourOfVariant(1));
        assertEquals(ProduceStacks.ORANGE, ProduceStacks.colourOfVariant(2));
        assertEquals(ProduceStacks.PURPLE, ProduceStacks.colourOfVariant(3));
        assertEquals(ProduceStacks.RED, ProduceStacks.colourOfVariant(4));
        assertEquals(ProduceStacks.ORANGE, ProduceStacks.colourOfVariant(0, ProduceStacks.ORANGE));
        assertEquals("yellow", ProduceStacks.colourName(ProduceStacks.YELLOW));
        assertEquals("orange", ProduceStacks.colourName(ProduceStacks.ORANGE));
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
     * WR-706: a colour key reads as its number 0-3; a number outside, a string, a list or a compound is the item's
     * default colour. An old stack (no key, 1b, 2b) keeps its meaning; an old orange eastern carrot, merged with
     * yellow at the time, reads as yellow.
     */
    @Test
    void forgedColourKeysReadAsTheDefault() {
        CompoundTag t = new CompoundTag();
        for (byte b : new byte[] {4, 5, -1, Byte.MAX_VALUE, Byte.MIN_VALUE}) {
            t.putByte(ProduceStacks.COLOUR, b);
            assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "byte " + b);
            assertEquals(ProduceStacks.ORANGE, ProduceStacks.colour(t, ProduceStacks.ORANGE), "byte " + b);
        }
        for (int c = 0; c < ProduceStacks.COLOURS; c++) {
            t.putByte(ProduceStacks.COLOUR, (byte) c);
            assertEquals(c, ProduceStacks.colour(t));
        }
        t.putString(ProduceStacks.COLOUR, "1");
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "string");
        t.put(ProduceStacks.COLOUR, new net.minecraft.nbt.ListTag());
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "list");
        t.put(ProduceStacks.COLOUR, new CompoundTag());
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "compound");
        t.putInt(ProduceStacks.COLOUR, 2);
        assertEquals(ProduceStacks.RED, ProduceStacks.colour(t), "int 2 reads as its byte value");
        t.putInt(ProduceStacks.COLOUR, 9);
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(t), "int 9");
        // old stacks keep their meaning
        assertEquals(ProduceStacks.PURPLE, ProduceStacks.colour(ProduceStacks.tag(3, false, 1)));
        assertEquals(ProduceStacks.YELLOW, ProduceStacks.colour(new CompoundTag()));
        for (int v = -4; v <= 8; v++) {
            int c = ProduceStacks.colourOfVariant(v);
            assertTrue(c >= 0 && c < ProduceStacks.COLOURS, "variant " + v);
        }
        for (int c : new int[] {Integer.MIN_VALUE, -1, 4, Integer.MAX_VALUE}) {
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
