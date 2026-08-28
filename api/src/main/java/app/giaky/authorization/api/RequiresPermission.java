package app.giaky.authorization.api;

import app.giaky.authorization.domain.Permission;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A coarse permission pre-check on a controller method, from its path variables (doc 04 §5.1, B3.8).
 *
 * <p><b>It is a fast fail and nothing more.</b> The authoritative check is the {@code
 * permissionResolver.require(...)} call inside the application service, which sees the loaded
 * aggregate and the real target; this one sees only the ids in the URL. Two consequences, and both
 * are deliberate:
 *
 * <ul>
 *   <li>An endpoint may carry this annotation <i>and</i> still be refused by the service. Good — the
 *       service knows more.
 *   <li>An endpoint may carry no annotation at all and still be safe, because the service checks.
 *       The annotation is not what makes a route protected, so its absence is not a vulnerability;
 *       {@code architecture.AuthorizationCoverageTest} is what makes forgetting the real check
 *       impossible.
 * </ul>
 *
 * <p>What it buys is the cheap half: a request from somebody with no membership at all is refused
 * before a transaction opens, and the route's intent is legible in the controller instead of only in
 * the service it delegates to.
 *
 * @see RequiresPermissionInterceptor
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    /** The permission the caller needs. */
    Permission value();

    /** The path variable holding the clan id. */
    String clanIdParam() default "clanId";

    /**
     * A path variable holding a branch id to check against, when the route has one.
     *
     * <p>Empty means "held anywhere in this clan" — deliberately the weakest useful question. A
     * pre-check must never refuse something the service would have allowed, and the URL of {@code
     * PUT /persons/{personId}} says nothing about which branch that person sits in; asking for the
     * permission at <i>clan</i> scope there would lock out every branch editor in the product.
     */
    String branchIdParam() default "";
}
