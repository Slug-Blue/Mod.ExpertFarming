package io.github.slugblue.wildroots.client.journal.page;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The real lang files, for the layout tests. */
public final class LangFixture {

    public static final List<String> LANGS = List.of("en_us", "ru_ru");

    private LangFixture() {
    }

    static Path modDir() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path q = p; q != null; q = q.getParent()) {
            if (Files.isDirectory(q.resolve("src/main/resources/assets/wildroots/lang"))) {
                return q;
            }
            if (Files.isDirectory(q.resolve("mod/src/main/resources/assets/wildroots/lang"))) {
                return q.resolve("mod");
            }
        }
        throw new IllegalStateException("mod sources not found");
    }

    public static Map<String, String> lang(String name) throws IOException {
        Path f = modDir().resolve("src/main/resources/assets/wildroots/lang/" + name + ".json");
        JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> m = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            m.put(e.getKey(), e.getValue().getAsString());
        }
        return m;
    }

    /** Link markup out ({@code [[id|text]]} shows its text), as the reader sees the text. */
    public static String plain(String s) {
        return s.replaceAll("\\[\\[[^|\\]]*\\|([^\\]]*)]]", "$1").replaceAll("\\[\\[([^\\]]*)]]", "$1")
                .replace("%1$s", "99").replace("%2$s", "99").replace("%s", "99");
    }


    /** One violation per line, so a failure lists them all. */
    static void report(List<String> violations) {
        assertTrue(violations.isEmpty(), violations.size() + " strings leave their slot:\n"
                + String.join("\n", violations));
    }
}
