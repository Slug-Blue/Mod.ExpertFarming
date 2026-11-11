package io.github.slugblue.wildroots.archfixtures.feature;

import io.github.slugblue.wildroots.archfixtures.client.ClientHandlers;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Violation (WR-198): an annotation whose class value is a client class. */
@AnnotatedWithClientValue.Uses(ClientHandlers.class)
public class AnnotatedWithClientValue {
    @Retention(RetentionPolicy.CLASS)
    @interface Uses {
        Class<?> value();
    }
}
