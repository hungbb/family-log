package app.giaky.common.error;

/** One entry of the {@code errors} extension: which field was rejected, and why. */
public record FieldViolation(String field, String message) {}
