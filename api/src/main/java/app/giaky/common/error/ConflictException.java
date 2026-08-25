package app.giaky.common.error;

/** 409 — the request collides with current state: a stale version, or a duplicate. */
public non-sealed class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, String detail) {
        super(code, detail);
    }
}
