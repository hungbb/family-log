/**
 * The exception hierarchy and its mapping onto RFC 7807 Problem Details.
 *
 * <p>Every exception carries a {@code code} from the catalogue in {@code .docs/05-api-specification.md}
 * §2. {@code GlobalExceptionHandler} is the only place that turns an exception into a response body.
 */
package app.giaky.common.error;
