package io.github.slugblue.wildroots.core.safety;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entry point that the game calls (random tick, {@code onLoad},
 * {@code use}/{@code useOn}, event handlers, mixin handlers): its logic runs
 * through {@link Guarded} (SEC-34). ArchUnit {@code entryPointsAreGuarded}
 * checks that every marked method calls {@code Guarded}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface EntryPoint {
}
