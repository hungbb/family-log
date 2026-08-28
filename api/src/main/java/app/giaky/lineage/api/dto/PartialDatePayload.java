package app.giaky.lineage.api.dto;

import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.DatePrecision;
import app.giaky.common.types.PartialDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A {@link PartialDate} on the wire (doc 05 §3.1).
 *
 * <p>One record for both directions. The alternative — a request DTO and a response DTO with the
 * same seven fields — is two places for a field to be added to and one place for it to be forgotten,
 * for a shape that is symmetric by nature.
 *
 * <p>It exists at all because {@link PartialDate}'s constructor refuses an inconsistent date by
 * throwing, and an {@code IllegalArgumentException} escaping Jackson's deserializer is a 500 with a
 * stack trace where the client deserved a 422 naming the field. {@link #toDomain} makes that
 * translation once.
 *
 * @param precision {@code EXACT | MONTH | YEAR | RANGE | UNKNOWN}; null is read as {@code UNKNOWN}
 * @param rangeEndYear the far end of a {@code RANGE}, inclusive
 * @param lunarText the âm lịch reading exactly as the book writes it, stored and shown verbatim
 */
@Schema(description = "A date at whatever precision the family record actually has.")
public record PartialDatePayload(
        @Min(1) @Max(2999) Integer year,
        @Min(1) @Max(12) Integer month,
        @Min(1) @Max(31) Integer day,
        @Schema(example = "YEAR") String precision,
        boolean circa,
        @Min(1) @Max(2999) Integer rangeEndYear,
        @Size(max = 100) @Schema(example = "tháng 6 năm Quý Mùi") String lunarText) {

    /** Null in, null out: an absent date and an unknown one are the same fact. */
    public static PartialDatePayload of(PartialDate date) {
        if (date == null || !date.isKnown()) {
            return null;
        }
        return new PartialDatePayload(
                date.year(),
                date.month(),
                date.day(),
                date.precision().name(),
                date.circa(),
                date.rangeEndYear(),
                date.lunarText());
    }

    /** The domain value, or {@link PartialDate#unknown()} when the field was omitted. */
    public static PartialDate toDomain(PartialDatePayload payload, String field) {
        if (payload == null) {
            return PartialDate.unknown();
        }
        DatePrecision precision;
        try {
            precision =
                    payload.precision() == null || payload.precision().isBlank()
                            ? DatePrecision.UNKNOWN
                            : DatePrecision.valueOf(payload.precision().trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    field + ": no such precision — " + payload.precision());
        }
        try {
            return new PartialDate(
                    payload.year(),
                    payload.month(),
                    payload.day(),
                    precision,
                    payload.circa(),
                    payload.rangeEndYear(),
                    payload.lunarText());
        } catch (IllegalArgumentException invalid) {
            // The value object says what is wrong in one sentence; the only thing added here is
            // which of the two dates on the form it was.
            throw new DomainRuleViolation(
                    ErrorCode.DATE_RANGE_INVALID, field + ": " + invalid.getMessage());
        }
    }
}
