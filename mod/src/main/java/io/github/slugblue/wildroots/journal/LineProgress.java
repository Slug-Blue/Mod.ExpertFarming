package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.genetics.random.Hash64;

/**
 * Per-line rules of the journal (narrative §4.1, §4.5; GDD §14), pure and
 * stored in the journal counters:
 *
 * <ul>
 *   <li>{@link #observe}: the best score of each generation of a named
 *   line; three generations in a row without a gain of at least
 *   {@value #MIN_GAIN} over the best so far is a stall ({@code stagnation});</li>
 *   <li>{@link #mayFire}: a per-line hint fires once for the line and
 *   again no sooner than {@value JournalHints#REPEAT_GENERATIONS} generations
 *   later.</li>
 * </ul>
 * The score is what the caller measures (the mean {@code G_eff} of the
 * target profile's traits); the line is a player name, so counters use a
 * hash of it ({@link #lineKey}).
 */
public final class LineProgress {

    public static final double MIN_GAIN = 0.01;
    public static final int STALL_GENERATIONS = 3;
    static final long SCALE = 10_000;
    static final long NONE = Long.MIN_VALUE / 2;

    private LineProgress() {
    }

    /** Counter-safe key of a line name: {@code l} and 16 hex digits. */
    public static String lineKey(String lineName) {
        return "l" + String.format("%016x", Hash64.str(lineName));
    }

    /**
     * Records {@code score} of a plant of generation {@code generation} of
     * the line; returns {@code true} when this observation closes the third
     * stalled generation in a row (the stall count restarts then).
     * Observations of older generations are ignored.
     */
    public static boolean observe(JournalData j, String lineKey, int generation, double score) {
        String p = "lp." + lineKey + ".";
        long s = Math.round(score * SCALE);
        if (j.counter(p + "n") == 0) {
            // n: generation + 1 of the open generation (0 = no state yet)
            set(j, p + "n", generation + 1L);
            set(j, p + "b", s);
            set(j, p + "r", NONE);
            set(j, p + "s", 0);
            return false;
        }
        long open = j.counter(p + "n") - 1;
        if (generation < open) {
            return false;
        }
        if (generation == open) {
            set(j, p + "b", Math.max(get(j, p + "b"), s));
            return false;
        }
        long best = get(j, p + "b");
        long ref = get(j, p + "r");
        long stall = get(j, p + "s");
        if (ref == NONE || best >= ref + Math.round(MIN_GAIN * SCALE)) {
            ref = Math.max(ref, best);
            stall = 0;
        } else {
            stall++;
        }
        boolean stalled = stall >= STALL_GENERATIONS;
        if (stalled) {
            stall = 0;
        }
        set(j, p + "n", generation + 1L);
        set(j, p + "b", s);
        set(j, p + "r", ref);
        set(j, p + "s", stall);
        return stalled;
    }

    /**
     * Whether a per-line hint may fire for the line at {@code generation};
     * if so, the generation is remembered.
     */
    public static boolean mayFire(JournalData j, String hint, String lineKey, int generation) {
        String k = "pl." + hint + "." + lineKey;
        long last = j.counter(k) - 1; // stored as generation + 1
        if (j.counter(k) != 0 && generation - last < JournalHints.REPEAT_GENERATIONS) {
            return false;
        }
        return j.setCounter(k, generation + 1L);
    }

    private static void set(JournalData j, String key, long v) {
        // 0 removes a counter; the stall count and the generation marker can be 0 legitimately
        j.setCounter(key, v == 0 ? Long.MIN_VALUE : v);
    }

    /** Reads a counter written by {@link #set}. */
    static long get(JournalData j, String key) {
        long v = j.counter(key);
        return v == Long.MIN_VALUE ? 0 : v;
    }
}
