package app.giaky.common.audit;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry for the audit trail.
 *
 * <p>Assembled at the call site and handed to {@link AuditWriter}; the correlation id and the
 * timestamp are the writer's job, because a caller that had to remember them would eventually
 * forget.
 *
 * @param action what happened
 * @param targetType the kind of thing it happened to, e.g. {@code USER}
 * @param targetId that thing's id; null when the target could not be identified
 * @param actorUserId who did it; null for anonymous or system actions
 * @param clanId the clan it happened in; null for actions outside any clan, which is all of Phase 1
 * @param beforeJson state before, as JSON; null when there was none
 * @param afterJson state after, as JSON; null when there is none
 * @param authorizingGrantId the permission grant that allowed the action (FR-Z8); null when nothing
 *     had to be authorized — registration, a scheduled sweep, an action open to any member
 * @param ipAddress where the request came from
 */
public record AuditRecord(
        AuditAction action,
        String targetType,
        UUID targetId,
        UserId actorUserId,
        ClanId clanId,
        String beforeJson,
        String afterJson,
        UUID authorizingGrantId,
        String ipAddress) {

    /** The {@code targetType} for anything that happens to an account. */
    public static final String TARGET_USER = "USER";

    public AuditRecord {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
    }

    /** An action performed by an account upon itself — registration, login, logout, reset. */
    public static AuditRecord ofUser(AuditAction action, UserId subject) {
        return new AuditRecord(
                action, TARGET_USER, subject.value(), subject, null, null, null, null, null);
    }

    /** An action against no identifiable account, such as a login for an address nobody owns. */
    public static AuditRecord anonymous(AuditAction action) {
        return new AuditRecord(action, TARGET_USER, null, null, null, null, null, null, null);
    }

    /** The {@code targetType} for anything that happens to a clan. */
    public static final String TARGET_CLAN = "CLAN";

    /** The {@code targetType} for anything that happens to a branch. */
    public static final String TARGET_BRANCH = "BRANCH";

    /** The {@code targetType} for a membership row. */
    public static final String TARGET_MEMBERSHIP = "MEMBERSHIP";

    /** The {@code targetType} for a join request. */
    public static final String TARGET_JOIN_REQUEST = "JOIN_REQUEST";

    /** The {@code targetType} for a permission grant. */
    public static final String TARGET_PERMISSION_GRANT = "PERMISSION_GRANT";

    /** The {@code targetType} for a person node. */
    public static final String TARGET_PERSON = "PERSON";

    /** The {@code targetType} for a parent-child edge. */
    public static final String TARGET_PARENTAGE = "PARENTAGE";

    /** The {@code targetType} for a marriage. */
    public static final String TARGET_UNION = "UNION";

    /** The {@code targetType} for a node claim request. */
    public static final String TARGET_CLAIM = "CLAIM";

    /** The {@code targetType} for a platform-wide setting, such as the mail transport. */
    public static final String TARGET_PLATFORM_SETTING = "PLATFORM_SETTING";

    /**
     * Something a super admin did to somebody else's account.
     *
     * <p>Distinct from {@link #ofUser}, whose actor and target are the same person: here they are
     * two, and the difference is the whole of what an investigator wants to know.
     */
    public static AuditRecord byOperator(AuditAction action, UserId operator, UserId subject) {
        return new AuditRecord(
                action, TARGET_USER, subject.value(), operator, null, null, null, null, null);
    }

    /** A super admin changed a platform-wide setting. There is no row id to name. */
    public static AuditRecord onPlatformSetting(AuditAction action, UserId operator) {
        return new AuditRecord(
                action, TARGET_PLATFORM_SETTING, null, operator, null, null, null, null, null);
    }

    /**
     * An action inside a clan.
     *
     * <p>The {@code clanId} is what makes the entry reachable: {@code ix_audit_clan_time} is the
     * index the clan audit view of doc 05 §4.11 reads, and an entry written without one is a row
     * only a platform operator will ever see again.
     *
     * @param actorUserId who did it; null for the scheduled sweeps, which have no human behind them
     */
    public static AuditRecord inClan(
            AuditAction action,
            ClanId clanId,
            String targetType,
            UUID targetId,
            UserId actorUserId) {

        return new AuditRecord(
                action, targetType, targetId, actorUserId, clanId, null, null, null, null);
    }

    public AuditRecord withIpAddress(String value) {
        return new AuditRecord(
                action,
                targetType,
                targetId,
                actorUserId,
                clanId,
                beforeJson,
                afterJson,
                authorizingGrantId,
                value);
    }

    public AuditRecord withAfterJson(String value) {
        return new AuditRecord(
                action,
                targetType,
                targetId,
                actorUserId,
                clanId,
                beforeJson,
                value,
                authorizingGrantId,
                ipAddress);
    }

    public AuditRecord withChange(String before, String after) {
        return new AuditRecord(
                action,
                targetType,
                targetId,
                actorUserId,
                clanId,
                before,
                after,
                authorizingGrantId,
                ipAddress);
    }

    /**
     * Records <i>why</i> the actor was allowed to do this (FR-Z8).
     *
     * <p>Fed from {@code AccessDecision.authorizingGrantId}, which is why the resolver returns a
     * decision rather than a boolean. Without it the trail says a person changed something and a
     * different person had granted them something, and joining the two is guesswork — with it,
     * every mutation names the row that permitted it, back to the founder's bootstrap grant.
     */
    public AuditRecord authorizedBy(UUID grantId) {
        return new AuditRecord(
                action,
                targetType,
                targetId,
                actorUserId,
                clanId,
                beforeJson,
                afterJson,
                grantId,
                ipAddress);
    }
}
