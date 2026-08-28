package app.giaky.architecture.fixtures.application;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import org.springframework.transaction.annotation.Transactional;

/**
 * A service that forgot its permission check — the fixture {@code AuthorizationCoverageTest} points
 * its rule at to prove the rule bites.
 *
 * <p>Phase 3 §5 asks for exactly this: the structural guarantee "verified with a deliberately broken
 * fixture". A rule that has only ever been run against compliant code is a rule nobody has tested,
 * and the failure mode — silently passing because of a typo in a package name or an annotation
 * predicate — is invisible until the day it matters.
 *
 * <p>It lives in the test tree, in a package ending {@code .application} so the rule's package
 * predicate matches it, and the production run of the rule imports with {@code
 * DO_NOT_INCLUDE_TESTS}, so this class can never fail the real build.
 */
@SuppressWarnings("unused")
public class UnguardedClanService {

    /** Mutating, transactional, and nothing asked whether the caller may do it. */
    @Transactional
    public void renameClan(UserId caller, ClanId clanId, String newName) {
        // Deliberately empty. The body is irrelevant; the absent require(...) is the point.
    }
}
