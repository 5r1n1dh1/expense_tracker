package com.srinidhi.kharch.ui.dashboard

/** Full month names, index 0 = January. */
private val MONTH_LABELS: List<String> = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

/** Three-letter month names, index 0 = January, for the twelve bar labels. */
private val MONTH_SHORT_LABELS: List<String> = MONTH_LABELS.map { it.take(3) }

/**
 * The three-letter label under a bar.
 *
 * English month names are bundled rather than read from the platform so the chart looks the
 * same on every device; a localisation pass replaces this one lookup.
 */
internal fun monthShortLabel(month: Int): String =
    MONTH_SHORT_LABELS.getOrNull(month - 1).orEmpty()

/** The full month name of a 1-based [month], e.g. `"August"`. */
internal fun monthLabel(month: Int): String = MONTH_LABELS.getOrNull(month - 1).orEmpty()

/** `"August 2026"` — the month sheet's title and the year sheet's sub-line. */
internal fun yearMonthLabel(year: Int, month: Int): String = "${monthLabel(month)} $year"
