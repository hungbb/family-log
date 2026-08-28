package app.giaky.authorization.api;

import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Enforces {@link RequiresPermission} before the controller method runs (doc 04 §5.1, doc 06 §4.2).
 *
 * <p>A {@code HandlerInterceptor} rather than an AOP aspect. Doc 06 §4.2 names either; this one wins
 * on two counts. The path variables are already parsed and sitting in a request attribute here,
 * where an aspect would have to re-derive them from method parameters and their annotations, and it
 * needs no weaver on the classpath for a check that runs once per request. The trade is that it only
 * sees controller methods — which is exactly what the annotation is for.
 *
 * <p>Refusals are thrown, not written. {@code GlobalExceptionHandler} renders every failure in the
 * product as RFC 7807 (doc 05 §2), and a second renderer living in an interceptor is the one that
 * would drift.
 */
public class RequiresPermissionInterceptor implements HandlerInterceptor {

    private final PermissionResolver resolver;

    public RequiresPermissionInterceptor(PermissionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            return true;
        }
        AuthenticatedUser caller = currentUser();
        Map<String, String> pathVariables = pathVariables(request);

        ClanId clanId =
                uuid(pathVariables.get(required.clanIdParam()))
                        .map(ClanId::of)
                        .orElseThrow(RequiresPermissionInterceptor::noSuchClan);

        // Doc 04 §7. Before any permission question: a non-member is told the clan does not exist,
        // because clan codes must not be enumerable and a 403 would confirm one.
        if (!resolver.isMember(caller.id(), clanId)) {
            throw noSuchClan();
        }

        Optional<BranchId> branchId =
                required.branchIdParam().isEmpty()
                        ? Optional.empty()
                        : uuid(pathVariables.get(required.branchIdParam())).map(BranchId::of);

        if (branchId.isPresent()) {
            // The URL names a precise target, so ask the precise question — and let the resolver
            // phrase the refusal, naming the branch the caller does hold (doc 04 §7).
            resolver.require(caller.id(), required.value(), Target.branch(clanId, branchId.get()));
            return true;
        }

        // Otherwise the coarse question, "do they hold this anywhere here?". Anything narrower
        // would refuse requests the service is about to allow, which is the one thing a pre-check
        // must never do.
        if (!resolver.permissionsOf(caller.id(), clanId).contains(required.value())) {
            throw new PermissionDeniedException(
                    "You do not hold " + required.value() + " in this clan.");
        }
        return true;
    }

    /**
     * The signed-in caller.
     *
     * <p>Read from the security context the same way {@code CurrentUserArgumentResolver} does. The
     * chain has normally answered 401 long before this runs; the throw covers the routes it leaves
     * open, and costs nothing on the ones it does not.
     */
    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationRequiredException(
                ErrorCode.AUTH_REQUIRED, "This endpoint requires a signed-in user.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return attribute instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private static Optional<UUID> uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static NotFoundException noSuchClan() {
        return new NotFoundException(
                ErrorCode.CLAN_NOT_FOUND, "That clan does not exist, or you are not a member of it.");
    }
}
