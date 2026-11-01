package io.github.slugblue.wildroots.genetics.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * WR-111: "old save loads" in the engine (docs/05-architecture.md §5.4 p. 1):
 * every frozen {@code golden/v1/*.json} record migrates bit for bit to the
 * stored result, with the stored warnings.
 */
class GenomeMigratorGoldenTest {

    static long hex(Object o) {
        return Long.parseUnsignedLong((String) o, 16);
    }

    static long[] hexArray(Object o) {
        if (o == null) {
            return null;
        }
        List<?> l = (List<?>) o;
        long[] a = new long[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = hex(l.get(i));
        }
        return a;
    }

    @TestFactory
    Stream<DynamicTest> goldenV1() {
        return MigrationGoldens.CASES.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> f = (Map<String, Object>) MiniJson.resource("/golden/v1/" + c.name() + ".json");
            @SuppressWarnings("unchecked")
            Map<String, Object> rec = (Map<String, Object>) f.get("record");
            @SuppressWarnings("unchecked")
            Map<String, Object> exp = (Map<String, Object>) f.get("expected");
            GenomeRecord r = new GenomeRecord(((Double) rec.get("v")).intValue(), (String) rec.get("sp"),
                    hex(rec.get("lh")), hexArray(rec.get("a")));
            // the frozen input is what the generator produced: guards against fixture drift
            assertEquals(MigrationGoldens.record(c), r, "fixture input");
            GenomeMigrator.Result res = MigrationGoldens.run(c, r);
            assertEquals(exp.get("status"), res.status().name());
            assertArrayEquals(hexArray(exp.get("a")), res.genome().map(Genome::bits).orElse(null));
            assertEquals(exp.get("warnings"), res.warnings());
            if (res.genome().isPresent()) {
                assertEquals(hex(exp.get("lh")), res.genome().get().layout().hash());
                assertKeptAlleles(c, r, res.genome().get());
            }
        }));
    }

    /** Every allele of a trait and locus present in both layouts, in range of the new one, is unchanged. */
    static void assertKeptAlleles(MigrationGoldens.Case c, GenomeRecord r, Genome migrated) {
        if (!c.registered()) {
            return;
        }
        SpeciesDef old = MigrationGoldens.species(c.oldSpecies());
        GenomeLayout ol = GenomeLayout.of(old);
        long[] raw = r.a();
        int kept = 0;
        for (Locus s : migrated.layout().sections()) {
            Locus o = ol.section(s.trait());
            if (o == null || o.kind() != s.kind()) {
                continue;
            }
            for (int l = 0; l < Math.min(s.loci(), o.loci()); l++) {
                for (int w = 0; w < 2; w++) {
                    int before = GenomeLayout.get(raw, o, l, w);
                    if (before < s.alleleCount()) {
                        assertEquals(before, migrated.allele(s, l, w), s.trait() + " " + l + "/" + w);
                        kept++;
                    }
                }
            }
        }
        assertTrue(kept > 0);
    }
}
