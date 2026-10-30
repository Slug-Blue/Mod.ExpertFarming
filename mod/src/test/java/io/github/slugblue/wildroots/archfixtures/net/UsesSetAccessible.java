package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesSetAccessible {
    void f(java.lang.reflect.Field field) { field.setAccessible(true); }
}
