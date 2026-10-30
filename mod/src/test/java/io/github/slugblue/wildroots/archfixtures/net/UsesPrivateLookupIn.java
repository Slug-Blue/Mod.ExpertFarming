package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesPrivateLookupIn {
    Object f() throws Exception { return java.lang.invoke.MethodHandles.privateLookupIn(String.class, java.lang.invoke.MethodHandles.lookup()); }
}
