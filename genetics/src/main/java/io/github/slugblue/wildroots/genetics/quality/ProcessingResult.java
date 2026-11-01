package io.github.slugblue.wildroots.genetics.quality;

/**
 * Grade of a processed product (GDD §6.6).
 *
 * @param grade grade of the result
 * @param raw   {@code g_raw}, the floor of the weighted mean
 * @param cap   cap by the lowest tier of the main ingredients
 */
public record ProcessingResult(int grade, int raw, int cap) {

    /**
     * Output count of a technical product by grade (GDD §6.6 table, e.g.
     * paper {@code [1, 2, 3, 3]}): {@code outputByGrade[grade − 1]}.
     */
    public int output(int[] outputByGrade) {
        if (outputByGrade == null || outputByGrade.length < 4) {
            return 0;
        }
        return Math.max(0, outputByGrade[Math.max(1, Math.min(4, grade)) - 1]);
    }
}
