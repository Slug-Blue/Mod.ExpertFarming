package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * WR-193, WR-194: every {@code wildroots:} model parent, override model and texture a model
 * names exists; every PNG is RGBA, 16 wide and a multiple of 16 high, and carries only
 * IHDR/PLTE/tRNS/IDAT/IEND chunks (no metadata). The wave-1 M1a items have item models.
 */
class AssetIntegrityTest {

    static final Set<String> ALLOWED_CHUNKS = Set.of("IHDR", "PLTE", "tRNS", "IDAT", "IEND");
    static final List<String> M1A_ITEMS = List.of("wild_carrot_root", "eastern_carrot", "carrot", "mother_root",
            "seed_lot", "seed_pouch", "field_journal", "hand_lens");

    static Path assets() throws Exception {
        URL url = AssetIntegrityTest.class.getResource("/assets/wildroots/blockstates/wild_carrot.json");
        assertTrue(url != null && "file".equals(url.getProtocol()), "assets on the test classpath as files");
        return Path.of(url.toURI()).getParent().getParent();
    }

    static List<Path> files(Path dir, String ext) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(ext)).sorted().toList();
        }
    }

    static Path resolve(Path assets, String kind, String ref, String ext) {
        String path = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
        return assets.resolve(kind).resolve(path + ext);
    }

    @Test
    void referencedModelsAndTexturesExist() throws Exception {
        Path assets = assets();
        List<String> missing = new ArrayList<>();
        for (Path model : files(assets.resolve("models"), ".json")) {
            JsonObject json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> models = new ArrayList<>();
            if (json.has("parent")) {
                models.add(json.get("parent").getAsString());
            }
            if (json.has("overrides")) {
                for (JsonElement o : json.getAsJsonArray("overrides")) {
                    models.add(o.getAsJsonObject().get("model").getAsString());
                }
            }
            for (String ref : models) {
                if (ref.startsWith("wildroots:") && !Files.isRegularFile(resolve(assets, "models", ref, ".json"))) {
                    missing.add(model.getFileName() + " -> model " + ref);
                }
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    String ref = t.getValue().getAsString();
                    if (ref.startsWith("wildroots:") && !Files.isRegularFile(resolve(assets, "textures", ref, ".png"))) {
                        missing.add(model.getFileName() + " -> texture " + ref);
                    }
                }
            }
        }
        for (String item : M1A_ITEMS) {
            if (!Files.isRegularFile(assets.resolve("models/item/" + item + ".json"))) {
                missing.add("item model " + item);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void pngsAreCleanRgbaSixteenPixelTiles() throws Exception {
        List<Path> pngs = files(assets().resolve("textures"), ".png");
        assertTrue(pngs.size() > 0, "textures present");
        for (Path png : pngs) {
            try (InputStream raw = Files.newInputStream(png); DataInputStream in = new DataInputStream(raw)) {
                byte[] sig = in.readNBytes(8);
                assertEquals(0x89, sig[0] & 0xFF, png + ": PNG signature");
                boolean first = true;
                while (true) {
                    int len = in.readInt();
                    String type = new String(in.readNBytes(4), StandardCharsets.US_ASCII);
                    assertTrue(ALLOWED_CHUNKS.contains(type), () -> png + ": chunk " + type);
                    byte[] data = in.readNBytes(len);
                    in.readInt();
                    if (first) {
                        assertEquals("IHDR", type, png + ": IHDR first");
                        int w = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                        int h = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
                        assertEquals(16, w, png + ": width");
                        assertTrue(h > 0 && h % 16 == 0, png + ": height " + h);
                        assertEquals(8, data[8], png + ": bit depth");
                        assertEquals(6, data[9], png + ": colour type RGBA");
                        first = false;
                    }
                    if (type.equals("IEND")) {
                        break;
                    }
                }
            }
        }
    }
}
