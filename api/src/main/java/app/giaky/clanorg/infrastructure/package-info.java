/**
 * Clan organisation — clans, branches, memberships, join requests.
 *
 * <p>Infrastructure layer. JPA entities ({@code *JpaEntity}), Spring Data repositories, MapStruct
 * mappers, native-SQL projections and adapters implementing this context's ports.
 *
 * <p><b>Rules</b> (enforced by {@code architecture.LayeringTest}):
 * <ul>
 *   <li>Implements ports declared in {@code domain}; the dependency arrow points inwards.</li>
 *   <li>Every clan-scoped repository read takes a {@code ClanId}, or is annotated
 *       {@code @TenantExempt} with a justification.</li>
 *   <li>Never referenced by {@code domain} or by another context.</li>
 * </ul>
 */
package app.giaky.clanorg.infrastructure;
