package app.giaky.lineage.infrastructure;

import app.giaky.common.types.DatePrecision;
import app.giaky.common.types.PartialDate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The seven columns a {@link PartialDate} flattens onto (doc 03 §3.7).
 *
 * <p>An {@code @Embeddable} rather than seven fields repeated per date, because {@code persons}
 * carries two of these and {@code unions} another two — twenty-eight columns that would otherwise be
 * twenty-eight fields and fifty-six accessors, all of them the same. Column names come from
 * {@code @AttributeOverride} at each use site, which is where the {@code birth_}/{@code death_}
 * prefix actually belongs.
 *
 * <p>{@code precision} is stored as a string, matching the {@code CHECK} constraint rather than a
 * native enum (doc 03 §1) — a new precision is then a Flyway migration instead of a type change.
 *
 * <p>Never null as a whole: {@code precision} is {@code NOT NULL}, so an unknown date is the string
 * {@code UNKNOWN} with every other column null. That is what keeps Hibernate from reading an
 * all-null embeddable back as a null reference and handing the domain a {@code NullPointerException}
 * where it expected {@link PartialDate#unknown()}.
 */
@Embeddable
public class PartialDateEmbeddable {

    @Column(name = "year")
    private Short year;

    @Column(name = "month")
    private Short month;

    @Column(name = "day")
    private Short day;

    @Column(name = "precision", nullable = false, length = 10)
    private String precision = DatePrecision.UNKNOWN.name();

    @Column(name = "circa", nullable = false)
    private boolean circa;

    @Column(name = "range_end_year")
    private Short rangeEndYear;

    @Column(name = "lunar_text", length = 100)
    private String lunarText;

    protected PartialDateEmbeddable() {}

    /** Flattens a domain date onto the columns. Null becomes {@link PartialDate#unknown()}. */
    static PartialDateEmbeddable of(PartialDate value) {
        PartialDate date = value == null ? PartialDate.unknown() : value;
        PartialDateEmbeddable row = new PartialDateEmbeddable();
        row.year = shortOf(date.year());
        row.month = shortOf(date.month());
        row.day = shortOf(date.day());
        row.precision = date.precision().name();
        row.circa = date.circa();
        row.rangeEndYear = shortOf(date.rangeEndYear());
        row.lunarText = date.lunarText();
        return row;
    }

    /**
     * Reassembles the domain value.
     *
     * <p>Goes through {@link PartialDate}'s constructor, which re-validates the shape — so a row
     * hand-edited into a day with no month fails on read rather than propagating into a comparison
     * that quietly returns the wrong answer. That is the whole reason the value object exists.
     */
    PartialDate toDomain() {
        return new PartialDate(
                intOf(year),
                intOf(month),
                intOf(day),
                precision == null ? DatePrecision.UNKNOWN : DatePrecision.valueOf(precision),
                circa,
                intOf(rangeEndYear),
                lunarText);
    }

    private static Short shortOf(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private static Integer intOf(Short value) {
        return value == null ? null : value.intValue();
    }
}
