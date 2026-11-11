package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * WR-195: changes the batch order of a GameTest run (opt-in). Vanilla
 * {@code GameTestRunner} groups tests by batch name with
 * {@code Collectors.groupingBy}, so batches run in {@code HashMap} order of
 * their names and a suite always runs in one fixed order. With
 * {@code WILDROOTS_ORDER_SALT=<n>} this adds {@code n} empty batches with
 * salted names: the map grows and rehashes and the real batches run in
 * another order. Used to show that no test depends on what an earlier batch
 * left in the world. Off by default; CI is unchanged.
 */
@GameTestHolder(WildRoots.MOD_ID)
public final class OrderSaltGameTests {

    private OrderSaltGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> salt() {
        String v = System.getenv("WILDROOTS_ORDER_SALT");
        int n = v == null ? 0 : Integer.parseInt(v.trim());
        List<TestFunction> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new TestFunction("wr_order_salt_" + n + "_" + i, "wildroots.order_salt_" + i,
                    "wildroots:" + SmokeGameTests.FLOOR_3X3X3, 20, 0L, true, h -> h.succeed()));
        }
        return out;
    }
}
