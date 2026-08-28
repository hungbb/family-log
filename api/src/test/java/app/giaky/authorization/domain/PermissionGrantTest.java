package app.giaky.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.support.ResolverFixture;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The grant aggregate and the two value objects it is built from — I-G1, I-G2 and the catalogue. */
class PermissionGrantTest {

    private static final ClanId CLAN = ResolverFixture.newClanId();
    private static final UserId GRANTEE = ResolverFixture.newUserId();
    private static final UserId GRANTER = ResolverFixture.newUserId();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("I-G1: a clan scope names nothing, and the others must name something")
    void scopeReferenceMatchesScopeType() {
        assertThat(PermissionScope.clan().refId()).isNull();
        assertThat(PermissionScope.clan().isClanWide()).isTrue();

        BranchId branch = ResolverFixture.newBranchId();
        assertThat(PermissionScope.branch(branch).branchId()).isEqualTo(branch);

        assertThatThrownBy(() -> PermissionScope.of(ScopeType.CLAN, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I-G1");
        assertThatThrownBy(() -> PermissionScope.of(ScopeType.BRANCH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I-G1");
        assertThatThrownBy(() -> PermissionScope.of(ScopeType.SUBTREE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionScope.of(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("branchId() is only meaningful for a branch scope")
    void branchIdIsScopeSpecific() {
        assertThatThrownBy(() -> PermissionScope.clan().branchId())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PermissionScope.subtree(UUID.randomUUID()).branchId())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a scope prints in a form safe to put in a denial message")
    void scopeDescribesItself() {
        BranchId branch = ResolverFixture.newBranchId();
        assertThat(PermissionScope.clan().toString()).isEqualTo("CLAN");
        assertThat(PermissionScope.branch(branch).toString()).contains(branch.value().toString());
    }

    @Test
    @DisplayName("I-G2: CLAN_ADMIN cannot be scoped to a branch")
    void adminIsClanWideOnly() {
        assertThatThrownBy(
                        () ->
                                PermissionGrant.issue(
                                        UUID.randomUUID(),
                                        CLAN,
                                        GRANTEE,
                                        Permission.CLAN_ADMIN,
                                        PermissionScope.branch(ResolverFixture.newBranchId()),
                                        GRANTER,
                                        null,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLAN_ADMIN");
    }

    @Test
    @DisplayName("MEMBER_APPROVE has no subtree form")
    void memberApproveHasNoSubtreeForm() {
        assertThat(Permission.MEMBER_APPROVE.isValidAt(ScopeType.SUBTREE)).isFalse();
        assertThat(Permission.MEMBER_APPROVE.isValidAt(ScopeType.BRANCH)).isTrue();
        assertThat(Permission.TREE_EDIT.validScopes()).hasSize(3);
        assertThat(Permission.CLAN_ADMIN.validScopes()).containsExactly(ScopeType.CLAN);
    }

    @Test
    @DisplayName("revocation is a timestamp, and idempotent")
    void revocationKeepsHistory() {
        PermissionGrant grant = liveGrant();
        assertThat(grant.isRevoked()).isFalse();
        assertThat(grant.isEffectiveAt(NOW)).isTrue();

        Instant firstRevocation = NOW.plusSeconds(60);
        PermissionGrant revoked = grant.revoke(GRANTER, firstRevocation);

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.isEffectiveAt(NOW.plusSeconds(120))).isFalse();
        // A double-click on "revoke" must not rewrite when it happened.
        assertThat(revoked.revoke(GRANTEE, NOW.plusSeconds(600)).revokedAt())
                .isEqualTo(firstRevocation);
        assertThat(revoked.revoke(GRANTEE, NOW.plusSeconds(600)).revokedBy()).isEqualTo(GRANTER);
    }

    @Test
    @DisplayName("the expiry sweep may revoke without an actor; a live grant may not name one")
    void systemRevocationHasNoActor() {
        PermissionGrant swept = liveGrant().revoke(null, NOW.plusSeconds(60));
        assertThat(swept.isRevoked()).isTrue();
        assertThat(swept.revokedBy()).isNull();

        assertThatThrownBy(
                        () ->
                                new PermissionGrant(
                                        UUID.randomUUID(),
                                        CLAN,
                                        GRANTEE,
                                        Permission.TREE_EDIT,
                                        PermissionScope.clan(),
                                        GRANTER,
                                        NOW,
                                        null,
                                        null,
                                        GRANTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("live grant");
    }

    @Test
    @DisplayName("expiry is evaluated against the clock, not assumed swept")
    void expiryIsEvaluated() {
        Instant expiry = NOW.plusSeconds(3600);
        PermissionGrant grant =
                PermissionGrant.issue(
                        UUID.randomUUID(),
                        CLAN,
                        GRANTEE,
                        Permission.TREE_EDIT,
                        PermissionScope.clan(),
                        GRANTER,
                        expiry,
                        NOW);

        assertThat(grant.isEffectiveAt(expiry.minusSeconds(1))).isTrue();
        assertThat(grant.isEffectiveAt(expiry)).as("expiry is exclusive").isFalse();
        assertThat(grant.hasExpiredBy(expiry)).isTrue();
        assertThat(grant.hasExpiredBy(expiry.minusSeconds(1))).isFalse();
        assertThat(liveGrant().hasExpiredBy(NOW.plusSeconds(1_000_000))).isFalse();
    }

    @Test
    @DisplayName("an expiry in the past is not a grant, it is a mistake")
    void expiryMustFollowIssue() {
        assertThatThrownBy(
                        () ->
                                PermissionGrant.issue(
                                        UUID.randomUUID(),
                                        CLAN,
                                        GRANTEE,
                                        Permission.TREE_EDIT,
                                        PermissionScope.clan(),
                                        GRANTER,
                                        NOW.minusSeconds(1),
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    @DisplayName("the implication rule, stated once and asked here")
    void implications() {
        assertThat(Permission.CLAN_ADMIN.implies(Permission.TREE_EDIT)).isTrue();
        assertThat(Permission.TREE_EDIT.implies(Permission.CLAN_VIEW)).isTrue();
        assertThat(Permission.TREE_EDIT.implies(Permission.TREE_EDIT)).isTrue();
        assertThat(Permission.TREE_EDIT.implies(Permission.ROLE_MANAGE)).isFalse();
        assertThat(Permission.CLAN_VIEW.implies(Permission.CLAN_ADMIN)).isFalse();
        assertThat(liveGrant().confers(Permission.CLAN_VIEW)).isTrue();
    }

    @Test
    @DisplayName("permission names parse case-insensitively, and unknown ones are empty")
    void parsing() {
        assertThat(Permission.parse("tree_edit")).contains(Permission.TREE_EDIT);
        assertThat(Permission.parse("  CLAN_ADMIN  ")).contains(Permission.CLAN_ADMIN);
        assertThat(Permission.parse("PATRIARCH")).isEmpty();
        assertThat(Permission.parse("")).isEmpty();
        assertThat(Permission.parse(null)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("the required fields are required")
    void nullChecks() {
        assertThatThrownBy(
                        () ->
                                PermissionGrant.issue(
                                        null,
                                        CLAN,
                                        GRANTEE,
                                        Permission.TREE_EDIT,
                                        PermissionScope.clan(),
                                        GRANTER,
                                        null,
                                        NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                PermissionGrant.issue(
                                        UUID.randomUUID(),
                                        CLAN,
                                        GRANTEE,
                                        Permission.TREE_EDIT,
                                        PermissionScope.clan(),
                                        GRANTER,
                                        null,
                                        null))
                .isInstanceOf(NullPointerException.class);
    }

    private static PermissionGrant liveGrant() {
        return PermissionGrant.issue(
                UUID.randomUUID(),
                CLAN,
                GRANTEE,
                Permission.TREE_EDIT,
                PermissionScope.clan(),
                GRANTER,
                null,
                NOW);
    }
}
