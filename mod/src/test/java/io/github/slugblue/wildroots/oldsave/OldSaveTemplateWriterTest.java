package io.github.slugblue.wildroots.oldsave;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * WR-196: writes {@code old_save_<version>.nbt} once per release, from
 * {@code WILDROOTS_WRITE_OLD_SAVE=<version>} (see {@link OldSaveTemplate}).
 * Off in normal runs; it never overwrites an existing template.
 */
class OldSaveTemplateWriterTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "WILDROOTS_WRITE_OLD_SAVE", matches = "v[0-9]+")
    void writeTemplateOnce() throws Exception {
        String version = System.getenv("WILDROOTS_WRITE_OLD_SAVE");
        boolean wrote = OldSaveTemplate.writeOnce(version);
        System.out.println("[WR-196] old_save_" + version + ".nbt " + (wrote ? "written" : "exists, left as it is"));
        assertTrue(Files.isRegularFile(OldSaveTemplate.file(version)));
    }
}
