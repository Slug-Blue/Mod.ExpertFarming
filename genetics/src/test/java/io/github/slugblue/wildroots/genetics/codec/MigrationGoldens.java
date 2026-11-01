package io.github.slugblue.wildroots.genetics.codec;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Writes the "old save" golden fixtures {@code src/test/resources/golden/v1/*.json}
 * (docs/05-architecture.md §5.4). Run once per released format or layout
 * change with {@code WILDROOTS_REGEN_GOLDEN=1}; the files are then frozen and
 * never regenerated for an already released version — {@link GenomeMigratorGoldenTest}
 * checks them bit by bit.
 */
class MigrationGoldens {

    /** name, description, old species (registered in the dictionary or null), current species, record version, seed. */
    record Case(String name, String description, String oldSpecies, boolean registered, String currentSpecies,
                int version, long seed) {
    }

    static final List<Case> CASES = List.of(
            new Case("carrot_unchanged", "current layout: decoded as is", "wild_carrot", true, "wild_carrot", 1, 1),
            new Case("carrot_yield_6_to_8", "yield 6 -> 8 loci: old alleles kept, new loci background",
                    "wild_carrot", true, "wild_carrot_yield8", 1, 2),
            new Case("carrot_yield_8_to_6", "yield 8 -> 6 loci: loci 6..7 dropped with a warning",
                    "wild_carrot_yield8", true, "wild_carrot", 1, 3),
            new Case("carrot_flavor_removed", "flavor removed: dropped with a warning", "wild_carrot", true,
                    "wild_carrot_noflavor", 1, 4),
            new Case("carrot_sugar_added", "new trait sugar: background with a warning", "wild_carrot", true,
                    "wild_carrot_sugar", 1, 5),
            new Case("carrot_dormancy_kind_change", "dormancy qualitative -> quantitative: background with a warning",
                    "wild_carrot", true, "wild_carrot_dormancy_quant", 1, 6),
            new Case("s_locus_6_to_16", "S-locus widened 6 -> 16 alleles: values kept", "wild_carrot_s6", true,
                    "wild_carrot_s16", 1, 7),
            new Case("s_locus_10_to_6", "S-locus narrowed 10 -> 6 alleles: values >= 6 replaced with a warning",
                    "wild_carrot_s10", true, "wild_carrot_s6", 1, 8),
            new Case("unknown_layout", "layout missing from the dictionary: regenerated as wild",
                    "wild_carrot_yield8", false, "wild_carrot", 1, 9),
            new Case("future_version", "format version 2 from a newer mod: inert, raw kept", "wild_carrot", true,
                    "wild_carrot", 2, 10));

    static SpeciesDef species(String name) {
        return DefJson.species("/species/" + name + ".json");
    }

    /** The stored record of a case: a random genome in the old layout. */
    static GenomeRecord record(Case c) {
        SpeciesDef old = species(c.oldSpecies());
        GenomeLayout l = GenomeLayout.of(old);
        SplitMix64 rng = new SplitMix64(c.seed());
        // the S-locus of a 10-allele species uses all values, including those ≥ 6
        Genome g = RandomGenomes.uniform(old.id(), l, rng);
        return new GenomeRecord(c.version(), old.id(), l.hash(), g.bits());
    }

    static LayoutDictionary dictionary(Case c) {
        LayoutDictionary d = new LayoutDictionary();
        if (c.registered()) {
            d.register(species(c.oldSpecies()).id(), GenomeLayout.of(species(c.oldSpecies())));
        }
        SpeciesDef cur = species(c.currentSpecies());
        d.register(cur.id(), GenomeLayout.of(cur));
        return d;
    }

    static GenomeMigrator.Result run(Case c, GenomeRecord r) {
        SpeciesDef cur = species(c.currentSpecies());
        return GenomeMigrator.migrate(r, cur, GenomeLayout.of(cur), dictionary(c));
    }

    static String hex(long v) {
        return "\"" + String.format("%016x", v) + "\"";
    }

    static String hexArray(long[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; a != null && i < a.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(hex(a[i]));
        }
        return sb.append("]").toString();
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WILDROOTS_REGEN_GOLDEN", matches = "1")
    void writeGoldenV1() throws IOException {
        Path dir = Paths.get("src", "test", "resources", "golden", "v1");
        Files.createDirectories(dir);
        for (Case c : CASES) {
            GenomeRecord r = record(c);
            GenomeMigrator.Result res = run(c, r);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"description\": ").append(quote(c.description())).append(",\n");
            sb.append("  \"old_species\": ").append(quote(c.oldSpecies())).append(",\n");
            sb.append("  \"old_layout_registered\": ").append(c.registered()).append(",\n");
            sb.append("  \"current_species\": ").append(quote(c.currentSpecies())).append(",\n");
            sb.append("  \"record\": { \"v\": ").append(r.v()).append(", \"sp\": ").append(quote(r.species()))
                    .append(", \"lh\": ").append(hex(r.lh())).append(", \"a\": ").append(hexArray(r.a()))
                    .append(" },\n");
            sb.append("  \"expected\": {\n");
            sb.append("    \"status\": ").append(quote(res.status().name())).append(",\n");
            sb.append("    \"lh\": ").append(res.genome().map(g -> hex(g.layout().hash())).orElse("null"))
                    .append(",\n");
            sb.append("    \"a\": ").append(res.genome().map(g -> hexArray(g.bits())).orElse("null")).append(",\n");
            sb.append("    \"warnings\": [");
            for (int i = 0; i < res.warnings().size(); i++) {
                sb.append(i == 0 ? "\n      " : ",\n      ").append(quote(res.warnings().get(i)));
            }
            sb.append(res.warnings().isEmpty() ? "]\n" : "\n    ]\n");
            sb.append("  }\n}\n");
            Files.writeString(dir.resolve(c.name() + ".json"), sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
