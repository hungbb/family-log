package app.giaky.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the {@link AuthenticatedUser} into a controller method.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * MeResponse me(@CurrentUser AuthenticatedUser caller) { ... }
 * }</pre>
 *
 * <p>Reading the principal out of {@code SecurityContextHolder} inside a controller would work
 * equally well and be untestable and easy to forget; this makes the dependency part of the
 * signature. Resolution fails with a 401 when there is no principal, so an endpoint that asks for a
 * caller can never silently receive {@code null}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {

    /**
     * Whether a caller must be present.
     *
     * <p>Set to {@code false} on the endpoints that work either way: signing out succeeds on the
     * strength of the refresh token alone, so a client whose access token expired first must still
     * be able to do it — while a caller who <i>is</i> signed in should still be named in the audit
     * entry. The parameter is null when nobody is signed in.
     */
    boolean required() default true;
}
