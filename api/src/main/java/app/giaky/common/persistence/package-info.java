/**
 * The bridge between the typed ids of {@link app.giaky.common.types} and the {@code UUID} columns
 * they are stored in.
 *
 * <p>Without these converters a JPA attribute would have to be a raw {@code UUID}, and so would
 * every repository finder's parameter — which is precisely the shape rule 6 of {@code
 * architecture.LayeringTest} exists to forbid. Typing the column lets {@code
 * findByClanIdAndId(ClanId, UUID)} be both a derived query Spring Data can build and a signature the
 * tenancy rule accepts, instead of the two being in tension.
 *
 * <p>They sit in {@code common} rather than in a context's {@code infrastructure} because every
 * context's tables carry the same ids, and a converter duplicated five times is a converter that
 * will one day disagree with itself.
 */
package app.giaky.common.persistence;
