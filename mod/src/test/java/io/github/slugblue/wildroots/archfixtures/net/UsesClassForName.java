package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesClassForName {
    Object f() throws Exception { return Class.forName("x"); }
}
