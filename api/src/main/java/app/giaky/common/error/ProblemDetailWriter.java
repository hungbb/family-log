package app.giaky.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes a Problem Details body straight onto the servlet response.
 *
 * <p>For everything that runs inside the dispatcher, {@code GlobalExceptionHandler} does this. But a
 * servlet filter that rejects a request — an exhausted rate limit, a bearer token that does not
 * verify — never reaches a controller, and Spring Security's entry points are handed a raw
 * response too. Without this they would each hand-roll a body, and the error contract would hold
 * everywhere except at the edges that matter most.
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Renders {@code code} with the given detail. Does nothing if the response is already committed. */
    public void write(HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        write(response, code, detail, null);
    }

    /** As {@link #write}, additionally setting {@code Retry-After} when a wait is known. */
    public void write(
            HttpServletResponse response, ErrorCode code, String detail, Duration retryAfter)
            throws IOException {

        if (response.isCommitted()) {
            return;
        }
        ProblemDetail body = Problems.of(code, detail);

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (retryAfter != null) {
            long seconds = (Math.max(0L, retryAfter.toMillis()) + 999L) / 1000L;
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
