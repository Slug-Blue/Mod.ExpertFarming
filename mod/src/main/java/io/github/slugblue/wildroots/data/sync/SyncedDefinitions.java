package io.github.slugblue.wildroots.data.sync;

import io.github.slugblue.wildroots.data.CultivarInfo;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.data.SpeciesInfo;
import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * What clients learn about the definitions (docs/05-architecture.md §8.3,
 * left column; SEC-04, SEC-31): per species the archetype, the translation
 * key, the display names of traits, the tint colour and the life cycle; per
 * cultivar the species, the tier, the harvest item, the help key and the
 * food reference; the list of loaded lines.
 *
 * <p>Never sent: allele frequencies, elite loci, {@code tradeoffs},
 * {@code budget}, load, mutation rates, profile thresholds, village
 * templates, quality roles. The records below are the whole serialised form;
 * {@code DefinitionsSyncTest} pins their components.
 */
public record SyncedDefinitions(List<String> lines, List<Species> species, List<Cultivar> cultivars) {

    /** Largest string field in the payload. */
    static final int MAX_STRING = 256;
    /** Largest element count; the byte budget of the payload bounds it further. */
    static final int MAX_COUNT = 65_536;

    public record Species(ResourceLocation id, String archetype, String translationKey,
                          Map<String, String> displayTraits, int color, String lifeCycle) {

        public Species {
            displayTraits = Map.copyOf(displayTraits);
        }
    }

    public record Cultivar(ResourceLocation id, ResourceLocation species, int tier,
                           Optional<ResourceLocation> produceItem, String helpKey,
                           Optional<FoodReference> foodReference) {
    }

    /** {@code food_reference} for {@code getFoodProperties} and tooltips on the client. */
    public record FoodReference(int nutrition, float saturation) {
    }

    public SyncedDefinitions {
        lines = List.copyOf(lines);
        species = List.copyOf(species);
        cultivars = List.copyOf(cultivars);
    }

    /** The client view of a server snapshot plus the loaded lines. */
    public static SyncedDefinitions of(DefinitionsSnapshot snapshot, List<String> lines) {
        List<Species> s = new ArrayList<>();
        for (DefinitionsSnapshot.SpeciesEntry e : snapshot.species().values()) {
            SpeciesInfo i = e.info();
            OptionalInt color = i.color();
            s.add(new Species(i.id(), i.archetype(), i.translationKey(), i.displayTraits(), color.orElse(-1),
                    i.lifeCycle()));
        }
        List<Cultivar> c = new ArrayList<>();
        for (DefinitionsSnapshot.CultivarEntry e : snapshot.cultivars().values()) {
            CultivarInfo i = e.info();
            ResourceLocation species = ResourceLocation.tryParse(e.def().species());
            if (species == null) {
                continue;
            }
            c.add(new Cultivar(i.id(), species, e.def().tier(), i.produceItem(), i.helpKey(),
                    i.foodReference().map(f -> new FoodReference(f.nutrition(), (float) f.saturation()))));
        }
        return new SyncedDefinitions(lines, s, c);
    }

    // ------------------------------------------------------------------ wire format

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        lines.forEach(l -> buf.writeUtf(l, MAX_STRING));
        buf.writeVarInt(species.size());
        for (Species s : species) {
            buf.writeResourceLocation(s.id());
            buf.writeUtf(s.archetype(), MAX_STRING);
            buf.writeUtf(s.translationKey(), MAX_STRING);
            buf.writeVarInt(s.displayTraits().size());
            s.displayTraits().forEach((k, v) -> {
                buf.writeUtf(k, MAX_STRING);
                buf.writeUtf(v, MAX_STRING);
            });
            buf.writeInt(s.color());
            buf.writeUtf(s.lifeCycle(), MAX_STRING);
        }
        buf.writeVarInt(cultivars.size());
        for (Cultivar c : cultivars) {
            buf.writeResourceLocation(c.id());
            buf.writeResourceLocation(c.species());
            buf.writeVarInt(c.tier());
            buf.writeOptional(c.produceItem(), FriendlyByteBuf::writeResourceLocation);
            buf.writeUtf(c.helpKey(), MAX_STRING);
            buf.writeOptional(c.foodReference(), (b, f) -> {
                b.writeVarInt(f.nutrition());
                b.writeFloat(f.saturation());
            });
        }
    }

    /** Bounded reader: counts and strings are checked before allocation, extra bytes are an error. */
    public static SyncedDefinitions read(FriendlyByteBuf buf) {
        int nl = BoundedReader.readSize(buf, MAX_COUNT);
        List<String> lines = new ArrayList<>(Math.min(nl, 64));
        for (int i = 0; i < nl; i++) {
            lines.add(buf.readUtf(MAX_STRING));
        }
        int ns = BoundedReader.readSize(buf, MAX_COUNT);
        List<Species> species = new ArrayList<>(Math.min(ns, 1024));
        for (int i = 0; i < ns; i++) {
            ResourceLocation id = BoundedReader.readId(buf);
            String archetype = buf.readUtf(MAX_STRING);
            String key = buf.readUtf(MAX_STRING);
            int nt = BoundedReader.readSize(buf, 64);
            Map<String, String> traits = new LinkedHashMap<>();
            for (int t = 0; t < nt; t++) {
                traits.put(buf.readUtf(MAX_STRING), buf.readUtf(MAX_STRING));
            }
            int color = buf.readInt();
            if (color < -1 || color > 0xFFFFFF) {
                throw new DecoderException("colour out of range");
            }
            species.add(new Species(id, archetype, key, traits, color, buf.readUtf(MAX_STRING)));
        }
        int nc = BoundedReader.readSize(buf, MAX_COUNT);
        List<Cultivar> cultivars = new ArrayList<>(Math.min(nc, 1024));
        for (int i = 0; i < nc; i++) {
            ResourceLocation id = BoundedReader.readId(buf);
            ResourceLocation sp = BoundedReader.readId(buf);
            int tier = buf.readVarInt();
            if (tier < 0 || tier > 9) {
                throw new DecoderException("tier out of range");
            }
            Optional<ResourceLocation> item = buf.readOptional(BoundedReader::readId);
            String help = buf.readUtf(MAX_STRING);
            Optional<FoodReference> food = buf.readOptional(b -> {
                int n = b.readVarInt();
                float s = b.readFloat();
                if (n < 0 || n > 20 || !Float.isFinite(s) || s < 0 || s > 2) {
                    throw new DecoderException("food reference out of range");
                }
                return new FoodReference(n, s);
            });
            cultivars.add(new Cultivar(id, sp, tier, item, help, food));
        }
        BoundedReader.requireFullyRead(buf);
        return new SyncedDefinitions(lines, species, cultivars);
    }
}
