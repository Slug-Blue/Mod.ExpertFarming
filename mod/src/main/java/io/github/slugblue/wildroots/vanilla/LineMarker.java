package io.github.slugblue.wildroots.vanilla;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.line.LineManifest;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkEvent;

/**
 * The chunk marker {@code wr_lines} (WR-162; docs/05-architecture.md §11.2;
 * SEC-23): the lines that were enabled when our generation made the chunk.
 * A chunk generated before the mod was installed has no marker at all, so a
 * vanilla field in it may become a village landrace; a chunk generated with
 * the line never does. Written once, on the first load of a new chunk
 * ({@code ChunkEvent.Load#isNewChunk}), saved with the chunk.
 */
public final class LineMarker {

    public static final ResourceLocation ID = Ids.mod("wr_lines");
    static final String KEY = "wr_lines";
    static final int MAX_LINES = 64;

    public static final Capability<LineMarker> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** {@code null}: no marker (an old chunk). */
    private List<String> lines;

    /** The marked lines, or empty when the chunk has no marker. */
    public Optional<List<String>> lines() {
        return Optional.ofNullable(lines);
    }

    /** Sets the marker; {@code null} removes it (tests: an old chunk). */
    public void set(List<String> marked) {
        lines = marked == null ? null : List.copyOf(marked);
    }

    /** Whether the chunk was generated with {@code line} enabled. */
    public boolean has(String line) {
        return lines != null && lines.contains(line);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (lines != null) {
            ListTag l = new ListTag();
            lines.forEach(s -> l.add(StringTag.valueOf(s)));
            t.put(KEY, l);
        }
        return t;
    }

    void load(CompoundTag t) {
        if (!t.contains(KEY, Tag.TAG_LIST)) {
            lines = null;
            return;
        }
        ListTag l = t.getList(KEY, Tag.TAG_STRING);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size() && out.size() < MAX_LINES; i++) {
            String s = l.getString(i);
            if (s.matches("[a-z0-9_]{1,32}")) {
                out.add(s);
            }
        }
        lines = List.copyOf(out);
    }

    /** Marker of a loaded chunk. */
    public static Optional<LineMarker> of(LevelChunk chunk) {
        return chunk.getCapability(CAPABILITY).resolve();
    }

    /** Provider attached to every level chunk. */
    static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final LineMarker data = new LineMarker();
        private final LazyOptional<LineMarker> self = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return CAPABILITY.orEmpty(cap, self);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.load(nbt);
        }
    }

    /** Mod bus. */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(LineMarker.class);
    }

    /** Forge bus: every level chunk carries the (possibly empty) marker. */
    @EntryPoint
    public static void onAttach(AttachCapabilitiesEvent<LevelChunk> event) {
        Guarded.run("chunk line marker attach", "chunk", () -> event.addCapability(ID, new Provider()));
    }

    /** Forge bus: a chunk our generation just made gets the enabled lines. */
    @EntryPoint
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk c) {
            Guarded.run("chunk line marker", c.getPos(), () -> of(c).ifPresent(m -> {
                m.set(LineRegistry.get().enabled().stream().map(LineManifest.Line::id).toList());
                c.setUnsaved(true);
            }));
        }
    }
}
