package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.ArrayList;
import java.util.List;

/** WR-713: qualitative server reading, four bytes on the wire; no genome or exact tolerance. */
public record LensClimate(int bits) {
    public static final LensClimate NONE = new LensClimate(0);
    private static final int PRESENT = 1 << 20;
    private static final String PREFIX = "wildroots.lens.climate.";
    private static final List<String> EXEMPTIONS = List.of("none", "config", "origin", "mother", "stage",
            "dimension", "starter", "old_save");

    public LensClimate {
        if (bits != 0 && ((bits & ~0x1fffff) != 0 || (bits & PRESENT) == 0
                || ((bits >> 3) & 3) > 2 || ((bits >> 5) & 15) >= EXEMPTIONS.size())) {
            throw new IllegalArgumentException("invalid lens climate");
        }
    }

    public static LensClimate of(double stress, double threshold, boolean hot, String exemption, int minutes,
                                  boolean wilted, boolean sheltered, boolean irrigated) {
        if (!Double.isFinite(stress) || !Double.isFinite(threshold) || threshold <= 0) {
            throw new IllegalArgumentException("invalid climate stress");
        }
        int severity = stress <= 0 ? 0 : stress >= threshold ? 3 : stress < 0.2 ? 1 : 2;
        double room = threshold - stress;
        int reserve = room <= 0 ? 0 : room < 0.2 ? 1 : 2;
        int exempt = exemption == null ? 0 : EXEMPTIONS.indexOf(exemption);
        if (exempt < 0) {
            throw new IllegalArgumentException("unknown climate exemption");
        }
        return new LensClimate(PRESENT | (hot ? 1 : 0) | severity << 1 | reserve << 3 | exempt << 5
                | (wilted ? 1 << 9 : 0) | (sheltered ? 1 << 10 : 0) | (irrigated ? 1 << 11 : 0)
                | Math.max(0, Math.min(255, minutes)) << 12);
    }

    public List<DispCodec.Line> lines() {
        if (bits == 0) {
            return List.of();
        }
        List<DispCodec.Line> out = new ArrayList<>();
        String direction = (bits & 1) != 0 ? "heat" : "cold";
        int severity = (bits >> 1) & 3;
        int exemption = (bits >> 5) & 15;
        boolean wilted = (bits & (1 << 9)) != 0 && exemption == 0;
        String status = wilted ? "wilted" : severity == 0 ? "comfortable" : direction + "." + severity;
        out.add(DispCodec.Line.of(PREFIX + status));
        out.add(DispCodec.Line.of(PREFIX + "reserve." + direction + "." + ((bits >> 3) & 3)));
        if (exemption != 0) {
            out.add(DispCodec.Line.of(PREFIX + "exempt." + EXEMPTIONS.get(exemption)));
        } else if (!wilted && severity == 3) {
            int minutes = (bits >> 12) & 255;
            out.add(DispCodec.Line.of(PREFIX + (minutes == 0 ? "paused" : "time"), Integer.toString(minutes)));
        }
        if ((bits & ((1 << 10) | (1 << 11))) != 0) {
            String protection = (bits & (1 << 10)) == 0 ? "water" : (bits & (1 << 11)) == 0 ? "shelter" : "both";
            out.add(DispCodec.Line.of(PREFIX + "protection." + protection));
        }
        return List.copyOf(out);
    }
}
