package io.github.slugblue.wildroots.genetics.random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * WR-101: SplitMix64, Hash64 and FNV-1a 64 against golden values produced by
 * an independent Python implementation ({@code src/test/golden-tools/wr101_reference.py}).
 */
class RandomGoldenTest {

    static List<String> lines(String resource) throws IOException {
        InputStream in = RandomGoldenTest.class.getResourceAsStream(resource);
        assertTrue(in != null, resource + " missing");
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#")) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {0, 42})
    void splitMix64FirstThousandValuesMatchGolden(long seed) throws IOException {
        SplitMix64 rng = new SplitMix64(seed);
        int checked = 0;
        for (String line : lines("/golden/splitmix64.txt")) {
            String[] f = line.split(" ");
            if (Long.parseLong(f[0]) != seed) {
                continue;
            }
            assertEquals(checked, Integer.parseInt(f[1]));
            assertEquals(Long.parseUnsignedLong(f[2], 16), rng.nextLong(), "seed " + seed + " index " + checked);
            checked++;
        }
        assertEquals(1000, checked);
    }

    @Test
    void fnv1a64AndHash64MatchGoldenForTwentyStrings() throws IOException {
        List<String> rows = lines("/golden/hash64.txt");
        assertEquals(20, rows.size());
        boolean sawCyrillic = false;
        boolean sawEmpty = false;
        for (int i = 0; i < rows.size(); i++) {
            String[] f = rows.get(i).split("\t", -1);
            String s = f[2];
            sawCyrillic |= s.chars().anyMatch(c -> c >= 0x400 && c <= 0x4FF);
            sawEmpty |= s.isEmpty();
            long fnv = Long.parseUnsignedLong(f[0], 16);
            assertEquals(fnv, Hash64.fnv1a64(s), "fnv1a64(\"" + s + "\")");
            assertEquals(Long.parseUnsignedLong(f[1], 16), Hash64.of(fnv, i), "Hash64.of row " + i);
        }
        assertTrue(sawCyrillic && sawEmpty, "golden set must contain Cyrillic and the empty string");
    }

    @Test
    void hash64IsOrderAndLengthSensitive() {
        assertNotEquals(Hash64.of(1, 2), Hash64.of(2, 1));
        assertNotEquals(Hash64.of(0), Hash64.of(0, 0));
        assertNotEquals(Hash64.of(), Hash64.of(0));
        assertEquals(Hash64.of(7, 8, 9), Hash64.of(7, 8, 9));
    }

    @Test
    void gaussianMeanAndVarianceOnMillionSamples() {
        Gaussian g = new Gaussian(new SplitMix64(20261101L));
        int n = 1_000_000;
        double sum = 0;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double x = g.next();
            sum += x;
            sumSq += x * x;
        }
        double mean = sum / n;
        double var = sumSq / n - mean * mean;
        assertTrue(Math.abs(mean) < 0.005, "mean " + mean);
        assertTrue(Math.abs(var - 1) < 0.01, "variance " + var);
    }

    @Test
    void nextIntIsInRangeAndRoughlyUniform() {
        SplitMix64 rng = new SplitMix64(1);
        int[] counts = new int[7];
        for (int i = 0; i < 70_000; i++) {
            counts[rng.nextInt(7)]++;
        }
        for (int c : counts) {
            assertTrue(Math.abs(c - 10_000) < 500, "bucket " + c);
        }
    }
}
