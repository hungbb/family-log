package app.giaky.linking.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.linking.api.dto.NodeClaimResponse;
import app.giaky.linking.application.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own claims — {@code GET /me/claims}, doc 05 §4.2.
 *
 * <p>Its own controller rather than a method on {@link ClaimController}, because the path is not
 * clan-scoped: this list spans every family the person has claimed a node in, and {@code /me} is
 * where somebody goes to find out what came of what they asked for (F6.4).
 */
@RestController
@Tag(name = "Node claims", description = "Asking to be recognised as a person in the tree")
@SecurityRequirement(name = "bearerAuth")
public class MyClaimsController {

    private final ClaimService claims;

    public MyClaimsController(ClaimService claims) {
        this.claims = claims;
    }

    @GetMapping("/me/claims")
    @Operation(
            summary = "Your own claims",
            description =
                    "Across every clan, newest first, with the reason behind any refusal — which is"
                        + " the whole point of requiring one.")
    public List<NodeClaimResponse> mine(@CurrentUser AuthenticatedUser caller) {
        return claims.myClaims(caller.id()).stream().map(NodeClaimResponse::of).toList();
    }
}
