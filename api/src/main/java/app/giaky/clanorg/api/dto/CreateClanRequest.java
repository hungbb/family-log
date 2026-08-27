package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.Clan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /clans} (FR-C1).
 *
 * <p>The lengths mirror the columns, so an over-long field is a 422 with a field name rather than a
 * database error the caller cannot act on.
 */
public record CreateClanRequest(
        @Schema(description = "The clan as it is displayed", example = "Nguyễn Văn")
                @NotBlank
                @Size(max = Clan.NAME_MAX_LENGTH)
                String name,
        @Schema(
                        description =
                                "The family name the join code is built from. Diacritics are"
                                    + " stripped: Nguyễn becomes the NGUYEN in NGUYEN-7F3K."
                                    + " Defaults to the clan name.",
                        example = "Nguyễn")
                @Size(max = 100)
                String surname,
        @Schema(description = "Quê quán — where the family comes from", example = "Nam Định")
                @Size(max = Clan.ORIGIN_PLACE_MAX_LENGTH)
                String originPlace,
        @Size(max = Clan.DESCRIPTION_MAX_LENGTH) String description) {}
