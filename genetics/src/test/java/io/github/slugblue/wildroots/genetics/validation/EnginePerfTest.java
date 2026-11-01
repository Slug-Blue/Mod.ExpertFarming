package io.github.slugblue.wildroots.genetics.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.codec.GenomeCodec;
import io.github.slugblue.wildroots.genetics.codec.GenomeMigrator;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.quality.Booster;
import io.github.slugblue.wildroots.genetics.quality.GradedInput;
import io.github.slugblue.wildroots.genetics.quality.ProcessingQuality;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SEC-30: one engine call takes at most 50 µs at p99 on a species at the
 * DataLimits bounds (512 bits, 64 pairs, 64 candidates). Wall-clock timing is
 * noisy on shared CI runners, so the test is tagged {@code perf}, excluded
 * from {@code build} and run with {@code ./gradlew :genetics:perfTest}.
 */
@Tag("perf")
class EnginePerfTest {

    static final long LIMIT_NANOS = 50_000;

    static long p99(LongConsumer op, int warmup, int runs) {
        for (int i = 0; i < warmup; i++) {
            op.accept(i);
        }
        long[] t = new long[runs];
        for (int i = 0; i < runs; i++) {
            long a = System.nanoTime();
            op.accept(warmup + i);
            t[i] = System.nanoTime() - a;
        }
        Arrays.sort(t);
        return t[(int) Math.floor(0.99 * (runs - 1))];
    }

    @Test
    void p99OfEveryEngineCallIsAtMost50Microseconds() {
        SpeciesDef s = EngineTotalityPropertyTest.boundarySpecies(32, 0);
        CompiledSpecies cs = new CompiledSpecies(s);
        GenomeLayout l = cs.layout();
        SplitMix64 rng = new SplitMix64(30);
        Genome[] pool = new Genome[256];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = RandomGenomes.uniform(s.id(), l, rng);
        }
        List<CultivarDef> profiles = List.of(EngineTotalityPropertyTest.randomCultivar(rng, s, "a"),
                EngineTotalityPropertyTest.randomCultivar(rng, s, "b"));
        List<Candidate> nearby = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            nearby.add(new Candidate(pool[i % pool.length], i % 33 - 16, 0, i / 33 - 3));
        }
        NoiseVector eps = PhenotypeCalculator.rollNoise(cs, 1);
        Environment env = Environment.neutral(0.5);
        List<QuantTrait> edited = new ArrayList<>(s.quantitative());
        edited.remove(edited.size() - 1);
        SpeciesDef next = new SpeciesDef(s.id(), s.tempMin(), s.tempMax(), edited, List.of(), s.budget(), s.wild(),
                s.wildF(), s.qualitative(), s.starterFreq(), s.load(), s.mutation(), s.pollination(), s.timing(),
                s.qualityAxes());
        GenomeLayout nl = GenomeLayout.of(next);
        LayoutDictionary dict = new LayoutDictionary();
        dict.register(s.id(), l);
        dict.register(s.id(), nl);
        GenomeRecord[] records = new GenomeRecord[pool.length];
        for (int i = 0; i < pool.length; i++) {
            records[i] = GenomeCodec.encode(pool[i]);
        }
        List<GradedInput> mains = List.of(new GradedInput(4, 2, 3, false), new GradedInput(2, 1, 2, true));
        List<Booster> boosters = List.of(new Booster(true), new Booster(false));

        Map<String, LongConsumer> ops = new LinkedHashMap<>();
        ops.put("phenotype", i -> PhenotypeCalculator.compute(cs, pool[(int) (i & 255)], env, eps));
        ops.put("offspring", i -> Offspring.of(new LotRecord(pool[(int) (i & 255)],
                FatherSelector.choose(s, pool[0], nearby.subList(0, 8), List.of(), i).fathers(), 0.6),
                s.mutation(), i));
        ops.put("classify", i -> CultivarClassifier.classify(cs, pool[(int) (i & 255)], profiles, Optional.empty()));
        ops.put("chooseFathers", i -> FatherSelector.choose(s, pool[(int) (i & 255)], nearby, List.of(), i));
        ops.put("process", i -> ProcessingQuality.process(mains, boosters));
        ops.put("decode", i -> GenomeCodec.decode(records[(int) (i & 255)], s.id(), l));
        ops.put("migrate", i -> GenomeMigrator.migrate(records[(int) (i & 255)], next, nl, dict, i));
        StringBuilder report = new StringBuilder();
        List<String> slow = new ArrayList<>();
        for (Map.Entry<String, LongConsumer> e : ops.entrySet()) {
            long p = p99(e.getValue(), 20_000, 20_000);
            report.append(e.getKey()).append(" p99=").append(p / 1000.0).append(" us; ");
            if (p > LIMIT_NANOS) {
                slow.add(e.getKey());
            }
        }
        System.out.println("[SEC-30] " + report);
        assertTrue(slow.isEmpty(), "p99 over 50 us: " + slow + " — " + report);
    }
}
