package io.github.slugblue.wildroots.archfixtures.genetics;
/** Violates WR-101: SplittableRandom. */
public class UsesSplittableRandom { long roll() { return new java.util.SplittableRandom(1).nextLong(); } }
