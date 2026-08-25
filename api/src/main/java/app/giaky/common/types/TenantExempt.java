package app.giaky.common.types;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method that deliberately reads across clans.
 *
 * <p>The default rule is that every repository read takes a {@link ClanId}; this annotation is the
 * documented way out, and {@code architecture.LayeringTest} enforces that it is the <i>only</i> way
 * out. The {@code justification} is mandatory and is read in review — "it was easier" is not one.
 *
 * <p>Legitimate uses are narrow: platform-operator queries, the user account lookup during login
 * (no clan is known yet), and background jobs that sweep every tenant.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TenantExempt {

    /** Why this read may cross the tenant boundary. */
    String justification();
}
