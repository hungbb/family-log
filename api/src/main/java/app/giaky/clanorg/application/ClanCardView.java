package app.giaky.clanorg.application;

import java.util.UUID;

/**
 * What somebody holding a code is shown before they ask to join (doc 05 §4.3).
 *
 * <p><b>Everything absent from this record is absent on purpose.</b> The caller is not a member, so
 * they get the family name, where it came from, and how large it is — enough to recognize that this
 * is the right family — and nothing else. No clan id, no description, no member list, nothing that
 * would make the endpoint worth scraping.
 *
 * @param targetBranchName set when the code was a branch code, so the card can say which branch the
 *     request would go to
 * @param alreadyMember the caller is already in; the UI offers to open the clan rather than to ask
 * @param hasPendingRequest the caller has already asked; the UI says so instead of asking again
 */
public record ClanCardView(
        String clanCode,
        String name,
        String originPlace,
        long memberCount,
        UUID targetBranchId,
        String targetBranchName,
        boolean alreadyMember,
        boolean hasPendingRequest) {}
