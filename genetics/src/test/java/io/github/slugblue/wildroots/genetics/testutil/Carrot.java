package io.github.slugblue.wildroots.genetics.testutil;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.List;

/** Carrot line fixtures: species JSON of GDD §3.9, cultivars of GDD §6.3 and §7.3. */
public final class Carrot {

    public static final SpeciesDef SPECIES = DefJson.species("/species/wild_carrot.json");
    public static final GenomeLayout LAYOUT = GenomeLayout.of(SPECIES);
    public static final CultivarDef EASTERN = DefJson.cultivar("/cultivars/eastern_carrot.json");
    public static final CultivarDef CARROT = DefJson.cultivar("/cultivars/carrot.json");
    public static final List<CultivarDef> PROFILES = List.of(EASTERN, CARROT);

    public static final TraitId YIELD = TraitId.of("yield");
    public static final TraitId FRUIT = TraitId.of("fruit_size");
    public static final TraitId GROWTH = TraitId.of("growth_speed");
    public static final TraitId FLAVOR = TraitId.of("flavor");
    public static final TraitId COLD = TraitId.of("cold_tolerance");
    public static final TraitId HEAT = TraitId.of("heat_tolerance");
    public static final TraitId DROUGHT = TraitId.of("drought_tolerance");
    public static final TraitId BITTER = TraitId.of("bitterness");
    public static final TraitId Y1 = TraitId.of("color_y1");
    public static final TraitId Y2 = TraitId.of("color_y2");
    public static final TraitId P = TraitId.of("color_p");
    public static final TraitId DORMANCY = TraitId.of("dormancy");

    private Carrot() {
    }
}
