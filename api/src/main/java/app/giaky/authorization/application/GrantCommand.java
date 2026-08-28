package app.giaky.authorization.application;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * "Give this person this permission, here, until then" — the input to {@link GrantService#grant}.
 *
 * <p>Scope arrives as a raw type name and id rather than as a {@code PermissionScope}, because an
 * unknown scope type is a 422 the caller wrote and not an exception thrown while binding a DTO.
 * {@code GrantService} does the parsing and owns the error.
 *
 * @param expiresAt when the grant should lapse; null for one that stands until revoked
 */
public record GrantCommand(
        UserId granteeUserId,
        String permission,
        String scopeType,
        UUID scopeRefId,
        Instant expiresAt) {}
