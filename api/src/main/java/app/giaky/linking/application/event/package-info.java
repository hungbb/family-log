/**
 * The domain events Linking publishes.
 *
 * <p>They live in {@code application} rather than in {@code domain} for the same concrete reason
 * ClanOrg's do: other bounded contexts consume them, and {@code architecture.LayeringTest} rule 4
 * forbids anything outside a context from importing its {@code domain}. An event in {@code domain}
 * would be an event nobody may subscribe to — which is not an event.
 *
 * <p>Notification is the subscriber that exists today (B6.9). Everything here is built from
 * {@code common.types} ids, plain strings and its own nested enums, so a subscriber never needs a
 * Linking class to read one.
 */
package app.giaky.linking.application.event;
