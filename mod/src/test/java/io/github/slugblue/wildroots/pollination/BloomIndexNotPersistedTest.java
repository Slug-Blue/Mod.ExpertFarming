package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.Test;

/**
 * WR-140 acceptance: the bloom index is not saved to disk. No class of the
 * pollination package is a {@link SavedData}, and the index holds no
 * {@code SavedData} field; it is rebuilt from {@code onLoad} instead.
 */
class BloomIndexNotPersistedTest {

    static List<Class<?>> packageClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        Path dir = Path.of(BloomIndex.class.getResource("BloomIndex.class").toURI()).getParent();
        List<String> names;
        try (Stream<Path> s = Files.list(dir)) {
            names = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class"))
                    .map(n -> n.substring(0, n.length() - ".class".length())).toList();
        }
        ClassLoader cl = BloomIndex.class.getClassLoader();
        java.util.ArrayList<Class<?>> out = new java.util.ArrayList<>();
        for (String n : names) {
            out.add(Class.forName(BloomIndex.class.getPackageName() + "." + n, false, cl));
        }
        return out;
    }

    @Test
    void noSavedDataInThePollinationPackage() throws Exception {
        List<Class<?>> classes = packageClasses();
        assertTrue(classes.contains(BloomIndex.class) && classes.contains(BloomTracker.class), "scan found " + classes);
        for (Class<?> c : classes) {
            assertFalse(SavedData.class.isAssignableFrom(c), c + " is a SavedData");
        }
        for (Class<?> c : List.of(BloomIndex.class, BloomTracker.class)) {
            for (Field f : c.getDeclaredFields()) {
                assertFalse(SavedData.class.isAssignableFrom(f.getType()), c.getSimpleName() + "." + f.getName());
            }
        }
    }
}
