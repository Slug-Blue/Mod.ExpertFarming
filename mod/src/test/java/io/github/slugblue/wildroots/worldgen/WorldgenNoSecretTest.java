package io.github.slugblue.wildroots.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture §4.3, worldgen §3: worldgen keys its RNG by the world seed
 * only, never by the world secret, so a seed reproduces the world. No
 * compiled class of the worldgen package refers to {@code WorldSecret}
 * (its name would be in the constant pool). Batch 4 mutation testing: a
 * patch seed mixed with the secret survived every other test.
 */
class WorldgenNoSecretTest {

    static final String SECRET_CLASS = "io/github/slugblue/wildroots/core/safety/WorldSecret";

    @Test
    void noWorldgenClassRefersToTheWorldSecret() throws Exception {
        Path dir = Path.of(ChunkBand.class.getResource("ChunkBand.class").toURI()).getParent();
        List<Path> classes;
        try (Stream<Path> s = Files.list(dir)) {
            classes = s.filter(p -> p.toString().endsWith(".class")).toList();
        }
        assertTrue(classes.stream().anyMatch(p -> p.getFileName().toString().equals("WildPatchFeature.class")),
                "scan found " + classes.size() + " classes in " + dir);
        for (Path p : classes) {
            String pool = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
            assertFalse(pool.contains(SECRET_CLASS), p.getFileName() + " refers to WorldSecret");
        }
    }
}
