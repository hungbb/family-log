package app.giaky.common.types;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A date a family book actually contains (doc 02 §3, B4.1).
 *
 * <p>{@code LocalDate} models a date somebody looked up in a calendar. A <i>gia phả</i> holds
 * something else: a year and nothing more for most ancestors, "khoảng 1890" for the ones remembered
 * rather than recorded, a lunar date for a giỗ that has been kept for two centuries, and sometimes
 * only a span — "between the two wars". Forcing any of those into a {@code LocalDate} means asking
 * whoever types it in to invent the missing parts, and invented data is indistinguishable from
 * recorded data the moment it is stored.
 *
 * <p>So this carries what is known and says so. Immutable, comparable three-valued (doc 02 §3), and
 * flattened onto plain columns by the mapping layer (doc 03 §3.7) so every field stays queryable.
 *
 * <p><b>The lunar text is deliberately free text.</b> Converting âm lịch to Gregorian needs a table
 * of lunar months that differs between the Vietnamese and Chinese calendars and has exceptions
 * around 1968; getting it wrong silently moves a family's anniversary. The book's own words are
 * recorded verbatim and rendered verbatim, and the conversion is a feature nobody has asked for.
 *
 * @param year null only when nothing at all is known
 * @param month null unless the precision is {@link DatePrecision#MONTH} or {@link
 *     DatePrecision#EXACT}
 * @param day null unless the precision is {@link DatePrecision#EXACT}
 * @param circa "khoảng" — the year is approximate, widened by {@link #CIRCA_TOLERANCE_YEARS} on
 *     both sides when comparing
 * @param rangeEndYear the inclusive far end of a {@link DatePrecision#RANGE}; null otherwise
 * @param lunarText the âm lịch record as the book writes it, e.g. {@code "tháng 6 năm Quý Mùi"}
 */
public record PartialDate(
        Integer year,
        Integer month,
        Integer day,
        DatePrecision precision,
        boolean circa,
        Integer rangeEndYear,
        String lunarText) {

    /**
     * How far either side of the stated year a {@code circa} date is allowed to fall.
     *
     * <p>Five, because that is roughly the error in "khoảng 1890" as families use it — a recollection
     * of a decade, not of a year. The number matters because it decides which records I-P2 refuses:
     * too small and a genuine "circa 1900 – died 1898" is rejected, too large and an obvious
     * transposition slips through. It is named rather than inlined so a future argument about it is
     * an argument about one constant.
     */
    public static final int CIRCA_TOLERANCE_YEARS = 5;

    /** {@code birth_lunar_text} and friends are {@code VARCHAR(100)}. */
    public static final int LUNAR_TEXT_MAX_LENGTH = 100;

    /** Nothing before this is a date somebody could have recorded about a person. */
    public static final int MIN_YEAR = 1;

    /** Guards a typo'd year from becoming a date nobody can render. Extended by a future migration. */
    public static final int MAX_YEAR = 2999;

    public PartialDate {
        precision = precision == null ? DatePrecision.UNKNOWN : precision;
        lunarText = trimToNull(lunarText);

        if (precision.requiresYear() && year == null) {
            throw new IllegalArgumentException(precision + " needs a year");
        }
        if (precision == DatePrecision.UNKNOWN && (year != null || month != null || day != null)) {
            throw new IllegalArgumentException(
                    "an UNKNOWN date carries no year, month or day — use YEAR precision instead");
        }
        if (year != null && (year < MIN_YEAR || year > MAX_YEAR)) {
            throw new IllegalArgumentException("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (day != null && (day < 1 || day > 31)) {
            throw new IllegalArgumentException("day must be between 1 and 31");
        }
        // The shape rules the CHECK constraints in V7 also carry. A day without a month is not a
        // date anybody can render, and a month at YEAR precision is a field the UI will not show
        // and the comparison will not read — which makes it data that silently disappears.
        if (month == null && day != null) {
            throw new IllegalArgumentException("a day needs a month");
        }
        if (precision == DatePrecision.EXACT && (month == null || day == null)) {
            throw new IllegalArgumentException("an EXACT date needs a month and a day");
        }
        if (precision == DatePrecision.MONTH && (month == null || day != null)) {
            throw new IllegalArgumentException("a MONTH date needs a month and no day");
        }
        if ((precision == DatePrecision.YEAR || precision == DatePrecision.RANGE)
                && (month != null || day != null)) {
            throw new IllegalArgumentException(precision + " carries a year only");
        }
        if ((precision == DatePrecision.RANGE) != (rangeEndYear != null)) {
            throw new IllegalArgumentException("a range, and only a range, has an end year");
        }
        if (rangeEndYear != null && rangeEndYear < year) {
            throw new IllegalArgumentException("a range cannot end before it starts");
        }
        if (lunarText != null && lunarText.length() > LUNAR_TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "lunar text must be at most " + LUNAR_TEXT_MAX_LENGTH + " characters");
        }
    }

    /** Nothing is known — the default for every date field on a new person. */
    public static PartialDate unknown() {
        return new PartialDate(null, null, null, DatePrecision.UNKNOWN, false, null, null);
    }

    /** The ordinary case: a year, and nothing finer. */
    public static PartialDate ofYear(int year) {
        return new PartialDate(year, null, null, DatePrecision.YEAR, false, null, null);
    }

    /** A year the family remembers approximately — "khoảng 1890". */
    public static PartialDate circaYear(int year) {
        return new PartialDate(year, null, null, DatePrecision.YEAR, true, null, null);
    }

    public static PartialDate ofMonth(int year, int month) {
        return new PartialDate(year, month, null, DatePrecision.MONTH, false, null, null);
    }

    public static PartialDate exact(int year, int month, int day) {
        return new PartialDate(year, month, day, DatePrecision.EXACT, false, null, null);
    }

    public static PartialDate exact(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return exact(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /** Somewhere in {@code [from, to]}, inclusive. */
    public static PartialDate between(int from, int to) {
        return new PartialDate(from, null, null, DatePrecision.RANGE, false, to, null);
    }

    /** Whether this date says anything at all about when something happened. */
    public boolean isKnown() {
        return precision != DatePrecision.UNKNOWN;
    }

    /** A copy carrying the book's own lunar wording alongside the Gregorian reading. */
    public PartialDate withLunarText(String value) {
        return new PartialDate(year, month, day, precision, circa, rangeEndYear, value);
    }

    /**
     * Where this date sits relative to {@code other} (doc 02 §3).
     *
     * <p>Each date is widened into the interval it could actually denote — a year becomes the whole
     * year, a {@code circa} year becomes {@link #CIRCA_TOLERANCE_YEARS} either side, a range becomes
     * itself — and the answer is {@link DateComparison#BEFORE} or {@link DateComparison#AFTER} only
     * when the two intervals do not touch. Overlap is {@link DateComparison#INDETERMINATE}, and so is
     * any comparison involving an unknown date.
     *
     * <p>That is stricter than it looks, and deliberately so: it never claims an order the record
     * does not support, which is exactly what an invariant built on it needs.
     */
    public DateComparison compareWith(PartialDate other) {
        if (other == null || !isKnown() || !other.isKnown()) {
            return DateComparison.INDETERMINATE;
        }
        if (latestOrdinal() < other.earliestOrdinal()) {
            return DateComparison.BEFORE;
        }
        if (earliestOrdinal() > other.latestOrdinal()) {
            return DateComparison.AFTER;
        }
        return DateComparison.INDETERMINATE;
    }

    /** Whether this date is certainly after {@code other} — the shape I-P2 refuses. */
    public boolean isCertainlyAfter(PartialDate other) {
        return compareWith(other) == DateComparison.AFTER;
    }

    /**
     * The earliest instant this date could denote, as {@code yyyymmdd}.
     *
     * <p>An ordinal rather than a {@code LocalDate} because 31 February is a legal thing for a book
     * to contain and this comparison must not throw on it. Ordering is all that is asked of it.
     */
    public int earliestOrdinal() {
        int startYear = circa ? Math.max(MIN_YEAR, year - CIRCA_TOLERANCE_YEARS) : year;
        return ordinal(startYear, month == null ? 1 : month, day == null ? 1 : day);
    }

    /** The latest instant this date could denote, as {@code yyyymmdd}. */
    public int latestOrdinal() {
        int endYear = rangeEndYear == null ? year : rangeEndYear;
        if (circa) {
            endYear = Math.min(MAX_YEAR, endYear + CIRCA_TOLERANCE_YEARS);
        }
        // Widening an unknown month to December and an unknown day to the 31st is what makes "1901"
        // mean the whole of 1901 rather than its first instant.
        return ordinal(endYear, month == null ? 12 : month, day == null ? 31 : day);
    }

    private static int ordinal(int year, int month, int day) {
        return year * 10_000 + month * 100 + day;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * A short, log-safe rendering. Not the display format — that is the client's job and it is
     * localized (F4.1) — but enough to make a failing assertion readable.
     */
    @Override
    public String toString() {
        if (!isKnown()) {
            return "unknown";
        }
        String base =
                switch (precision) {
                    case EXACT -> String.format("%04d-%02d-%02d", year, month, day);
                    case MONTH -> String.format("%04d-%02d", year, month);
                    case RANGE -> year + ".." + rangeEndYear;
                    case YEAR, UNKNOWN -> String.valueOf(year);
                };
        return circa ? "c. " + base : base;
    }
}
