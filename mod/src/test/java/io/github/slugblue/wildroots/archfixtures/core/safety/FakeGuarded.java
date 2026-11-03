package io.github.slugblue.wildroots.archfixtures.core.safety;

/** Stand-in for Guarded. */
public final class FakeGuarded {
    public static FakeGuarded shared() {
        return new FakeGuarded();
    }

    public static boolean run(String where, Object key, Runnable body) {
        body.run();
        return true;
    }
}
