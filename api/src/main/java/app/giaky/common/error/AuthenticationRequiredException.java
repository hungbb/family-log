package app.giaky.common.error;

/** 401 — no credentials, or credentials that no longer hold. */
public non-sealed class AuthenticationRequiredException extends ApiException {

    public AuthenticationRequiredException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public AuthenticationRequiredException(String detail) {
        super(ErrorCode.AUTH_REQUIRED, detail);
    }
}
