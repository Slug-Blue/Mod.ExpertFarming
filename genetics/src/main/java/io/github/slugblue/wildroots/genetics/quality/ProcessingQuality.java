package io.github.slugblue.wildroots.genetics.quality;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import java.util.List;

/**
 * Quality inherited from the raw material (GDD §6.6):
 * <pre>
 * g_raw = floor(weighted mean grade of the main ingredients)   // bitter input = 1
 * cap   = by the lowest tier of the main ingredients: wild 1 · landrace 3 · cultivar 4
 * g_out = min(cap, g_raw) + qualifying boosters (+1 each), at most cap + 1 and at most 4
 * </pre>
 * No main ingredients: grade 3, like an item without the field.
 */
public final class ProcessingQuality {

    /** Grade of an item without a grade field (GDD §6.6). */
    public static final int NEUTRAL_GRADE = 3;

    private ProcessingQuality() {
    }

    public static ProcessingResult process(List<GradedInput> mains, List<Booster> boosters) {
        long weighted = 0;
        long count = 0;
        int minTier = Integer.MAX_VALUE;
        for (GradedInput in : mains) {
            if (in == null || in.count() <= 0) {
                continue;
            }
            int g = in.bitter() ? 1 : Math.max(1, Math.min(Defaults.MAX_GRADE, in.grade()));
            weighted += (long) g * in.count();
            count += in.count();
            minTier = Math.min(minTier, Math.max(0, Math.min(2, in.tier())));
        }
        if (count == 0) {
            return new ProcessingResult(NEUTRAL_GRADE, NEUTRAL_GRADE, Defaults.MAX_GRADE);
        }
        int raw = (int) (weighted / count); // floor of a positive mean
        int cap = Defaults.processingCap(minTier);
        int bonus = 0;
        for (Booster b : boosters) {
            if (b != null && b.qualifies()) {
                bonus++;
            }
        }
        int out = Math.min(cap, raw) + bonus;
        out = Math.min(out, Math.min(cap + 1, Defaults.MAX_GRADE));
        return new ProcessingResult(out, raw, cap);
    }
}
