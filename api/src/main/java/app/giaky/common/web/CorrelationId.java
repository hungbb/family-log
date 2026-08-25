package app.giaky.common.web;

import org.slf4j.MDC;

/**
 * The per-request correlation id: read from {@code X-Correlation-Id} or generated, held in the MDC
 * for the life of the request, echoed on every response and quoted in every error body.
 */
public final class CorrelationId {

    /** Request and response header carrying the id. */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key; {@code logback-spring.xml} renders it on every line. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {}

    /** The current request's correlation id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
