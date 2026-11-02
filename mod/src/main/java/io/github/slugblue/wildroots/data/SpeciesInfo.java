package io.github.slugblue.wildroots.data;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/**
 * Mod-side data of a species that the engine never sees
 * (docs/05-architecture.md §7.1, §8.3): block or archetype, tint colour,
 * life cycle, display names of traits, rare allele, propagation. Part of it
 * goes to clients in {@code DefinitionsSyncPacket}.
 *
 * @param id            species id
 * @param archetype     plant archetype ({@code field}, {@code tall}, …)
 * @param block         own block of a built-in species, if any
 * @param color         tint of archetype blocks (0xRRGGBB)
 * @param lifeCycle     {@code annual}, {@code biennial}, {@code perennial}
 * @param displayTraits trait id → display name id (narrative §9.2)
 * @param rareAllele    allele that marks a rare find
 * @param propagation   {@code seed}, {@code clone}, …
 */
public record SpeciesInfo(ResourceLocation id, String archetype, Optional<ResourceLocation> block, OptionalInt color,
                          String lifeCycle, Map<String, String> displayTraits, Optional<String> rareAllele,
                          String propagation) {

    /** Archetypes of docs/05-architecture.md §7.1. */
    public static final java.util.List<String> ARCHETYPES =
            java.util.List.of("field", "tall", "water", "vine", "cane", "trunk_pod", "bush", "tree");

    public SpeciesInfo {
        displayTraits = Map.copyOf(displayTraits);
    }

    /** Translation key of the species name (narrative §2): {@code wildroots.species.<path>}, namespaced outside {@code wildroots}. */
    public String translationKey() {
        return "wildroots.species." + (id.getNamespace().equals("wildroots") ? id.getPath()
                : id.getNamespace() + "." + id.getPath());
    }
}
