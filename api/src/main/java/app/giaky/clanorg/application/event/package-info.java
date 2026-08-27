/**
 * The domain events ClanOrg publishes (doc 02 §4).
 *
 * <p>They live in {@code application} rather than in {@code domain} for one concrete reason: other
 * bounded contexts consume them, and {@code architecture.LayeringTest} rule 4 forbids anything
 * outside a context from importing its {@code domain}. An event in {@code domain} would therefore be
 * an event nobody may subscribe to — which is not an event.
 *
 * <p>Consequently these records are built from {@code common.types} ids and their own nested enums
 * only. Nothing here may expose a ClanOrg aggregate, a value object, or a status enum; a subscriber
 * that needed one would need the package it lives in.
 *
 * <p>Published with {@code ApplicationEventPublisher} and consumed after commit, so a listener never
 * acts on a transaction that then rolls back.
 */
package app.giaky.clanorg.application.event;
