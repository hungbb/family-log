package app.giaky.common.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PartialDate} — the value object every date in a <i>gia phả</i> goes through (B4.1).
 *
 * <p>The comparison tests are the ones that matter. I-P2 refuses a birth after a death, and it does
 * so by asking this class; a comparison that claimed an order the record does not support would
 * reject people whose real dates are simply imprecise.
 */
class PartialDateTest {

    @Nested
    @DisplayName("shape")
    class Shape {

        @Test
        @DisplayName("a year-only date is the ordinary case and needs nothing else")
        void yearOnly() {
            PartialDate date = PartialDate.ofYear(1901);

            assertThat(date.isKnown()).isTrue();
            assertThat(date.precision()).isEqualTo(DatePrecision.YEAR);
            assertThat(date.month()).isNull();
            assertThat(date.toString()).isEqualTo("1901");
        }

        @Test
        @DisplayName("an unknown date carries no parts at all")
        void unknownCarriesNothing() {
            assertThat(PartialDate.unknown().isKnown()).isFalse();
            assertThatThrownBy(
                            () ->
                                    new PartialDate(
                                            1901, null, null, DatePrecision.UNKNOWN, false, null,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UNKNOWN date carries no year");
        }

        @Test
        @DisplayName("a day without a month is refused")
        void dayNeedsMonth() {
            assertThatThrownBy(
                            () ->
                                    new PartialDate(
                                            1901, null, 3, DatePrecision.EXACT, false, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a range, and only a range, has an end year")
        void rangeShape() {
            assertThat(PartialDate.between(1930, 1945).latestOrdinal())
                    .isGreaterThan(PartialDate.between(1930, 1945).earliestOrdinal());

            assertThatThrownBy(
                            () ->
                                    new PartialDate(
                                            1930, null, null, DatePrecision.YEAR, false, 1945, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only a range");
            assertThatThrownBy(() -> PartialDate.between(1945, 1930))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot end before it starts");
        }

        @Test
        @DisplayName("the lunar reading is kept verbatim and trimmed")
        void lunarText() {
            PartialDate date = PartialDate.ofMonth(1943, 7).withLunarText("  tháng 6 năm Quý Mùi  ");

            assertThat(date.lunarText()).isEqualTo("tháng 6 năm Quý Mùi");
        }

        @Test
        @DisplayName("a lunar reading with no year is recorded, though it is not known")
        void lunarOnlyIsRecorded() {
            PartialDate anniversary = PartialDate.unknown().withLunarText("giỗ mồng 3 tháng Chạp");

            assertThat(anniversary.isKnown())
                    .as("nothing a timeline could use, so comparison must keep ignoring it")
                    .isFalse();
            assertThat(anniversary.isRecorded())
                    .as("but the family wrote it down, and a reader must not drop it")
                    .isTrue();
            assertThat(anniversary.compareWith(PartialDate.ofYear(1901)))
                    .isEqualTo(DateComparison.INDETERMINATE);
        }

        @Test
        @DisplayName("an empty date is neither, and a dated one is both")
        void recordedFollowsContent() {
            assertThat(PartialDate.unknown().isRecorded()).isFalse();
            assertThat(PartialDate.unknown().withLunarText("   ").isRecorded())
                    .as("blank text is trimmed to nothing")
                    .isFalse();
            assertThat(PartialDate.ofYear(1901).isRecorded()).isTrue();
        }

        @Test
        @DisplayName("an impossible calendar day is stored rather than corrected")
        void thirtyFirstOfFebruary() {
            // A book that says 31 February is a book that says 31 February. Refusing it would mean
            // refusing to transcribe the record; the comparison only needs an order, so it copes.
            PartialDate date = PartialDate.exact(1901, 2, 31);

            assertThat(date.compareWith(PartialDate.ofYear(1950))).isEqualTo(DateComparison.BEFORE);
        }
    }

    @Nested
    @DisplayName("three-valued comparison (doc 02 §3)")
    class Comparison {

        @Test
        @DisplayName("two exact dates order the obvious way")
        void exactDates() {
            assertThat(PartialDate.exact(1901, 3, 4).compareWith(PartialDate.exact(1968, 3, 4)))
                    .isEqualTo(DateComparison.BEFORE);
            assertThat(PartialDate.exact(1968, 3, 4).compareWith(PartialDate.exact(1901, 3, 4)))
                    .isEqualTo(DateComparison.AFTER);
        }

        @Test
        @DisplayName("the same year is indeterminate, not equal")
        void sameYearIsIndeterminate() {
            // "Born 1901, died 1901" happens, and it does not say which came first. Answering
            // "equal" would make an invariant built on this reject nothing and explain nothing.
            assertThat(PartialDate.ofYear(1901).compareWith(PartialDate.ofYear(1901)))
                    .isEqualTo(DateComparison.INDETERMINATE);
        }

        @Test
        @DisplayName("a year contains every date inside it")
        void yearContainsMonths() {
            assertThat(PartialDate.ofYear(1901).compareWith(PartialDate.ofMonth(1901, 6)))
                    .isEqualTo(DateComparison.INDETERMINATE);
            assertThat(PartialDate.ofMonth(1901, 1).compareWith(PartialDate.ofYear(1902)))
                    .isEqualTo(DateComparison.BEFORE);
        }

        @Test
        @DisplayName("an unknown date is never comparable")
        void unknownIsAlwaysIndeterminate() {
            assertThat(PartialDate.unknown().compareWith(PartialDate.ofYear(1901)))
                    .isEqualTo(DateComparison.INDETERMINATE);
            assertThat(PartialDate.ofYear(1901).compareWith(PartialDate.unknown()))
                    .isEqualTo(DateComparison.INDETERMINATE);
            assertThat(PartialDate.ofYear(1901).compareWith(null))
                    .isEqualTo(DateComparison.INDETERMINATE);
        }

        @Test
        @DisplayName("circa widens the year on both sides")
        void circaWidens() {
            PartialDate about1900 = PartialDate.circaYear(1900);

            assertThat(about1900.compareWith(PartialDate.ofYear(1903)))
                    .as("inside the tolerance, so the record does not settle the order")
                    .isEqualTo(DateComparison.INDETERMINATE);
            assertThat(about1900.compareWith(PartialDate.ofYear(1930)))
                    .as("well outside it")
                    .isEqualTo(DateComparison.BEFORE);
            assertThat(about1900.compareWith(PartialDate.ofYear(1850)))
                    .isEqualTo(DateComparison.AFTER);
        }

        @Test
        @DisplayName("ranges overlap into indeterminacy and separate into an order")
        void ranges() {
            assertThat(PartialDate.between(1930, 1945).compareWith(PartialDate.between(1940, 1950)))
                    .isEqualTo(DateComparison.INDETERMINATE);
            assertThat(PartialDate.between(1930, 1939).compareWith(PartialDate.between(1940, 1950)))
                    .isEqualTo(DateComparison.BEFORE);
        }

        @Test
        @DisplayName("isCertainlyAfter is the shape I-P2 refuses, and only that shape")
        void certainlyAfter() {
            assertThat(PartialDate.ofYear(1968).isCertainlyAfter(PartialDate.ofYear(1901))).isTrue();
            assertThat(PartialDate.ofYear(1901).isCertainlyAfter(PartialDate.ofYear(1901)))
                    .as("same year — the record does not say, so there is nothing to refuse")
                    .isFalse();
            assertThat(PartialDate.ofYear(1901).isCertainlyAfter(PartialDate.unknown())).isFalse();
        }
    }
}
