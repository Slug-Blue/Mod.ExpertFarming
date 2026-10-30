package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesUriToUrl {
    Object f() throws Exception { return java.net.URI.create("x:y").toURL(); }
}
