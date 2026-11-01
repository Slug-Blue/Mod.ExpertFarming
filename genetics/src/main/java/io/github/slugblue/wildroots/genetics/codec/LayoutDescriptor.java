package io.github.slugblue.wildroots.genetics.codec;

/**
 * One entry of the layout dictionary (docs/05-architecture.md §5.3):
 * species, 64-bit layout hash and the canonical string it was computed from.
 * Stored in the jar history ({@code data/wildroots/genome_layouts/}) and in
 * the world ({@code SavedData wildroots_layouts}).
 */
public record LayoutDescriptor(String species, long lh, String canonical) {
}
