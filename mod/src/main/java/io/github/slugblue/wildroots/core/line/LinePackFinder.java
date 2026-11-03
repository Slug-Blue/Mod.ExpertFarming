package io.github.slugblue.wildroots.core.line;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.Guarded;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;
import org.slf4j.Logger;

/**
 * Adds the built-in data pack of every enabled line (docs/05-architecture.md
 * §9.2; ADR-0006; S1): {@code PackSource.BUILT_IN}, required, on top. A new
 * world, and an old world that meets the pack for the first time, put it
 * above all {@code mod:*} packs; a required pack is selected even when
 * {@code level.dat} lists it as disabled. The order saved in
 * {@code level.dat} is kept: a mod installed later sits above the line pack
 * (known limitation, §9.2).
 */
public final class LinePackFinder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LinePackFinder() {
    }

    /** Pack id of a line: {@code wildroots:line/<id>}. */
    public static String packId(String line) {
        return WildRoots.MOD_ID + ":line/" + line;
    }

    /** Translation key of the pack title. */
    public static String titleKey(String line) {
        return "wildroots.pack.line." + line;
    }

    /** {@code AddPackFindersEvent} handler (mod bus): server data only. */
    @EntryPoint
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        Guarded.run("line packs", "AddPackFindersEvent", () -> {
            Function<String, Path> root = line -> ModList.get().getModFileById(WildRoots.MOD_ID).getFile()
                    .findResource("resourcepacks", "lines", line);
            List<Pack> packs = packsFor(LineRegistry.get(), root);
            event.addRepositorySource(consumer -> packs.forEach(consumer));
        });
    }

    /** Packs of the enabled lines of {@code registry}; {@code root} maps a line id to its folder. */
    public static List<Pack> packsFor(LineRegistry registry, Function<String, Path> root) {
        List<Pack> out = new ArrayList<>();
        for (LineManifest.Line line : registry.enabled()) {
            Path dir = root.apply(line.id());
            if (dir == null || !Files.isDirectory(dir)) {
                LOGGER.error("Wild Roots: pack folder of line '{}' is missing", line.id());
                continue;
            }
            String id = packId(line.id());
            Pack pack = Pack.readMetaAndCreate(id, Component.translatable(titleKey(line.id())), true,
                    name -> new PathPackResources(name, true, dir), PackType.SERVER_DATA, Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack == null) {
                LOGGER.error("Wild Roots: pack.mcmeta of line '{}' is broken", line.id());
                continue;
            }
            out.add(pack);
        }
        return out;
    }
}
