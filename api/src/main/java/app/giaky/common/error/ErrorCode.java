package app.giaky.common.error;

import org.springframework.http.HttpStatus;

/**
 * The error catalogue from {@code .docs/05-api-specification.md} §2.
 *
 * <p>The {@code code} a client sees is the constant's own name, so this enum <i>is</i> the contract:
 * renaming a constant is a breaking API change.
 */
public enum ErrorCode {

    // 400
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_CLAN_CODE(HttpStatus.BAD_REQUEST, "Invalid clan code"),

    // 401
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token invalid"),

    // 403
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "Permission denied"),
    OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "Out of scope"),
    PERMISSION_ESCALATION_BLOCKED(HttpStatus.FORBIDDEN, "Permission escalation blocked"),
    LAST_ADMIN_PROTECTED(HttpStatus.FORBIDDEN, "Last administrator protected"),
    /**
     * A super admin tried to suspend themselves or another super admin. Super admins are named by
     * configuration, so the only way to remove one is to change that configuration.
     */
    SUPER_ADMIN_PROTECTED(HttpStatus.FORBIDDEN, "Super admin protected"),

    // 404
    CLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Clan not found"),
    PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "Person not found"),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Branch not found"),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Request not found"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // 409
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Version conflict"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email already registered"),
    DUPLICATE_PENDING_REQUEST(HttpStatus.CONFLICT, "Duplicate pending request"),
    NODE_ALREADY_LINKED(HttpStatus.CONFLICT, "Node already linked"),
    USER_ALREADY_LINKED_IN_CLAN(HttpStatus.CONFLICT, "User already linked in clan"),
    MEMBERSHIP_EXISTS(HttpStatus.CONFLICT, "Membership exists"),
    /** I-G5: this person already holds this permission at this exact scope. */
    GRANT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Grant already exists"),
    /**
     * The account is not in a state the requested administrative action applies to — reactivating
     * an account that is not suspended, resending verification to a verified address.
     */
    USER_STATUS_CONFLICT(HttpStatus.CONFLICT, "User status conflict"),

    // 422
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),
    /**
     * A grant was asked for at {@code SUBTREE} scope, which the resolver understands and Phase 4
     * turns on (phase 3 §1). Deliberately its own code rather than a generic validation failure: the
     * scope is not invalid, it is not available yet, and the UI says so in those words.
     */
    SUBTREE_SCOPE_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Subtree scope unavailable"),
    CYCLE_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "Cycle detected"),
    INVALID_RELATIONSHIP(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid relationship"),
    DATE_RANGE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Date range invalid"),
    /** Outgoing mail is switched off, so there is nothing to send a message with. */
    MAIL_NOT_CONFIGURED(HttpStatus.UNPROCESSABLE_ENTITY, "Mail not configured"),
    /**
     * The SMTP server refused or could not be reached. Only the test-message endpoint reports it;
     * real deliveries happen after commit and are logged instead, so a mail outage never fails the
     * account change that asked for the message.
     */
    MAIL_DELIVERY_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Mail delivery failed"),

    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Rate limited"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    /** Human-readable summary of the error class; the RFC 7807 {@code title}. */
    public String title() {
        return title;
    }

    /** The RFC 7807 {@code type} URI, e.g. {@code https://giaky.app/errors/permission-denied}. */
    public String typeUri() {
        return "https://giaky.app/errors/" + name().toLowerCase().replace('_', '-');
    }
}
