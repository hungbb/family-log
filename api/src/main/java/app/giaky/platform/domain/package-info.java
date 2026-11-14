/**
 * Platform — what the whole installation shares rather than any one clan: the settings a super
 * admin edits at runtime, starting with the outgoing mail transport.
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
 * </ul>
 */
package app.giaky.platform.domain;
