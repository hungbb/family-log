package app.giaky.common.error;

import java.util.List;

/**
 * Base of the application's exception hierarchy. Everything thrown deliberately carries an
 * {@link ErrorCode}, which fixes both the HTTP status and the {@code code} the client reads.
 *
 * <p>Sealed at the root so {@code GlobalExceptionHandler} is exhaustive over the intended cases; the
 * five permitted subclasses are open, and later phases extend those rather than this.
 */
public abstract sealed class ApiException extends RuntimeException
        permits AuthenticationRequiredException,
                ConflictException,
                DomainRuleViolation,
                NotFoundException,
                PermissionDeniedException {

    private final transient ErrorCode code;
    private final transient List<FieldViolation> errors;

    protected ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of(), null);
    }

    protected ApiException(ErrorCode code, String detail, List<FieldViolation> errors) {
        this(code, detail, errors, null);
    }

    protected ApiException(
            ErrorCode code, String detail, List<FieldViolation> errors, Throwable cause) {
        super(detail, cause);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode code() {
        return code;
    }

    /** Field-level detail for the {@code errors} extension; empty when the error is not field-bound. */
    public List<FieldViolation> errors() {
        return errors;
    }
}
