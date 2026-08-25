/**
 * Value objects and small abstractions shared across contexts: typed ids, {@code PartialDate},
 * {@code MediaRef}, {@code IdGenerator}.
 *
 * <p><b>Rule:</b> this is the <i>only</i> package outside the JDK that a {@code domain} package may
 * depend on, so it must stay framework-free — no Spring, no JPA, no Jackson.
 */
package app.giaky.common.types;
