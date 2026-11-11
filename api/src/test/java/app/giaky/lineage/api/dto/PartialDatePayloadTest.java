package app.giaky.lineage.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.PartialDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PartialDatePayload}: what goes out is what came in (doc 05 §3.1).
 *
 * <p>The record is one shape for both directions, so the property worth pinning is the round trip —
 * {@code toDomain(of(date))} is {@code date} for every date the domain accepts. It did not hold for
 * the commonest date in a family book: a lunar reading with no Gregorian year went in, was stored,
 * and came back as nothing.
 */
class PartialDatePayloadTest {

    @Test
    @DisplayName("a lunar-only date is returned, at UNKNOWN precision with its text")
    void lunarOnlyIsReturned() {
        PartialDate anniversary = PartialDate.unknown().withLunarText("giỗ mồng 3 tháng Chạp");

        PartialDatePayload payload = PartialDatePayload.of(anniversary);

        assertThat(payload).isNotNull();
        assertThat(payload.precision()).isEqualTo("UNKNOWN");
        assertThat(payload.lunarText()).isEqualTo("giỗ mồng 3 tháng Chạp");
        assertThat(payload.year()).isNull();
    }

    @Test
    @DisplayName("every shape the domain accepts survives the round trip")
    void roundTrip() {
        PartialDate[] dates = {
            PartialDate.ofYear(1901),
            PartialDate.circaYear(1890),
            PartialDate.ofMonth(1943, 6).withLunarText("tháng 6 năm Quý Mùi"),
            PartialDate.exact(1968, 2, 1),
            PartialDate.between(1930, 1945),
            PartialDate.unknown().withLunarText("năm Ất Dậu"),
        };

        for (PartialDate date : dates) {
            assertThat(PartialDatePayload.toDomain(PartialDatePayload.of(date), "birth"))
                    .as("%s", date)
                    .isEqualTo(date);
        }
    }

    @Test
    @DisplayName("a date with nothing in it is still absent, not an empty object")
    void emptyIsAbsent() {
        assertThat(PartialDatePayload.of(null)).isNull();
        assertThat(PartialDatePayload.of(PartialDate.unknown())).isNull();
        assertThat(PartialDatePayload.toDomain(null, "birth")).isEqualTo(PartialDate.unknown());
    }

    @Test
    @DisplayName("an inconsistent date is a 422 that names the field")
    void inconsistentDateIsRefused() {
        PartialDatePayload yearAtUnknown =
                new PartialDatePayload(1901, null, null, "UNKNOWN", false, null, null);

        assertThatThrownBy(() -> PartialDatePayload.toDomain(yearAtUnknown, "death"))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageStartingWith("death:")
                .extracting(failure -> ((DomainRuleViolation) failure).code())
                .isEqualTo(ErrorCode.DATE_RANGE_INVALID);
    }
}
