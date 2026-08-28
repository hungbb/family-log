package app.giaky.common.types;

/**
 * How much of a {@link PartialDate} is actually known (doc 02 §3).
 *
 * <p>Not a formatting hint. The precision is the difference between "born in 1901" and "born on the
 * third of March 1901", and a model that stored only the second would force whoever is entering a
 * hundred-year-old record to invent a day. Most of the dates this product will ever hold are
 * {@link #YEAR}.
 */
public enum DatePrecision {

    /** Year, month and day are all recorded. */
    EXACT,

    /** Year and month. Common for deaths, which families remember by the month of the giỗ. */
    MONTH,

    /** The year alone — the ordinary case for anybody born before living memory. */
    YEAR,

    /** Somewhere between {@code year} and {@code rangeEndYear} inclusive. */
    RANGE,

    /** Nothing is known. The only precision a date with no year may carry. */
    UNKNOWN;

    /** Whether a date at this precision must carry a year. */
    public boolean requiresYear() {
        return this != UNKNOWN;
    }
}
