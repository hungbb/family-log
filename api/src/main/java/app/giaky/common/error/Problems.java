package app.giaky.common.error;

import app.giaky.common.web.CorrelationId;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 7807 body of doc 05 §2 — the one shape every failure leaves the application in.
 *
 * <p>Separate from {@code GlobalExceptionHandler} because the handler is not the only writer: a
 * servlet filter that rejects a request (rate limiting, an unusable bearer token) never reaches the
 * dispatcher, so it has to render the body itself. Two renderers would drift; this is the one.
 */
public final class Problems {

    private Problems() {}

    /** The catalogue's {@code type}, {@code title}, {@code status} and {@code code}, plus a detail. */
    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(code.status());
        body.setType(URI.create(code.typeUri()));
        body.setTitle(code.title());
        body.setDetail(detail);
        body.setProperty("code", code.name());
        String correlationId = CorrelationId.current();
        if (correlationId != null) {
            body.setProperty("correlationId", correlationId);
        }
        return body;
    }

    /** As {@link #of(ErrorCode, String)}, with the {@code errors} extension populated. */
    public static ProblemDetail of(ErrorCode code, String detail, List<FieldViolation> errors) {
        ProblemDetail body = of(code, detail);
        if (!errors.isEmpty()) {
            body.setProperty("errors", errors);
        }
        return body;
    }
}
