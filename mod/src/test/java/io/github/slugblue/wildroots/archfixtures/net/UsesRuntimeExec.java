package io.github.slugblue.wildroots.archfixtures.net;
/** SEC-43 violation fixture. */
@SuppressWarnings("all")
public class UsesRuntimeExec {
    Object f() throws Exception { return Runtime.getRuntime().exec(new String[] {"x"}); }
}
