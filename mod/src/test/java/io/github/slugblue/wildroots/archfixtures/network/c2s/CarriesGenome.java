package io.github.slugblue.wildroots.archfixtures.network.c2s;

/** Fixture: a C→S packet that sends state instead of an intent (violates SEC-04). */
public record CarriesGenome(long id, long[] genome, double grade) {
}
