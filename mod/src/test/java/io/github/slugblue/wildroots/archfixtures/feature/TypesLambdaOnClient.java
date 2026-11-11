package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import java.util.function.Consumer;

/** Violation (WR-198): the invokedynamic site carries the client type (instantiated method type). */
public class TypesLambdaOnClient {
    Consumer<ClientHandlers> consumer() {
        return h -> {
        };
    }
}
