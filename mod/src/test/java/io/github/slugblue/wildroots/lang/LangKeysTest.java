package io.github.slugblue.wildroots.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * WR-192: the two lang files agree, and every translation key the code or
 * the data names exists in both.
 *
 * <ol>
 *   <li>{@code en_us.json} and {@code ru_ru.json} have the same key set;</li>
 *   <li>every string literal in {@code src/main/java} and every JSON string
 *   under {@code src/main/resources} and {@code src/generated/resources}
 *   (lang files excepted) that is a whole key of the mod
 *   ({@code wildroots.*}, {@code item.wildroots.*}, {@code block.wildroots.*},
 *   {@code commands.wildroots.*}, {@code itemGroup.wildroots.*}) exists in
 *   both files. A literal ending in {@code .} is a prefix the code completes
 *   at run time and is not checked here; {@link #NOT_DISPLAY_KEYS} lists the
 *   literals that look like keys but are never shown;</li>
 *   <li>a key in both files has the same format arguments ({@code %s},
 *   {@code %1$s}, …) in the same count, order and form.</li>
 * </ol>
 */
class LangKeysTest {

    /** Literals with a key's shape that are not display keys. */
    static final Set<String> NOT_DISPLAY_KEYS = Set.of(
            "wildroots.journal", // DispCodec.valid probe of an argument list (JournalData.validArgs, JournalTriggers)
            "wildroots.audit", // logger name (AuditLog)
            "wildroots.strictErrors", // system property (Guarded)
            "wildroots.refmap.json"); // mixin refmap file name (wildroots.mixins.json)

    static final Pattern KEY_LITERAL = Pattern.compile(
            "\"((?:wildroots|item\\.wildroots|block\\.wildroots|commands\\.wildroots|itemGroup\\.wildroots)\\.[A-Za-z0-9_.]*)\"");
    static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z%])");

    static Path modDir() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path q = p; q != null; q = q.getParent()) {
            if (Files.isDirectory(q.resolve("src/main/java/io/github/slugblue/wildroots"))) {
                return q;
            }
            if (Files.isDirectory(q.resolve("mod/src/main/java/io/github/slugblue/wildroots"))) {
                return q.resolve("mod");
            }
        }
        throw new IllegalStateException("mod sources not found from " + p);
    }

    static Map<String, String> lang(String name) throws IOException {
        Path f = modDir().resolve("src/main/resources/assets/wildroots/lang/" + name + ".json");
        JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> m = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            m.put(e.getKey(), e.getValue().getAsString());
        }
        return m;
    }

    /** Key literal → files that name it. */
    static Map<String, Set<String>> references() throws IOException {
        Map<String, Set<String>> refs = new TreeMap<>();
        Path mod = modDir();
        scan(mod.resolve("src/main/java"), ".java", refs);
        scan(mod.resolve("src/main/resources"), ".json", refs);
        scan(mod.resolve("src/generated/resources"), ".json", refs);
        return refs;
    }

    static void scan(Path dir, String ext, Map<String, Set<String>> refs) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(f -> f.toString().endsWith(ext))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/lang/")).toList();
        }
        for (Path f : files) {
            Matcher m = KEY_LITERAL.matcher(Files.readString(f, StandardCharsets.UTF_8));
            while (m.find()) {
                refs.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(f.getFileName().toString());
            }
        }
    }

    /** The format specifiers of a value, {@code %%} left out. */
    static List<String> formats(String v) {
        List<String> out = new ArrayList<>();
        Matcher m = FORMAT.matcher(v);
        while (m.find()) {
            if (!m.group().equals("%%")) {
                out.add(m.group());
            }
        }
        return out;
    }

    @Test
    void bothFilesHaveTheSameKeys() throws IOException {
        Set<String> en = lang("en_us").keySet();
        Set<String> ru = lang("ru_ru").keySet();
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(ru);
        Set<String> onlyRu = new TreeSet<>(ru);
        onlyRu.removeAll(en);
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(), "only in en_us: " + onlyEn + "; only in ru_ru: " + onlyRu);
    }

    @Test
    void everyReferencedKeyIsInBothFiles() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> ru = lang("ru_ru");
        Map<String, Set<String>> refs = references();
        assertTrue(refs.size() > 20, "the scan found only " + refs.size() + " key literals: wrong directory?");
        List<String> missing = new ArrayList<>();
        refs.forEach((k, files) -> {
            if (k.endsWith(".") || NOT_DISPLAY_KEYS.contains(k)) {
                return;
            }
            if (!en.containsKey(k)) {
                missing.add("en_us " + k + " " + files);
            }
            if (!ru.containsKey(k)) {
                missing.add("ru_ru " + k + " " + files);
            }
        });
        assertTrue(missing.isEmpty(), "missing keys:\n" + String.join("\n", missing));
    }

    @Test
    void formatArgumentsMatchInCountOrderAndForm() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> ru = lang("ru_ru");
        List<String> bad = new ArrayList<>();
        en.forEach((k, v) -> {
            if (ru.containsKey(k) && !formats(v).equals(formats(ru.get(k)))) {
                bad.add(k + ": en " + formats(v) + " ru " + formats(ru.get(k)));
            }
        });
        assertTrue(bad.isEmpty(), "format arguments differ:\n" + String.join("\n", bad));
    }

    @Test
    void formatScannerSelfCheck() {
        assertEquals(List.of("%s", "%2$s", "%1$s"), formats("a %s b %2$s %% c %1$s"));
        assertEquals(List.of("%d"), formats("%d"));
        Matcher m = KEY_LITERAL.matcher("x(\"wildroots.tooltip.lot\") \"wildroots:id\" \"wildroots\" \"item.wildroots.a\"");
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        assertEquals(List.of("wildroots.tooltip.lot", "item.wildroots.a"), found);
    }
}
