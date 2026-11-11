package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.network.ServerPacketGuard;
import io.github.slugblue.wildroots.network.WrNetwork;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.zip.Deflater;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WR-197: the packet budget.
 *
 * <ul>
 *   <li>SEC-02 for every production C→S packet: the C→S types are read from
 *   the registration of {@link WrNetwork#CHANNEL} itself (Forge's codec
 *   table), and each must have a burst case in {@link #BURSTS} — 50 packets
 *   in one tick through the real {@code handleCustomPayload} path, at most 5
 *   handled. A new C→S packet without a case fails here.</li>
 *   <li>SEC-12 worst cases (pattern of S10 {@code s10_pouchAndPacketSizes}):
 *   16-entry pouches of adversarial lots at the 1 KiB limit, a double chest
 *   menu with 90 pouches (54 + 36) and the {@code InventoryMenu} with 41;
 *   the uncompressed {@code ClientboundContainerSetContentPacket} stays
 *   ≤ 1 900 000 B and the {@code NbtAccounter} usage of one pouch
 *   ≤ 512 KiB (S14).</li>
 * </ul>
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacketBudgetGameTests {

    static final Logger LOG = LoggerFactory.getLogger(PacketBudgetGameTests.class);

    /** Uncompressed packet bound: the 2 097 151 B frame without compression, minus a 10 % margin (S14). */
    static final int MAX_PACKET = 1_900_000;
    /** {@code NbtAccounter} bound of one pouch (S14; {@code readItem} allows 2 097 152). */
    static final long MAX_ACCOUNTER = 512 * 1024;

    private PacketBudgetGameTests() {
    }

    // ------------------------------------------------------------------ SEC-02

    /**
     * One burst case: sends 50 packets of its type in one tick through the
     * listener of {@code p} and returns how many reached the handler (by the
     * handler's own effect).
     */
    interface Burst {
        int sendFifty(GameTestHelper helper, PacketGuardGameTests.Probe p);
    }

    /** A case per production C→S packet; the check below keeps this map equal to the registration. */
    static final Map<Class<?>, Burst> BURSTS = Map.of(JournalActionPacket.class, PacketBudgetGameTests::journalActions);

    static int journalActions(GameTestHelper helper, PacketGuardGameTests.Probe p) {
        JournalData j = JournalGameTests.journal(p.player());
        for (int i = 0; i < 50; i++) {
            j.unlock("note.wr197_burst_" + i, List.of(), List.of());
        }
        int before = j.revision();
        for (int i = 0; i < 50; i++) {
            // one unpinned entry per packet: every handled PIN changes the journal exactly once
            JournalActionPacket msg = new JournalActionPacket(new ResourceLocation("wildroots", "note.wr197_burst_" + i),
                    JournalActionPacket.PIN);
            p.listener().handleCustomPayload((ServerboundCustomPayloadPacket) WrNetwork.CHANNEL
                    .toVanillaPacket(msg, NetworkDirection.PLAY_TO_SERVER));
        }
        return j.revision() - before;
    }

    /**
     * The message types of {@code channel} that the server accepts (direction
     * PLAY_TO_SERVER or none), read from Forge's {@code IndexedMessageCodec}
     * of the channel: the registration itself, not a list kept by hand.
     */
    @SuppressWarnings("unchecked")
    static Set<Class<?>> serverboundTypes(SimpleChannel channel) {
        try {
            Field codecField = SimpleChannel.class.getDeclaredField("indexedCodec");
            codecField.setAccessible(true);
            Object codec = codecField.get(channel);
            Field typesField = codec.getClass().getDeclaredField("types");
            typesField.setAccessible(true);
            Map<Class<?>, ?> types = (Map<Class<?>, ?>) typesField.get(codec);
            Set<Class<?>> out = new java.util.LinkedHashSet<>();
            for (Map.Entry<Class<?>, ?> e : types.entrySet()) {
                Field dir = e.getValue().getClass().getDeclaredField("networkDirection");
                dir.setAccessible(true);
                Optional<NetworkDirection> d = (Optional<NetworkDirection>) dir.get(e.getValue());
                if (d.isEmpty() || d.get() == NetworkDirection.PLAY_TO_SERVER || d.get() == NetworkDirection.LOGIN_TO_SERVER) {
                    out.add(e.getKey());
                }
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the registration of " + channel, e);
        }
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr18x_journal")
    public static void everyProductionC2sPacketHandlesAtMostFiveOfFifty(GameTestHelper helper) {
        Set<Class<?>> registered = serverboundTypes(WrNetwork.CHANNEL);
        // the reader finds the probe channel's one C→S packet and skips the S→C packets of the main channel
        helper.assertTrue(serverboundTypes(GuardProbeChannel.channel()).size() == 1, "registration reader is broken");
        helper.assertTrue(!registered.contains(io.github.slugblue.wildroots.journal.JournalSyncPacket.class),
                "registration reader counts S→C packets");
        Set<String> missing = new TreeSet<>();
        for (Class<?> c : registered) {
            if (!BURSTS.containsKey(c)) {
                missing.add(c.getName());
            }
        }
        helper.assertTrue(missing.isEmpty(), "C→S packets without a SEC-02 burst case in PacketBudgetGameTests.BURSTS: "
                + missing);
        Set<String> stale = new TreeSet<>();
        for (Class<?> c : BURSTS.keySet()) {
            if (!registered.contains(c)) {
                stale.add(c.getName());
            }
        }
        helper.assertTrue(stale.isEmpty(), "burst cases for packets that are not registered: " + stale);
        helper.assertTrue(!registered.isEmpty(), "no C→S packet registered on wildroots:main");
        Map<String, Integer> handled = new LinkedHashMap<>();
        for (Class<?> c : registered) {
            PacketGuardGameTests.Probe p = PacketGuardGameTests.Probe.connect(helper, "wr197_" + c.getSimpleName());
            JournalGameTests.Recorder rec = new JournalGameTests.Recorder();
            try {
                long t0 = System.nanoTime();
                int n = BURSTS.get(c).sendFifty(helper, p);
                int bound = 5 + PacketGuardGameTests.refillDuring(System.nanoTime() - t0);
                handled.put(c.getSimpleName(), n);
                helper.assertTrue(n >= 1, c.getSimpleName() + ": nothing handled, the real path is broken");
                helper.assertTrue(n <= bound, c.getSimpleName() + ": handled " + n + " of 50 in one tick, bound " + bound);
                helper.assertTrue(p.disconnects().isEmpty(), c.getSimpleName() + ": 45 drops must not disconnect");
            } finally {
                rec.close();
                ServerPacketGuard.LIMITER.forget(p.player().getUUID());
            }
        }
        LOG.info("[WR-197] SEC-02 bursts of 50: handled {}", handled);
        helper.succeed();
    }

    // ------------------------------------------------------------------ SEC-12

    /** Random CJK text of {@code n} code points: 3 B each in NBT, badly compressible. */
    static String cjk(SplittableRandom r, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.appendCodePoint(0x4E00 + r.nextInt(0x9FA5 - 0x4E00));
        }
        return sb.toString();
    }

    /**
     * An adversarial lot at the 1 KiB limit: the real lot format (mother,
     * 4 fathers, random CJK line name), its {@code disp} padded with random
     * CJK arguments until the next line would pass 1 KiB. Still a valid lot.
     */
    static CompoundTag adversarialLot(SplittableRandom r) {
        GeneticsService gs = Definitions.current().genetics();
        List<LotFathersNbt.Father> fathers = new ArrayList<>();
        for (int i = 0; i < NbtLimits.LOT_MAX_FATHERS; i++) {
            fathers.add(new LotFathersNbt.Father(gs.encode(PlantFixtures.genome(r.nextLong())).toFather(),
                    1 + r.nextInt(15), i == 3));
        }
        LotStacks.Parent mother = new LotStacks.Parent(PlantFixtures.SP, Optional.of("wildroots:eastern_carrot"),
                gs.encode(PlantFixtures.genome(r.nextLong())), Optional.of(cjk(r, LotNbt.MAX_LINE_NAME)),
                r.nextInt(NbtLimits.LOT_MAX_GENERATION), LotFlags.FROM_WILD);
        CompoundTag lot = LotStacks.seedsTag(mother, fathers, (float) r.nextDouble(), LotFlags.BEES, r.nextLong(), () -> false);
        List<DispCodec.Line> disp = new ArrayList<>(LotNbt.disp(lot));
        while (disp.size() < DispCodec.MAX_LINES) {
            disp.add(DispCodec.Line.of("wildroots.tooltip.lot.trait_" + disp.size(), cjk(r, 1 + r.nextInt(12))));
            CompoundTag next = lot.copy();
            next.put("disp", DispCodec.encode(disp));
            if (!NbtLimits.lot(next).ok()) {
                break;
            }
            lot = next;
        }
        return lot;
    }

    /** A 16-entry pouch item of distinct adversarial lots. */
    static ItemStack worstPouch(SplittableRandom r) {
        PouchContents p = PouchContents.EMPTY;
        for (int i = 0; i < PouchContents.CAPACITY; i++) {
            p = p.insert(adversarialLot(r), 1).orElseThrow(() -> new AssertionError("a lot at the limit did not fit")).pouch();
        }
        ItemStack s = new ItemStack(ModItems.SEED_POUCH.get());
        s.getOrCreateTag().put(PouchContents.KEY, p.write());
        return s;
    }

    static byte[] nbt(CompoundTag t) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(t, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static long accounter(CompoundTag t) {
        try {
            NbtAccounter acc = new NbtAccounter(Long.MAX_VALUE);
            NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbt(t))), acc);
            return acc.getUsage();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int deflate(byte[] data) {
        Deflater d = new Deflater(); // as CompressionEncoder
        d.setInput(data);
        d.finish();
        byte[] buf = new byte[8192];
        int total = 0;
        while (!d.finished()) {
            total += d.deflate(buf);
        }
        d.end();
        return total;
    }

    /** The bytes of the menu's full content packet as the server writes it (packet id included). */
    static byte[] contentPacket(AbstractContainerMenu menu) {
        NonNullList<ItemStack> items = NonNullList.create();
        items.addAll(menu.getItems());
        ClientboundContainerSetContentPacket p = new ClientboundContainerSetContentPacket(menu.containerId,
                menu.getStateId(), items, menu.getCarried());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(0x12); // id of the packet in the play protocol (one byte)
            p.write(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    static void fill(Player player, SplittableRandom r, ToIntFunction<Player> slots) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            inv.items.set(i, worstPouch(r));
        }
        if (slots.applyAsInt(player) > inv.items.size()) {
            for (int i = 0; i < inv.armor.size(); i++) {
                inv.armor.set(i, worstPouch(r));
            }
            inv.offhand.set(0, worstPouch(r));
        }
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 400)
    public static void worstCasePouchMenusFitOnePacket(GameTestHelper helper) {
        SplittableRandom r = new SplittableRandom(197);
        // one lot and one pouch
        CompoundTag lot = adversarialLot(r);
        NbtBudget.Result lr = NbtLimits.lot(lot);
        helper.assertTrue(LotNbt.read(lot) instanceof LotNbt.Lot, "the adversarial lot is not a valid lot");
        helper.assertTrue(lr.ok() && lr.bytes() > NbtLimits.LOT_MAX_BYTES - 64, "lot not at the limit: " + lr);
        ItemStack pouch = worstPouch(r);
        CompoundTag pouchRoot = pouch.getTag().getCompound(PouchContents.KEY);
        NbtBudget.Result pr = NbtLimits.pouch(pouchRoot);
        helper.assertTrue(pr.ok() && PouchContents.read(pouchRoot).orElseThrow().used() == 16, "pouch " + pr);
        long acc = accounter(pouch.getTag());
        int pouchNbt = nbt(pouch.getTag()).length;
        helper.assertTrue(acc <= MAX_ACCOUNTER, "NbtAccounter of one pouch " + acc + " > " + MAX_ACCOUNTER);

        // double chest: 54 chest slots + 36 of the player, all worst pouches (all different)
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer chest = new SimpleContainer(54);
        for (int i = 0; i < 54; i++) {
            chest.setItem(i, worstPouch(r));
        }
        fill(player, r, p -> 36);
        ChestMenu doubleChest = ChestMenu.sixRows(1, player.getInventory(), chest);
        long pouches = doubleChest.getItems().stream().filter(s -> s.is(ModItems.SEED_POUCH.get())).count();
        helper.assertTrue(doubleChest.getItems().size() == 90 && pouches == 90, "double chest menu " + pouches + " pouches");
        byte[] chestPacket = contentPacket(doubleChest);

        // the player's own inventory menu: 36 + 4 armour + offhand = 41 pouches of 46 slots
        fill(player, r, p -> 41);
        long invPouches = player.inventoryMenu.getItems().stream().filter(s -> s.is(ModItems.SEED_POUCH.get())).count();
        helper.assertTrue(player.inventoryMenu.getItems().size() == 46 && invPouches == 41, "inventory " + invPouches);
        byte[] invPacket = contentPacket(player.inventoryMenu);

        LOG.info("[WR-197] SEC-12: adversarial lot {} B (NbtBudget), pouch of 16: NbtBudget {} B, item tag NbtIo {} B, "
                        + "NbtAccounter {} (limit {}); double chest menu 90 pouches: {} B raw / {} B deflated (limit {}); "
                        + "InventoryMenu 41 pouches: {} B raw / {} B deflated",
                lr.bytes(), pr.bytes(), pouchNbt, acc, MAX_ACCOUNTER, chestPacket.length, deflate(chestPacket), MAX_PACKET,
                invPacket.length, deflate(invPacket));
        helper.assertTrue(chestPacket.length <= MAX_PACKET, "double chest packet " + chestPacket.length + " B > " + MAX_PACKET);
        helper.assertTrue(invPacket.length <= MAX_PACKET, "inventory packet " + invPacket.length + " B > " + MAX_PACKET);
        helper.succeed();
    }
}
