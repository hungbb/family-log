/**
 * The domain events Identity publishes.
 *
 * <p>In {@code application} rather than {@code domain} for the reason ClanOrg's are: other bounded
 * contexts subscribe, and {@code architecture.LayeringTest} rule 4 forbids anything outside a
 * context from importing its {@code domain}.
 */
package app.giaky.identity.application.event;
