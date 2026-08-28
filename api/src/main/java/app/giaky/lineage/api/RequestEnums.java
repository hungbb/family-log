package app.giaky.lineage.api;

import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;

/**
 * Enum parsing at the edge, with an error a client can act on.
 *
 * <p>Binding an enum directly in a DTO gets you Spring's own message — a 400 quoting the raw value
 * and the Java type name — for what doc 05 §2 says should be a {@code 422 VALIDATION_FAILED} naming
 * the field. Doc 05 §6 also asks clients to tolerate unknown enum values gracefully; the server owes
 * them the same courtesy in reverse, which is a sentence rather than a stack trace.
 *
 * <p>Null passes through as null. A {@code PATCH} that does not mention gender is not a {@code PATCH}
 * with an invalid gender.
 */
final class RequestEnums {

    private RequestEnums() {}

    static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, field + ": no such value — " + raw);
        }
    }
}
