package io.github.slugblue.wildroots.oldsave;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;

/**
 * WR-196, docs/05-architecture.md §5.4 item 3: builds the GameTest template
 * {@code old_save_v<N>.nbt} from the SNBT fixtures of that release
 * ({@code fixtures/v<N>/}), so "built from the fixtures" is checkable
 * ({@code OldSaveV1Test#templateIsBuiltFromTheFixtures}) and the next
 * release can freeze its own template the same way.
 *
 * <p>Writing a template (once per release, never over an existing file):
 * {@code WILDROOTS_WRITE_OLD_SAVE=v2 ./gradlew :mod:test --tests '*OldSaveTemplate*'}
 * runs {@link OldSaveTemplateWriterTest}, which writes
 * {@code src/gametest/resources/data/wildroots/structures/gametest/old_save_v2.nbt}
 * if it does not exist yet; then pin its sha256 in the release's test. A
 * later format needs its own fixtures directory (and, if the layout of the
 * template changes, a copy of {@link #build} for that release).
 *
 * <p>Layout ({@code 7×4×5}): stone floor at y 0, farmland at y 1, plants at
 * y 2 on row z 1 (x 1 young player plant, stage 0; x 2 flowering mother with
 * a pending lot, stage 4; x 3 lazy wild plant, stage 2; x 4 sleeping plant of
 * the removed species {@code wildroots:old_turnip}, stage 1), light blocks
 * at y 3 over the plants and on a checker pattern, empty farmland at
 * (1, 1, 3) with light above, a chest at (4, 1, 3) with a lot of 5 seeds, a
 * pouch and a mother root. Air fills the rest.
 */
public final class OldSaveTemplate {

    /** Data version of Minecraft 1.20.1. */
    static final int DATA_VERSION = 3465;
    static final Path TEMPLATES = Path.of("src/gametest/resources/data/wildroots/structures/gametest");

    private final String version;
    private final ListTag palette = new ListTag();
    private final ListTag blocks = new ListTag();

    private OldSaveTemplate(String version) {
        this.version = version;
    }

    static Path file(String version) {
        return TEMPLATES.resolve("old_save_" + version + ".nbt");
    }

    CompoundTag fixture(String name) throws IOException {
        String path = "/fixtures/" + version + "/" + name + "_" + version + ".snbt";
        try (InputStream in = OldSaveTemplate.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IOException("bad fixture " + path, e);
        }
    }

    private int state(String name, String... props) {
        CompoundTag s = new CompoundTag();
        s.putString("Name", name);
        if (props.length > 0) {
            CompoundTag p = new CompoundTag();
            for (int i = 0; i < props.length; i += 2) {
                p.putString(props[i], props[i + 1]);
            }
            s.put("Properties", p);
        }
        for (int i = 0; i < palette.size(); i++) {
            if (palette.getCompound(i).equals(s)) {
                return i;
            }
        }
        palette.add(s);
        return palette.size() - 1;
    }

    private static ListTag ints(int... v) {
        ListTag l = new ListTag();
        for (int i : v) {
            l.add(IntTag.valueOf(i));
        }
        return l;
    }

    private void put(int x, int y, int z, int st, CompoundTag nbt) {
        CompoundTag b = new CompoundTag();
        b.put("pos", ints(x, y, z));
        b.putInt("state", st);
        if (nbt != null) {
            b.put("nbt", nbt);
        }
        blocks.add(b);
    }

    private static CompoundTag plantBe(CompoundTag wr) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "wildroots:plant");
        t.put("wr", wr);
        return t;
    }

    private static CompoundTag item(int slot, String id, int count, String key, CompoundTag root) {
        CompoundTag t = new CompoundTag();
        t.putByte("Slot", (byte) slot);
        t.putString("id", id);
        t.putByte("Count", (byte) count);
        CompoundTag tag = new CompoundTag();
        tag.put(key, root);
        t.put("tag", tag);
        return t;
    }

    /** The structure template of {@code version} ({@code "v1"}, …) built from its fixtures. */
    static CompoundTag build(String version) throws IOException {
        return new OldSaveTemplate(version).build();
    }

    private CompoundTag build() throws IOException {
        int sx = 7;
        int sy = 4;
        int sz = 5;
        int stone = state("minecraft:stone");
        int farm = state("minecraft:farmland", "moisture", "7");
        int light = state("minecraft:light", "level", "15", "waterlogged", "false");
        int air = state("minecraft:air");
        CompoundTag plant = fixture("plant");
        CompoundTag young = plant.copy();
        young.remove("pl");
        young.remove("pf");
        CompoundTag sleeping = young.copy();
        sleeping.putString("sp", "wildroots:old_turnip");
        sleeping.getCompound("g").putString("sp", "wildroots:old_turnip");
        CompoundTag wild = fixture("plant_wild");
        int[][] cells = {{1, 0}, {2, 4}, {3, 2}, {4, 1}};
        CompoundTag[] roots = {young, plant, wild, sleeping};
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (y == 0) {
                        put(x, y, z, stone, null);
                        continue;
                    }
                    boolean done = false;
                    for (int i = 0; i < cells.length; i++) {
                        if (z == 1 && x == cells[i][0]) {
                            if (y == 1) {
                                put(x, y, z, farm, null);
                            }
                            if (y == 2) {
                                put(x, y, z, state("wildroots:wild_carrot", "stage", Integer.toString(cells[i][1]),
                                        "size", "0", "variant", "0"), plantBe(roots[i]));
                            }
                            if (y == 3) {
                                put(x, y, z, light, null);
                            }
                            done = true;
                        }
                    }
                    if (done) {
                        continue;
                    }
                    if (z == 3 && x == 1) {
                        put(x, y, z, y == 1 ? farm : y == 3 ? light : air, null);
                        continue;
                    }
                    if (z == 3 && x == 4 && y == 1) {
                        CompoundTag chest = new CompoundTag();
                        chest.putString("id", "minecraft:chest");
                        ListTag items = new ListTag();
                        items.add(item(0, "wildroots:seed_lot", 5, "wildroots:lot", fixture("lot")));
                        items.add(item(1, "wildroots:seed_pouch", 1, "wildroots:pouch", fixture("pouch")));
                        items.add(item(2, "wildroots:mother_root", 1, "wildroots:mother", fixture("mother")));
                        chest.put("Items", items);
                        put(x, y, z, state("minecraft:chest", "facing", "north", "type", "single", "waterlogged", "false"),
                                chest);
                        continue;
                    }
                    put(x, y, z, y == 3 && (x + z) % 2 == 0 ? light : air, null);
                }
            }
        }
        CompoundTag root = new CompoundTag();
        root.put("size", ints(sx, sy, sz));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", DATA_VERSION);
        return root;
    }

    /** Writes the template of {@code version} unless the file exists; returns whether it wrote. */
    static boolean writeOnce(String version) throws IOException {
        Path out = file(version);
        if (Files.exists(out)) {
            return false;
        }
        Files.createDirectories(out.getParent());
        NbtIo.writeCompressed(build(version), out.toFile());
        return true;
    }
}
