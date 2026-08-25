/**
 * Notification — in-app notifications and the event listeners that raise them.
 *
 * <p>Domain layer. Aggregates, value objects, domain services and ports.
 *
 * <p><b>Rules</b> (enforced by {@code architecture.LayeringTest}):
 * <ul>
 *   <li>Depends on nothing but the JDK and {@code app.giaky.common.types} — no Spring, no JPA,
 *       no Jackson, no {@code infrastructure}, no {@code api}.</li>
 *   <li>Persistence-ignorant: JPA entities live in {@code infrastructure} and are mapped across.</li>
 *   <li>Never reaches into another bounded context; cross-context access goes through the other
 *       context's published application services or domain events.</li>
 *   <li>Unit-testable without Spring and without a database.</li>
 * </ul>
 */
package app.giaky.notification.domain;
