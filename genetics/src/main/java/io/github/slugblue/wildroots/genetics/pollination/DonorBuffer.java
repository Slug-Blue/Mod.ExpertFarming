package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import java.util.ArrayList;
import java.util.List;

/**
 * Donors of one plant: at most 4, the oldest is pushed out by the fifth
 * (GDD §4.1). Immutable: {@link #add} returns a new buffer.
 */
public final class DonorBuffer {

    public static final DonorBuffer EMPTY = new DonorBuffer(List.of());

    private final List<Donor> donors;

    private DonorBuffer(List<Donor> donors) {
        this.donors = List.copyOf(donors);
    }

    /** Buffer from stored donors, oldest first; extra entries beyond 4 keep the newest. */
    public static DonorBuffer of(List<Donor> oldestFirst) {
        int from = Math.max(0, oldestFirst.size() - Defaults.MAX_DONORS);
        return new DonorBuffer(oldestFirst.subList(from, oldestFirst.size()));
    }

    public DonorBuffer add(Donor d) {
        List<Donor> next = new ArrayList<>(donors);
        next.add(d);
        if (next.size() > Defaults.MAX_DONORS) {
            next.remove(0);
        }
        return new DonorBuffer(next);
    }

    /** Donors, oldest first. */
    public List<Donor> donors() {
        return donors;
    }

    public int size() {
        return donors.size();
    }
}
