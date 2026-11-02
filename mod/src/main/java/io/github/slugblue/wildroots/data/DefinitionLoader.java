package io.github.slugblue.wildroots.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.slugblue.wildroots.data.codec.PathCodecs;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.validation.DefinitionValidator;
import io.github.slugblue.wildroots.genetics.validation.Problem;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Loads species and cultivar files into a {@link DefinitionsSnapshot}
 * (docs/05-architecture.md §8.2; ADR-0005; WR-125). No game state: the
 * reload listeners call it with the files of the resource manager, tests
 * call it with fixtures.
 *
 * <ol>
 *   <li>Each file: JSON → {@link PathCodecs} codec → defaults
 *       ({@code SpeciesSchema}, {@code CultivarSchema}). A structure or type
 *       error is an {@code ERROR} with its JSON path; the file is skipped.</li>
 *   <li>The whole set: {@code DefinitionValidator.validateSet} (meaning,
 *       counts, duplicate ids, reference graph). A rejected species takes its
 *       cultivars with it; each such cultivar gets its own line.</li>
 *   <li>Log lines {@code <file>: $.<path>: <message>} and the summary
 *       {@code Wild Roots: species N (k skipped), cultivars M (j skipped)}.</li>
 * </ol>
 *
 * <p>Every file is read inside {@code try/catch (Exception | StackOverflowError)}:
 * one bad file never stops the others (SEC-27, WR-126). Before the codec a
 * file over {@value #MAX_FILE_BYTES} bytes, an empty file and JSON nested
 * deeper than {@value #MAX_JSON_DEPTH} are errors; a UTF-8 byte order mark is
 * dropped.
 */
public final class DefinitionLoader {

    /** One parsed file. */
    public record Parsed<M>(ResourceLocation file, Optional<String> declaredId, Optional<M> mapped,
                            List<Problem> problems) {

        boolean usable() {
            return mapped.isPresent() && !DefinitionValidator.hasErrors(problems);
        }
    }

    /** One line for the log. */
    public record LogLine(boolean error, String text) {
    }

    /** Snapshot and log of one load. */
    public record LoadResult(DefinitionsSnapshot snapshot, List<LogLine> lines) {

        public String summary() {
            return snapshot.stats().summary();
        }
    }

    /** Largest definition file (SEC-27). */
    public static final int MAX_FILE_BYTES = 256 * 1024;
    /** Deepest JSON nesting of a definition file (SEC-27). */
    public static final int MAX_JSON_DEPTH = 32;
    /** U+FEFF, the byte order mark some editors write at the start of UTF-8 files. */
    private static final char BOM = (char) 0xFEFF;

    private DefinitionLoader() {
    }

    /**
     * Nesting depth of objects and arrays in {@code text}, counted without a
     * parser (no recursion, strings and escapes skipped); stops counting once
     * {@code limit} is passed. Gson parses recursively, so this runs first.
     */
    static int jsonDepth(String text, int limit) {
        int depth = 0;
        int max = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    depth++;
                    if (depth > max) {
                        max = depth;
                        if (max > limit) {
                            return max;
                        }
                    }
                }
                case '}', ']' -> depth = Math.max(0, depth - 1);
                default -> { }
            }
        }
        return max;
    }

    // ------------------------------------------------------------------ parsing

    public static List<Parsed<SpeciesSchema.Mapped>> parseSpecies(Map<ResourceLocation, IoSupplier<InputStream>> files) {
        return parse(files, SpeciesSchema.CODEC, SpeciesSchema::toDefinition, SpeciesSchema.Mapped::problems);
    }

    public static List<Parsed<LayoutHistorySchema.Mapped>> parseLayouts(Map<ResourceLocation, IoSupplier<InputStream>> files) {
        return parse(files, LayoutHistorySchema.CODEC, LayoutHistorySchema::toDescriptors,
                LayoutHistorySchema.Mapped::problems);
    }

    public static List<Parsed<CultivarSchema.Mapped>> parseCultivars(Map<ResourceLocation, IoSupplier<InputStream>> files) {
        return parse(files, CultivarSchema.CODEC, CultivarSchema::toDefinition, CultivarSchema.Mapped::problems);
    }

    private static <R, M> List<Parsed<M>> parse(Map<ResourceLocation, IoSupplier<InputStream>> files, Codec<R> codec,
                                                Function<R, M> mapper, Function<M, List<Problem>> problemsOf) {
        List<Parsed<M>> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> e : new TreeMap<>(files).entrySet()) {
            out.add(parseOne(e.getKey(), e.getValue(), codec, mapper, problemsOf));
        }
        return out;
    }

    static <R, M> Parsed<M> parseOne(ResourceLocation file, IoSupplier<InputStream> source, Codec<R> codec,
                                     Function<R, M> mapper, Function<M, List<Problem>> problemsOf) {
        List<Problem> problems = new ArrayList<>();
        Optional<String> declaredId = Optional.empty();
        try {
            byte[] bytes;
            try (InputStream in = source.get()) {
                bytes = in.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                problems.add(Problem.error("", "file larger than " + MAX_FILE_BYTES / 1024 + " KiB"));
                return new Parsed<>(file, declaredId, Optional.empty(), problems);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == BOM) {
                text = text.substring(1); // UTF-8 byte order mark
            }
            if (text.isBlank()) {
                problems.add(Problem.error("", "empty file"));
                return new Parsed<>(file, declaredId, Optional.empty(), problems);
            }
            int depth = jsonDepth(text, MAX_JSON_DEPTH);
            if (depth > MAX_JSON_DEPTH) {
                problems.add(Problem.error("", "JSON nesting deeper than " + MAX_JSON_DEPTH));
                return new Parsed<>(file, declaredId, Optional.empty(), problems);
            }
            JsonElement json = JsonParser.parseString(text);
            JsonElement id = json.isJsonObject() ? json.getAsJsonObject().get("id") : null;
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                declaredId = Optional.of(id.getAsString());
            }
            DataResult<R> r = codec.parse(JsonOps.INSTANCE, json);
            if (r.error().isPresent()) {
                for (PathCodecs.PathError pe : PathCodecs.errors(r.error().get().message())) {
                    problems.add(Problem.error(pe.path(), pe.message()));
                }
                return new Parsed<>(file, declaredId, Optional.empty(), problems);
            }
            M mapped = mapper.apply(r.result().orElseThrow());
            problems.addAll(problemsOf.apply(mapped));
            return new Parsed<>(file, declaredId, Optional.of(mapped), problems);
        } catch (Exception | StackOverflowError ex) {
            problems.add(Problem.error("", "cannot read: " + ex));
            return new Parsed<>(file, declaredId, Optional.empty(), problems);
        }
    }

    // ------------------------------------------------------------------ assembly

    public static LoadResult load(Map<ResourceLocation, IoSupplier<InputStream>> species,
                                  Map<ResourceLocation, IoSupplier<InputStream>> cultivars) {
        return assemble(parseSpecies(species), parseCultivars(cultivars));
    }

    /** {@link #assemble(List, List, List)} without layout history. */
    public static LoadResult assemble(List<Parsed<SpeciesSchema.Mapped>> species,
                                      List<Parsed<CultivarSchema.Mapped>> cultivars) {
        return assemble(species, List.of(), cultivars);
    }

    /**
     * Validates the set, resolves references, fills the layout dictionary
     * (history first, then the current layouts: a current layout whose hash
     * matches another canonical string of the same species rejects the
     * species, architecture §5.1) and builds the snapshot and its log.
     */
    public static LoadResult assemble(List<Parsed<SpeciesSchema.Mapped>> species,
                                      List<Parsed<LayoutHistorySchema.Mapped>> layouts,
                                      List<Parsed<CultivarSchema.Mapped>> cultivars) {
        Map<ResourceLocation, List<Problem>> byFile = new LinkedHashMap<>();
        LayoutDictionary dictionary = new LayoutDictionary();
        for (Parsed<LayoutHistorySchema.Mapped> p : layouts) {
            List<Problem> problems = byFile.computeIfAbsent(p.file(), k -> new ArrayList<>());
            problems.addAll(p.problems());
            p.mapped().ifPresent(m -> m.layouts().forEach(d -> dictionary.register(d)
                    .ifPresent(problem -> problems.add(Problem.error("history", problem.message())))));
        }
        List<String> dependents = new ArrayList<>();
        Map<String, Parsed<SpeciesSchema.Mapped>> speciesById = new TreeMap<>();
        Set<String> rejectedSpecies = new HashSet<>();
        for (Parsed<SpeciesSchema.Mapped> p : species) {
            byFile.computeIfAbsent(p.file(), k -> new ArrayList<>()).addAll(p.problems());
            if (!p.usable()) {
                p.declaredId().ifPresent(rejectedSpecies::add);
                continue;
            }
            String id = p.mapped().get().def().id();
            if (speciesById.containsKey(id)) {
                byFile.get(p.file()).add(Problem.error("id", "duplicate species id " + id + ", first in "
                        + speciesById.get(id).file()));
                continue;
            }
            speciesById.put(id, p);
        }
        Map<String, Parsed<CultivarSchema.Mapped>> cultivarsById = new TreeMap<>();
        for (Parsed<CultivarSchema.Mapped> p : cultivars) {
            byFile.computeIfAbsent(p.file(), k -> new ArrayList<>()).addAll(p.problems());
            if (!p.usable()) {
                continue;
            }
            String id = p.mapped().get().def().id().value();
            if (cultivarsById.containsKey(id)) {
                byFile.get(p.file()).add(Problem.error("id", "duplicate cultivar id " + id + ", first in "
                        + cultivarsById.get(id).file()));
                continue;
            }
            cultivarsById.put(id, p);
        }

        List<SpeciesDef> sDefs = speciesById.values().stream().map(p -> p.mapped().get().def()).toList();
        List<CultivarDef> cDefs = cultivarsById.values().stream().map(p -> p.mapped().get().def()).toList();
        DefinitionValidator.SetResult set = DefinitionValidator.validateSet(sDefs, cDefs, List.of());
        for (Problem p : set.problems()) {
            String key = p.path();
            int slash = key.indexOf('/');
            String record = slash < 0 ? key : key.substring(0, slash);
            String rest = slash < 0 ? "" : key.substring(slash + 1);
            ResourceLocation file = fileOf(record, speciesById, cultivarsById);
            if (file == null) {
                continue;
            }
            if (record.startsWith("cultivar:") && rest.isEmpty() && dependentMessage(p.message()) != null) {
                String target = dependentMessage(p.message());
                dependents.add(file + ": skipped: " + (rejectedSpecies.contains(target) || speciesById.containsKey(target)
                        ? "depends on rejected species " : "unknown species ") + target);
                continue;
            }
            byFile.computeIfAbsent(file, k -> new ArrayList<>()).add(new Problem(p.severity(), rest, p.message()));
        }

        Map<String, DefinitionsSnapshot.SpeciesEntry> okS = new LinkedHashMap<>();
        for (SpeciesDef d : set.species()) {
            Parsed<SpeciesSchema.Mapped> p = speciesById.get(d.id());
            Optional<Problem> collision = dictionary.register(d.id(), GenomeLayout.of(d));
            if (collision.isPresent()) {
                byFile.computeIfAbsent(p.file(), k -> new ArrayList<>())
                        .add(Problem.error("layout", collision.get().message()));
                continue;
            }
            okS.put(d.id(), new DefinitionsSnapshot.SpeciesEntry(d, p.mapped().get().info(), p.file()));
        }
        Map<String, DefinitionsSnapshot.CultivarEntry> okC = new LinkedHashMap<>();
        for (CultivarDef d : set.cultivars()) {
            if (!okS.containsKey(d.species())) {
                Parsed<CultivarSchema.Mapped> p = cultivarsById.get(d.id().value());
                dependents.add(p.file() + ": skipped: depends on rejected species " + d.species());
                continue;
            }
            Parsed<CultivarSchema.Mapped> p = cultivarsById.get(d.id().value());
            okC.put(d.id().value(), new DefinitionsSnapshot.CultivarEntry(d, p.mapped().get().info(), p.file()));
        }

        List<LogLine> lines = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        for (Map.Entry<ResourceLocation, List<Problem>> e : byFile.entrySet()) {
            for (Problem p : e.getValue()) {
                String path = new PathCodecs.PathError(p.path(), "").jsonPath();
                lines.add(new LogLine(p.isError(), e.getKey() + ": " + path + ": " + p.message()));
                if (p.isError()) {
                    errors++;
                } else {
                    warnings++;
                }
            }
        }
        for (String d : dependents) {
            lines.add(new LogLine(true, d));
            errors++;
        }
        DefinitionsSnapshot.Stats stats = new DefinitionsSnapshot.Stats(species.size(), species.size() - okS.size(),
                cultivars.size(), cultivars.size() - okC.size(), errors, warnings);
        return new LoadResult(new DefinitionsSnapshot(okS, okC, stats, dictionary), lines);
    }

    private static ResourceLocation fileOf(String record, Map<String, Parsed<SpeciesSchema.Mapped>> s,
                                           Map<String, Parsed<CultivarSchema.Mapped>> c) {
        if (record.startsWith("species:")) {
            Parsed<?> p = s.get(record.substring("species:".length()));
            return p == null ? null : p.file();
        }
        if (record.startsWith("cultivar:")) {
            Parsed<?> p = c.get(record.substring("cultivar:".length()));
            return p == null ? null : p.file();
        }
        return null;
    }

    /** Species id behind a reference-graph message of a cultivar, or {@code null}. */
    private static String dependentMessage(String message) {
        for (String prefix : List.of("depends on rejected species:", "dangling reference to species:")) {
            if (message.startsWith(prefix)) {
                String rest = message.substring(prefix.length());
                return rest.endsWith("#invalid") ? rest.substring(0, rest.length() - "#invalid".length()) : rest;
            }
        }
        return null;
    }
}
