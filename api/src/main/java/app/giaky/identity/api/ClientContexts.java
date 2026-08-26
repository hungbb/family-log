package app.giaky.identity.api;

import app.giaky.identity.application.ClientContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Reads the client's address and user agent off a request.
 *
 * <p>Binding, not business logic: the application layer needs the two values and must not know that
 * a servlet is where they came from.
 */
final class ClientContexts {

    private ClientContexts() {}

    /**
     * {@code getRemoteAddr()} rather than {@code X-Forwarded-For}: behind a proxy,
     * {@code server.forward-headers-strategy=framework} makes Spring resolve it from headers the
     * proxy is trusted to have set. Reading the header directly would let any client claim any
     * address, and those addresses end up in the audit trail.
     */
    static ClientContext from(HttpServletRequest request) {
        return new ClientContext(
                request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
