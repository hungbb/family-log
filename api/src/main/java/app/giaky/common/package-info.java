/**
 * Cross-cutting concerns shared by every bounded context.
 *
 * <p><b>Rule:</b> {@code common} holds no business logic and never depends on a bounded context.
 * Anything here must make sense to all of them.
 */
package app.giaky.common;
