package app.giaky.linking.application;

import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.linking.domain.LinkableNode;
import app.giaky.linking.domain.PersonUserLinker;
import app.giaky.linking.domain.ProfilePropagationRepository;
import app.giaky.linking.domain.PropagationFlags;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>The self-service exception</b> of doc 04 §6, and nothing else (B6.7, B6.8).
 *
 * <p>A member linked to a node may edit two fields on it — their photo and their notes — without
 * holding {@code TREE_EDIT}. Everything else about that node, their name included, still needs the
 * permission and still goes through {@code PersonService}, where the resolver decides.
 *
 * <p><b>Why it is a service and not a resolver rule.</b> Doc 04 §6 is explicit: a special case
 * inside {@code PermissionResolver} would be a second policy in the one place the product promises
 * there is only one, and it would apply to every call site rather than to this one. Here, the
 * exception is a class you can read in a minute and a whitelist you cannot express past.
 *
 * <p><b>The whitelist is a shape, not a check.</b> {@link SelfProfileCommand} has two editable
 * fields and no others; the statement underneath it names two columns and no others. There is no
 * runtime list to fall out of step with the code. Phase 6 §8 asks that extending it require an ADR —
 * and extending it means changing this class, that record and that statement, which is hard to do by
 * accident.
 *
 * <p><b>What a caller who is not the linked user gets:</b> {@code 403}. Not {@code 404} — they can
 * see the node, they simply are not it.
 */
@Service
public class SelfProfileService {

    private static final Logger log = LoggerFactory.getLogger(SelfProfileService.class);

    private final PersonUserLinker linker;
    private final PersonLinkGateway lineage;
    private final ProfilePropagationRepository propagation;
    private final PermissionResolver resolver;
    private final UserDirectory users;
    private final AuditWriter auditWriter;

    public SelfProfileService(
            PersonUserLinker linker,
            PersonLinkGateway lineage,
            ProfilePropagationRepository propagation,
            PermissionResolver resolver,
            UserDirectory users,
            AuditWriter auditWriter) {

        this.linker = linker;
        this.lineage = lineage;
        this.propagation = propagation;
        this.resolver = resolver;
        this.users = users;
        this.auditWriter = auditWriter;
    }

    /** The flags as they stand, so the form can render what the person chose last time. */
    @Transactional(readOnly = true)
    public PropagationFlags flags(UserId caller, ClanId clanId, PersonId personId) {
        resolver.requireMembership(caller, clanId);
        requireOwnNode(caller, clanId, personId);
        return propagation.find(clanId, personId);
    }

    /**
     * Edits the two whitelisted fields on one's own node, and records the propagation choices.
     *
     * @throws PermissionDeniedException the caller is not the node's linked user
     */
    @Transactional
    public LinkedNodeView update(
            UserId caller, ClanId clanId, PersonId personId, SelfProfileCommand command) {

        resolver.requireMembership(caller, clanId);
        LinkableNode node = requireOwnNode(caller, clanId, personId);

        // The statement's own WHERE clause is the second half of the check: even if the read above
        // were somehow stale, it can only write a row whose linked_user_id is the caller.
        boolean written =
                lineage.updateOwnProfile(
                        clanId, personId, caller, command.avatarMediaId(), command.notesHtml());
        if (!written) {
            throw notYours();
        }

        PropagationFlags flags =
                new PropagationFlags(command.propagateAvatar(), command.propagateDisplayName());
        propagation.save(clanId, personId, flags);
        if (flags.anyEnabled()) {
            // Applied immediately as well as on the next profile edit: somebody who has just ticked
            // "use my photo" expects to see it, not to have to re-save their profile.
            applyProfileTo(clanId, personId, caller, flags);
        }

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.PERSON_UPDATED,
                        clanId,
                        AuditRecord.TARGET_PERSON,
                        personId.value(),
                        caller));
        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.PROFILE_PROPAGATION_UPDATED,
                        clanId,
                        AuditRecord.TARGET_PERSON,
                        personId.value(),
                        caller));

        log.info(
                "Self-service profile edit clanId={} personId={} user={} propagateAvatar={}"
                        + " propagateDisplayName={}",
                clanId,
                personId,
                caller,
                flags.avatar(),
                flags.displayName());

        LinkableNode updated = linker.require(clanId, personId);
        return new LinkedNodeView(
                updated.id().value(),
                updated.fullName(),
                clanId.value(),
                caller.value(),
                users.profile(caller).map(UserProfile::displayName).orElse(null),
                true);
    }

    /**
     * Copies the account's opted-in fields onto one node (FR-N6).
     *
     * <p>Package-private and called from two places: here, when somebody turns a flag on, and from
     * {@code ProfilePropagationListener}, when the account itself changes. Both mean the same thing
     * and must not disagree about which columns move.
     */
    void applyProfileTo(ClanId clanId, PersonId personId, UserId userId, PropagationFlags flags) {
        Optional<UserProfile> profile = users.profile(userId);
        if (profile.isEmpty()) {
            return;
        }
        lineage.propagateProfile(
                clanId,
                personId,
                userId,
                flags.avatar(),
                profile.get().avatarMediaId(),
                flags.displayName(),
                profile.get().displayName());
    }

    private LinkableNode requireOwnNode(UserId caller, ClanId clanId, PersonId personId) {
        LinkableNode node = linker.require(clanId, personId);
        if (!node.isLinkedTo(caller)) {
            throw notYours();
        }
        return node;
    }

    private static PermissionDeniedException notYours() {
        return new PermissionDeniedException(
                ErrorCode.PERMISSION_DENIED,
                "You can only edit your own node this way. Editing anybody else's needs TREE_EDIT.");
    }
}
