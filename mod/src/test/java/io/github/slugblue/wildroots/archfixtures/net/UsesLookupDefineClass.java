package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesLookupDefineClass {
    Object f(byte[] b) throws Exception { return java.lang.invoke.MethodHandles.lookup().defineClass(b); }
}
