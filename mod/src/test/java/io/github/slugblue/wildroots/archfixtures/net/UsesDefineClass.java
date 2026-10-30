package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesDefineClass {
    static class Loader extends ClassLoader { Class<?> f(byte[] b) { return defineClass("x", b, 0, b.length); } }
}
