/**
 * Lineage — persons, unions, parentages and the tree read model.
 *
 * <p>Application layer. Use-case orchestration: commands, results and {@code *Service} classes.
 *
 * <p><b>Rules</b> (enforced by {@code architecture.LayeringTest}):
 * <ul>
 *   <li>May depend on this context's {@code domain} and on ports; never on {@code api}.</li>
 *   <li><b>The transaction boundary.</b> {@code @Transactional} lives here and nowhere else.</li>
 *   <li>Every mutating method calls {@code PermissionResolver.require(...)} or is on the explicit
 *       allow-list asserted by the architecture tests.</li>
 * </ul>
 */
package app.giaky.lineage.application;
