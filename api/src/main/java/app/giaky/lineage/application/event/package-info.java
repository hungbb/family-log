/**
 * The domain events Lineage publishes (doc 02 §4).
 *
 * <p>In {@code application} rather than {@code domain} for the same reason ClanOrg's are: other
 * contexts consume them, and {@code architecture.LayeringTest} rule 4 forbids anything outside a
 * context from importing its {@code domain}. An event nobody may subscribe to is not an event.
 *
 * <p>So these records are built from {@code common.types} ids and primitives only. Nothing here
 * exposes a {@code Person}, a {@code Union} or a lineage enum — a subscriber that needed one would
 * need the package it lives in.
 *
 * <p><b>They are notifications, not the mechanism.</b> The ancestry closure is <i>not</i> maintained
 * by a listener on {@code ParentageAdded}: doc 02 §6 puts that inside the mutating transaction, and
 * these are dispatched after commit. What they are for is audit, notification and — from Phase 5 —
 * cache invalidation, none of which may decide anything about access.
 */
package app.giaky.lineage.application.event;
