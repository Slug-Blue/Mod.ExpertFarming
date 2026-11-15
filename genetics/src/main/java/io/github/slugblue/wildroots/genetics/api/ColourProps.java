package io.github.slugblue.wildroots.genetics.api;

/**
 * Small properties of one colour, on top of the traits and outside
 * {@code G_eff} (docs/design/carrot-colours-climate.md §2): the limits of
 * the temperature window move by {@code heat} (upper) and {@code cold}
 * (lower; positive widens the window downwards), {@code drought} is added to
 * {@code P_drought} on dry farmland (clamped to 1), {@code saturationMult}
 * multiplies the saturation of the food. Every number is a
 * {@code [PLACEHOLDER]} of the balance pass and comes from the species JSON.
 *
 * @param heat           shift of the upper window limit
 * @param cold           widening of the lower window limit
 * @param drought        addition to {@code P_drought} on dry farmland
 * @param saturationMult food saturation multiplier, 1 = none
 */
public record ColourProps(double heat, double cold, double drought, double saturationMult) {

    /** No effect at all: a plant without a colour rule, or a colour without properties. */
    public static final ColourProps ZERO = new ColourProps(0, 0, 0, 1);

    /** Whether every modifier is neutral. */
    public boolean isZero() {
        return heat == 0 && cold == 0 && drought == 0 && saturationMult == 1;
    }
}
