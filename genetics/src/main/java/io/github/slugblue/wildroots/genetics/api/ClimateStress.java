package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;

/**
 * Climate stress and the wilting threshold of a species (docs/design/carrot-colours-climate.md §3.1, §4;
 * WR-710). Pure arithmetic, no Minecraft: the mod reads the biome temperature, the sky light and the farmland
 * and hands them in.
 *
 * <pre>
 * T_eff  = T_mid + (T_cell + humid - T_mid) · (1 - buffer)     protection smooths the deviation from mid-window
 * S_hot  = T_eff - (tempMax + 0.6·P_heat + c_heat)             above 0: too hot
 * S_cold = (tempMin - 0.6·P_cold - c_cold) - T_eff             above 0: too cold
 * S      = max(S_hot, S_cold)                                  negative: margin to the nearest limit
 * wilts  ⇔  S ≥ wiltMargin                                     [PLACEHOLDER] W = 0.35
 * </pre>
 *
 * {@code E} takes {@code −min(0.30, max(0, S))} (GDD §3.5): the existing penalty, only with {@code T_eff}. By
 * construction {@code S < 0} for every {@code P ≥ 0} inside the temperature 0.5–0.8 of a species with the window
 * 0.2–1.0 and the colour modifiers of the carrot (the starter patch and the M1a gate do not move).
 *
 * <p>A species that does not declare {@code climate_stress} is {@link #NONE}: it never wilts and has no humid
 * penalty or buffer. Every number is a {@code [PLACEHOLDER]} of the balance pass (WR-715), read from the species
 * JSON; the defaults live in {@link Defaults}.
 *
 * @param wiltMargin       {@code W}: stress at which a plant wilts; 0 = the species never wilts
 * @param humidPenalty     added to the cell temperature in jungle biomes, in the stress calculation only
 * @param shelterBuffer    {@code buffer} of a sheltered cell (the sky is covered)
 * @param irrigationBuffer {@code buffer} of irrigated farmland
 * @param graceTicks       {@code K}: random ticks with {@code S ≥ W} before the plant wilts
 * @param shelterSkyLight  highest sky light of a sheltered cell
 */
public record ClimateStress(double wiltMargin, double humidPenalty, double shelterBuffer, double irrigationBuffer,
                            int graceTicks, int shelterSkyLight) {

    /** The species does not wilt. */
    public static final ClimateStress NONE = new ClimateStress(0, 0, 0, 0, 0, 0);

    /** The defaults of GDD/design §4.2 and §4.5 with a humid penalty of {@code humid}. */
    public static ClimateStress defaults(double humid) {
        return new ClimateStress(Defaults.WILT_MARGIN, humid, Defaults.SHELTER_BUFFER, Defaults.IRRIGATION_BUFFER,
                Defaults.WILT_GRACE_TICKS, Defaults.SHELTER_SKY_LIGHT);
    }

    /** Whether the species can wilt at all. */
    public boolean enabled() {
        return wiltMargin > 0 && graceTicks > 0;
    }

    /** Sum of the protections, at most 1. */
    public double buffer(boolean sheltered, boolean irrigated) {
        return Math.min(1, (sheltered ? shelterBuffer : 0) + (irrigated ? irrigationBuffer : 0));
    }

    /** Largest buffer there is: a plant that does not wilt with none cannot wilt with any. */
    public double maxBuffer() {
        return Math.min(1, shelterBuffer + irrigationBuffer);
    }

    /** Whether the stress {@code s} wilts the plant. */
    public boolean wilts(double s) {
        return enabled() && s >= wiltMargin;
    }

    // ------------------------------------------------------------------ the calculation

    /** {@code T_mid}: the middle of the species' temperature window. */
    public static double mid(SpeciesDef def) {
        return (def.tempMin() + def.tempMax()) / 2;
    }

    /** {@code T_eff}: the deviation of {@code T_cell + humid} from {@code mid} shrinks by {@code 1 − buffer}. */
    public static double effectiveTemperature(double mid, double cell, double humid, double buffer) {
        return mid + (cell + humid - mid) * (1 - buffer);
    }

    /** The cell temperature the stress sees: the biome's plus the humid penalty of a humid biome. */
    public double humid(boolean humidBiome) {
        return humidBiome ? humidPenalty : 0;
    }

    /** Upper limit of the effective window: {@code tempMax + 0.6·P_heat + c_heat}. */
    public static double hotLimit(SpeciesDef def, double heatTolerance, ColourProps colour) {
        return def.tempMax() + Defaults.TOLERANCE_WIDENING * heatTolerance + colour.heat();
    }

    /** Lower limit of the effective window: {@code tempMin − 0.6·P_cold − c_cold}. */
    public static double coldLimit(SpeciesDef def, double coldTolerance, ColourProps colour) {
        return def.tempMin() - Defaults.TOLERANCE_WIDENING * coldTolerance - colour.cold();
    }

    /**
     * The two limits of a plant, {@code {hot, cold}}. They depend only on genome, noise and colour (the tolerances
     * are not {@code vigor_affected}; a vigor-affected tolerance counts 0 as in {@code E}), so a plant computes
     * them once.
     */
    public static double[] limits(SpeciesDef def, Phenotype p) {
        ColourProps c = p.colourProps();
        return new double[] {hotLimit(def, tolerance(def, p, Traits.HEAT_TOLERANCE), c),
                coldLimit(def, tolerance(def, p, Traits.COLD_TOLERANCE), c)};
    }

    /** {@code P} of a tolerance as {@code E} sees it: 0 if the trait is vigor-affected or absent. */
    public static double tolerance(SpeciesDef def, Phenotype p, TraitId t) {
        for (QuantTrait q : def.quantitative()) {
            if (q.id().equals(t)) {
                return q.vigorAffected() ? 0 : p.pOr(t, 0);
            }
        }
        return 0;
    }

    /** {@code S} for the effective temperature {@code tEff} and the limits of a plant (see {@link #limits}). */
    public static double stress(double tEff, double hotLimit, double coldLimit) {
        return Math.max(tEff - hotLimit, coldLimit - tEff);
    }

    /** {@code S} of a plant in a cell, protection and humidity included. */
    public double stress(SpeciesDef def, double[] limits, double cellTemperature, boolean humidBiome, boolean sheltered,
                         boolean irrigated) {
        double t = effectiveTemperature(mid(def), cellTemperature, humid(humidBiome), buffer(sheltered, irrigated));
        return stress(t, limits[0], limits[1]);
    }

    // ------------------------------------------------------------------ minimum tolerance (JUnit, journal)

    /**
     * {@code P*}: the least {@code P_heat} that survives {@code tEff} (design §3.2). Above 1: impossible without
     * protection. The colour's heat modifier is {@code colourHeat}.
     */
    public double requiredHeatTolerance(SpeciesDef def, double tEff, double colourHeat) {
        return (tEff - def.tempMax() - colourHeat - wiltMargin) / Defaults.TOLERANCE_WIDENING;
    }

    /** {@code P*} for the cold: the least {@code P_cold} that survives {@code tEff}; {@code colourCold} widens downwards. */
    public double requiredColdTolerance(SpeciesDef def, double tEff, double colourCold) {
        return (def.tempMin() - colourCold - tEff - wiltMargin) / Defaults.TOLERANCE_WIDENING;
    }
}
