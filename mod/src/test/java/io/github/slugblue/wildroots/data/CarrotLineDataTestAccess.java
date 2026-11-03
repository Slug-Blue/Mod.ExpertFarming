package io.github.slugblue.wildroots.data;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;

/** The shipped carrot line data for JUnit tests of other packages (loaded as in {@link CarrotLineDataTest}). */
public final class CarrotLineDataTestAccess {

    private static final DefinitionsSnapshot SNAPSHOT = CarrotLineDataTest.load().snapshot();

    private CarrotLineDataTestAccess() {
    }

    public static DefinitionsSnapshot snapshot() {
        return SNAPSHOT;
    }

    public static SpeciesDef wildCarrot() {
        return SNAPSHOT.species("wildroots:wild_carrot").orElseThrow().def();
    }
}
