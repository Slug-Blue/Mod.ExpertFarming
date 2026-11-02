package io.github.slugblue.wildroots.core.line;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Manifest of the lines shipped in the jar (docs/05-architecture.md §9.2;
 * ADR-0006): {@code resourcepacks/lines/lines.json} with id, release wave,
 * vanilla crops of the line and the Farmer's Delight analogue.
 *
 * <p>The file sits next to the line packs: {@code ci/check-jar.sh} allows
 * {@code resourcepacks/lines/**} in the jar, and a root-level
 * {@code wildroots/} folder would be outside the allowlist.
 */
public record LineManifest(List<Line> lines) {

    /** Path of the manifest inside the mod file. */
    public static final String PATH = "resourcepacks/lines/lines.json";

    /** One line (a crop wave unit). */
    public record Line(String id, int wave, List<ResourceLocation> vanillaCrops, Optional<ResourceLocation> fdAnalog) {

        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.comapFlatMap(Line::checkId, s -> s).fieldOf("id").forGetter(Line::id),
                Codec.intRange(1, 99).fieldOf("wave").forGetter(Line::wave),
                ResourceLocation.CODEC.listOf().optionalFieldOf("vanilla_crops", List.of()).forGetter(Line::vanillaCrops),
                ResourceLocation.CODEC.optionalFieldOf("fd_analog").forGetter(Line::fdAnalog)
        ).apply(i, Line::new));

        private static DataResult<String> checkId(String s) {
            return s.matches("[a-z0-9_]{1,32}") ? DataResult.success(s)
                    : DataResult.error(() -> "line id must match [a-z0-9_]{1,32}: " + s);
        }
    }

    public static final Codec<LineManifest> CODEC = RecordCodecBuilder.create(i -> i.group(
            Line.CODEC.listOf().fieldOf("lines").forGetter(LineManifest::lines)
    ).apply(i, LineManifest::new));

    public LineManifest {
        lines = List.copyOf(lines);
    }

    /** Parses a manifest; a broken jar resource is a build error, so this throws. */
    public static LineManifest parse(Reader reader) {
        JsonElement json = JsonParser.parseReader(reader);
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, msg -> { });
    }

    /** Reads the manifest from the classpath of the mod. */
    public static LineManifest fromClasspath() {
        try (InputStream in = LineManifest.class.getResourceAsStream("/" + PATH)) {
            if (in == null) {
                throw new IllegalStateException("missing " + PATH);
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + PATH, e);
        }
    }
}
