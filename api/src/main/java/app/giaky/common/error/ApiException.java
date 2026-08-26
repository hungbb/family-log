package app.giaky.common.error;

import java.util.List;

/**
 * Base of the application's exception hierarchy. Everything thrown deliberately carries an
 * {@link ErrorCode}, which fixes both the HTTP status and the {@code code} the client reads.
 *
 * <p>Sealed at the root so {@code GlobalExceptionHandler} is exhaustive over the intended cases; the
 * permitted subclasses are open, and a phase extends those rather than this list. Phase 1 is the
 * exception that proves it: {@link TooManyRequestsException} describes the caller's recent history
 * rather than anything about the request, which none of the original five could express.
 */
public abstract sealed class ApiException extends RuntimeException
        permits AuthenticationRequiredException,
                ConflictException,
                DomainRuleViolation,
                NotFoundException,
                PermissionDeniedException,
                TooManyRequestsException {

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
