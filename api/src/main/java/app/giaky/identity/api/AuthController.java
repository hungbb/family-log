package app.giaky.identity.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.identity.api.dto.ForgotPasswordRequest;
import app.giaky.identity.api.dto.LoginRequest;
import app.giaky.identity.api.dto.LogoutRequest;
import app.giaky.identity.api.dto.RefreshRequest;
import app.giaky.identity.api.dto.RegisterRequest;
import app.giaky.identity.api.dto.RegisterResponse;
import app.giaky.identity.api.dto.ResetPasswordRequest;
import app.giaky.identity.api.dto.TokenResponse;
import app.giaky.identity.api.dto.VerifyEmailRequest;
import app.giaky.identity.application.AuthService;
import app.giaky.identity.application.LoginCommand;
import app.giaky.identity.application.RegisterCommand;
import app.giaky.identity.domain.LocaleTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authentication endpoints of doc 05 §4.1.
 *
 * <p>Bind, validate, delegate, map — the whole of it. Every decision about what a failure reveals
 * lives in {@code AuthService}; a controller that shaped its own error would be a second place for
 * that judgement to be made, and the second place is always the one that leaks.
 *
 * <p>Served under {@code /api/v1} by the prefix {@code WebConfig} applies, and left public by
 * {@code SecurityConfig} — this is where a token comes from. {@code logout-all} is the exception:
 * it is on a public path but needs a caller, which {@code @CurrentUser} enforces.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, sessions and recovery (FR-A1…A6)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register an account",
            description =
                    "Creates an account in PENDING_VERIFICATION and issues a verification token."
                        + " The account may sign in immediately. Returns 409 if the address is"
                        + " already registered, and 422 if the password fails the policy.")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {

        return RegisterResponse.of(
                authService.register(
                        new RegisterCommand(
                                request.email(),
                                request.password(),
                                request.displayName(),
                                request.locale() == null
                                        ? null
                                        : LocaleTag.fromTagOrDefault(request.locale()),
                                ClientContexts.from(httpRequest))));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Sign in",
            description =
                    "Returns an access token, a refresh token and the account. Every failure —"
                        + " unknown address, wrong password, suspended account — answers 401 with"
                        + " the same message.")
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {

        return TokenResponse.of(
                authService.login(
                        new LoginCommand(
                                request.email(),
                                request.password(),
                                ClientContexts.from(httpRequest))));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate the token pair",
            description =
                    "Spends the presented refresh token and returns its successor. Presenting an"
                        + " already-spent token revokes every session in its chain.")
    public TokenResponse refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {

        return TokenResponse.of(
                authService.refresh(request.refreshToken(), ClientContexts.from(httpRequest)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "End this session",
            description =
                    "Revokes the presented refresh token. Succeeds whatever it is given, so a client"
                        + " whose token already expired can still sign out cleanly.")
    public void logout(
            @Valid @RequestBody LogoutRequest request,
            @CurrentUser(required = false) AuthenticatedUser caller) {

        authService.logout(request.refreshToken(), caller == null ? null : caller.id());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "End every session",
            description =
                    "Revokes all refresh tokens and invalidates outstanding access tokens"
                        + " immediately by bumping the account's token version.")
    public void logoutAll(@CurrentUser AuthenticatedUser caller) {
        authService.logoutAll(caller.id());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm an email address", description = "Idempotent.")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Request a password reset",
            description =
                    "Always 202, whether or not the address has an account. Anything else would"
                        + " make this an account-enumeration oracle.")
    public void forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {

        authService.forgotPassword(request.email(), ClientContexts.from(httpRequest));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Set a new password",
            description = "Spends the reset token and revokes every session for the account.")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
    }
}
