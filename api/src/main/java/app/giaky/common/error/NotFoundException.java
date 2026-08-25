package app.giaky.common.error;

/**
 * 404 — the resource does not exist, <i>or</i> the caller may not know that it does.
 *
 * <p>Cross-tenant lookups deliberately land here rather than on a 403, so an id cannot be probed for
 * existence from outside its clan.
 */
public non-sealed class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public NotFoundException(String detail) {
        super(ErrorCode.RESOURCE_NOT_FOUND, detail);
    }
}
