package io.github.slugblue.wildroots.archfixtures.genetics;
/** Violates WR-101: String.hashCode as a key. */
public class UsesStringHashCode { int key(String id) { return id.hashCode(); } }
