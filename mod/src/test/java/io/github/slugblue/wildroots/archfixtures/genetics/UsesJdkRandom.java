package io.github.slugblue.wildroots.archfixtures.genetics;
/** Violates WR-101: java.util.Random. */
public class UsesJdkRandom { int roll() { return new java.util.Random(1).nextInt(6); } }
