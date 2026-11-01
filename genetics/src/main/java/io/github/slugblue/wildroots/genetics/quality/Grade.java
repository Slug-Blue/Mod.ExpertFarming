package io.github.slugblue.wildroots.genetics.quality;

import io.github.slugblue.wildroots.genetics.api.Defaults;

/**
 * Grade 1–4 of a harvest (GDD §6.4): {@code Q} below 0.35 → 1, below 0.55
 * → 2, below 0.75 → 3, otherwise 4; a bitter harvest is never above 2.
 */
public final class Grade {

    private Grade() {
    }

    public static int of(double q, boolean bitter) {
        int g;
        if (!(q >= Defaults.GRADE_2_AT)) {
            g = 1; // also NaN
        } else if (q < Defaults.GRADE_3_AT) {
            g = 2;
        } else if (q < Defaults.GRADE_4_AT) {
            g = 3;
        } else {
            g = 4;
        }
        return bitter ? Math.min(g, Defaults.BITTER_MAX_GRADE) : g;
    }
}
