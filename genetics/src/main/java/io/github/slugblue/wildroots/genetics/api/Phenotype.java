package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Phenotype of a plant (GDD §3.1, §3.10): per quantitative trait the
 * inherited value {@code G}, the realised genetic value {@code G_eff} and the
 * phenotype {@code P}; vitality {@code V}; per qualitative trait the visible
 * phenotype; the colour of the species' colour rules and its properties.
 * Immutable: arrays are copied in and out.
 */
public final class Phenotype {

    private final List<TraitId> traits;
    private final double[] g;
    private final double[] gEff;
    private final double[] p;
    private final double vigor;
    private final List<TraitId> qualTraits;
    private final boolean[] domestic;
    private final boolean bitter;
    private final int colour;
    private final ColourProps colourProps;

    /** Colour number of a species without colour rules or of a genotype no rule matches. */
    public static final int NO_COLOUR = -1;

    /** A phenotype without a colour. */
    public Phenotype(List<TraitId> traits, double[] g, double[] gEff, double[] p, double vigor,
                     List<TraitId> qualTraits, boolean[] domestic, boolean bitter) {
        this(traits, g, gEff, p, vigor, qualTraits, domestic, bitter, NO_COLOUR, ColourProps.ZERO);
    }

    public Phenotype(List<TraitId> traits, double[] g, double[] gEff, double[] p, double vigor,
                     List<TraitId> qualTraits, boolean[] domestic, boolean bitter, int colour,
                     ColourProps colourProps) {
        this.traits = List.copyOf(traits);
        this.g = g.clone();
        this.gEff = gEff.clone();
        this.p = p.clone();
        this.vigor = vigor;
        this.qualTraits = List.copyOf(qualTraits);
        this.domestic = domestic.clone();
        this.bitter = bitter;
        this.colour = colour;
        this.colourProps = colourProps == null ? ColourProps.ZERO : colourProps;
        if (g.length != this.traits.size() || gEff.length != g.length || p.length != g.length
                || domestic.length != this.qualTraits.size()) {
            throw new IllegalArgumentException("array sizes do not match the trait lists");
        }
    }

    /** Quantitative traits in layout order. */
    public List<TraitId> traits() {
        return traits;
    }

    public List<TraitId> qualitativeTraits() {
        return qualTraits;
    }

    private int index(TraitId t) {
        int i = traits.indexOf(t);
        if (i < 0) {
            throw new IllegalArgumentException("no quantitative trait " + t);
        }
        return i;
    }

    public double g(TraitId t) {
        return g[index(t)];
    }

    public double gEff(TraitId t) {
        return gEff[index(t)];
    }

    public double p(TraitId t) {
        return p[index(t)];
    }

    /** {@code P} or {@code fallback} if the species has no such trait. */
    public double pOr(TraitId t, double fallback) {
        int i = traits.indexOf(t);
        return i < 0 ? fallback : p[i];
    }

    public double[] gArray() {
        return g.clone();
    }

    public double[] gEffArray() {
        return gEff.clone();
    }

    public double[] pArray() {
        return p.clone();
    }

    public double vigor() {
        return vigor;
    }

    /** Visible phenotype of a biallelic qualitative trait: {@code true} = domestic. */
    public boolean domestic(TraitId t) {
        int i = qualTraits.indexOf(t);
        if (i < 0) {
            throw new IllegalArgumentException("no qualitative trait " + t);
        }
        return domestic[i];
    }

    /** Bitter harvest (GDD §3.6, §6.4): the {@code bitterness} trait shows the wild phenotype. */
    public boolean bitter() {
        return bitter;
    }

    /**
     * Colour number by the species' colour rules (the position in {@code colours.ids}, for the
     * carrot 0 white, 1 yellow, 2 orange, 3 purple, 4 red), or {@link #NO_COLOUR}.
     */
    public int colour() {
        return colour;
    }

    /** Properties of the colour; {@link ColourProps#ZERO} without a colour. */
    public ColourProps colourProps() {
        return colourProps;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Phenotype x && x.traits.equals(traits) && Arrays.equals(x.g, g)
                && Arrays.equals(x.gEff, gEff) && Arrays.equals(x.p, p) && x.vigor == vigor
                && x.qualTraits.equals(qualTraits) && Arrays.equals(x.domestic, domestic) && x.bitter == bitter
                && x.colour == colour && x.colourProps.equals(colourProps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traits, Arrays.hashCode(p), vigor, bitter);
    }

    @Override
    public String toString() {
        return "Phenotype[" + traits + " P=" + Arrays.toString(p) + " V=" + vigor + " bitter=" + bitter + " colour=" + colour + "]";
    }
}
