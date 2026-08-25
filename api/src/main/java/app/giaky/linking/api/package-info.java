/**
 * Linking — node claim requests binding a user account to a person node.
 *
 * <p>API layer. REST controllers and their request/response DTOs.
 *
 * <p><b>Rules</b> (enforced by {@code architecture.LayeringTest}):
 * <ul>
 *   <li>May depend on {@code application} and on DTOs; never on repositories or JPA entities.</li>
 *   <li>No business logic: bind, validate, delegate, map.</li>
 *   <li>Never transactional — the application service owns the transaction.</li>
 *   <li>DTOs are records; request DTOs are {@code <Verb><Noun>Request}, responses
 *       {@code <Noun>Response} / {@code <Noun>Summary} / {@code <Noun>Detail}.</li>
 * </ul>
 */
package app.giaky.linking.api;
