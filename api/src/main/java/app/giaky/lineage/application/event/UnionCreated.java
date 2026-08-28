package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/** A marriage was recorded. */
public record UnionCreated(
        ClanId clanId,
        UUID unionId,
        PersonId partnerAId,
        PersonId partnerBId,
        String unionType,
        UserId createdBy,
        Instant occurredAt) {}
