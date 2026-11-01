package io.github.slugblue.wildroots.genetics.random;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * WR-101 (grep half; the ArchUnit half is {@code ArchRules#geneticsDeterministicRandomOnly}
 * in {@code :mod}): the engine sources never use JDK randomness or
 * {@code String.hashCode} (ADR-0001, docs/05-architecture.md §4.3).
 */
class ForbiddenRandomSourcesTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "java\\.util\\.Random\\b|\\bnew Random\\(|SplittableRandom|ThreadLocalRandom"
                    + "|Math\\.random|SecureRandom|\\.hashCode\\(\\)");

    @Test
    void engineSourcesUseOnlySplitMixAndHash64() throws IOException {
        Path main = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(main), "run from the :genetics project dir: " + main.toAbsolutePath());
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String code = lines.get(i).replaceAll("//.*$", "");
                    if (code.trim().startsWith("*")) {
                        continue; // javadoc prose may name the forbidden APIs
                    }
                    if (FORBIDDEN.matcher(code).find()) {
                        hits.add(p + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "forbidden randomness in :genetics:\n" + String.join("\n", hits));
    }
}
