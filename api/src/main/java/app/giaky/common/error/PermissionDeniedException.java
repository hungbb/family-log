package app.giaky.common.error;

/**
 * 403 — the caller is known but does not hold the permission over the target.
 *
 * <p>The message is safe to show a user: it names the permission, never why the check failed.
 */
public non-sealed class PermissionDeniedException extends ApiException {

    public PermissionDeniedException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public PermissionDeniedException(String detail) {
        super(ErrorCode.PERMISSION_DENIED, detail);
    }
}
