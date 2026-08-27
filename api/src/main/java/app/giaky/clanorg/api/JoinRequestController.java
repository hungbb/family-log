package app.giaky.clanorg.api;

import app.giaky.clanorg.api.dto.JoinRequestResponse;
import app.giaky.clanorg.api.dto.SubmitJoinRequest;
import app.giaky.clanorg.application.JoinRequestService;
import app.giaky.clanorg.application.SubmitJoinRequestCommand;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The requester's side of joining — doc 05 §4.5 and §4.2.
 *
 * <p><b>Not nested under a clan, and it cannot be.</b> Somebody asking to join is not a member, so
 * there is no clan-scoped path they are allowed to reach; the code they hold is the whole of their
 * claim, and the clan is derived from it rather than named in the URL.
 *
 * <p>Both endpoints act on the caller's own requests only. The cancel path looks a request up by id
 * <i>and</i> requester, so an id belonging to somebody else is a 404 rather than an authorization
 * question.
 */
@RestController
@Tag(name = "Join requests (mine)", description = "Asking to join a clan, and following it up (FR-J1, FR-J5)")
@SecurityRequirement(name = "bearerAuth")
public class JoinRequestController {

    private final JoinRequestService joinRequests;

    public JoinRequestController(JoinRequestService joinRequests) {
        this.joinRequests = joinRequests;
    }

    @PostMapping("/join-requests")
    @Operation(
            summary = "Ask to join a clan",
            description =
                    "Give exactly one code. A branch code asks to join that branch, which lets the"
                        + " approver answer yes or no instead of deciding where you belong.")
    public ResponseEntity<JoinRequestResponse> submit(
            @CurrentUser AuthenticatedUser caller, @Valid @RequestBody SubmitJoinRequest request) {

        JoinRequestResponse body =
                JoinRequestResponse.of(
                        joinRequests.submit(
                                caller.id(),
                                new SubmitJoinRequestCommand(
                                        request.clanCode(),
                                        request.branchCode(),
                                        request.message())));

        return ResponseEntity.created(URI.create("/api/v1/join-requests/" + body.id())).body(body);
    }

    @DeleteMapping("/join-requests/{requestId}")
    @Operation(summary = "Withdraw your own pending request")
    public ResponseEntity<Void> cancel(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID requestId) {

        joinRequests.cancel(caller.id(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/join-requests")
    @Operation(
            summary = "Your own join requests",
            description =
                    "Across every clan, including ones you are not a member of — which is the only"
                        + " place a pending applicant can see what came of their request.")
    public List<JoinRequestResponse> mine(@CurrentUser AuthenticatedUser caller) {
        return joinRequests.myRequests(caller.id()).stream().map(JoinRequestResponse::of).toList();
    }
}
