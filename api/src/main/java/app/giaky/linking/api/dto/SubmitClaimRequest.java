package app.giaky.linking.api.dto;

import app.giaky.linking.domain.NodeClaimRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code POST /clans/{clanId}/claims} (FR-N1).
 *
 * <p>The evidence is optional. Somebody claiming the only Nguyen Thi Lan of their generation has
 * nothing to add, and requiring a sentence would have them invent one.
 */
public record SubmitClaimRequest(
        @NotNull UUID personId,
        @Size(max = NodeClaimRequest.EVIDENCE_MAX_LENGTH)
                @Schema(
                        description =
                                "What tells you apart from a same-named relative, in your own"
                                    + " words.")
                String evidence) {}
