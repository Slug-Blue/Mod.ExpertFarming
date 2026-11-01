package io.github.slugblue.wildroots.genetics.validation;

import java.util.Objects;

/**
 * One finding of definition validation (docs/05-architecture.md §4.4): JSON
 * path, level and message. {@code ERROR} drops the record, {@code WARN}
 * applies a default or a normalisation.
 */
public record Problem(Severity severity, String path, String message) {

    public enum Severity {
        WARN,
        ERROR
    }

    public Problem {
        Objects.requireNonNull(severity, "severity");
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }

    public static Problem error(String path, String message) {
        return new Problem(Severity.ERROR, path, message);
    }

    public static Problem warn(String path, String message) {
        return new Problem(Severity.WARN, path, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + path + ": " + message;
    }
}
