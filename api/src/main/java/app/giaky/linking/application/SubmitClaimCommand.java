package app.giaky.linking.application;

import app.giaky.common.types.PersonId;

/**
 * {@code POST /clans/{clanId}/claims} (FR-N1).
 *
 * @param evidence what the requester offers to tell themselves apart from a same-named relative;
 *     optional, because somebody claiming the only Nguyễn Thị Lan of their generation has nothing
 *     to add and should not be made to invent something
 */
public record SubmitClaimCommand(PersonId personId, String evidence) {}
