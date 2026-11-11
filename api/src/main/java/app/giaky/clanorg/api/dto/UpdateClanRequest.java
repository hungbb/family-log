package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.Clan;
import app.giaky.common.web.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code PATCH /clans/{clanId}} (FR-C3).
 *
 * <p>An absent field is left unchanged. There is no {@code clanCode} field and there will not be
 * one: the code is immutable (I-C1), and the surest way to keep it so is for the request not to be
 * able to express a change.
 */
public record UpdateClanRequest(
        @NullOrNotBlank @Size(max = Clan.NAME_MAX_LENGTH) String name,
        @Size(max = Clan.DESCRIPTION_MAX_LENGTH) String description,
        @Size(max = Clan.ORIGIN_PLACE_MAX_LENGTH) String originPlace,
        UUID coverMediaId,
        @Schema(
                        description =
                                "The version last read. A mismatch means somebody else edited the"
                                    + " clan in between, and the request is refused with 409"
                                    + " VERSION_CONFLICT rather than overwriting their change.")
                @PositiveOrZero
                long version) {}
