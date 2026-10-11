package com.srinidhi.kharch.core.model

import java.time.LocalDate

/**
 * A calendar month, with the arithmetic the dashboard needs and the off-by-one bugs it avoids.
 *
 * Everything here works in `Long` epoch days so a month maps directly onto the `dateEpochDay`
 * index that [Expense] and the `expenses` table store. Ranges are **inclusive of the first day
 * and inclusive of the last day** via [epochDayRange]; the DAO queries take an *exclusive* end
 * so they can be chained without an off-by-one (see `ExpenseDao.observeLiveBetween`).
 *
 * Leap years are handled explicitly rather than delegated, so the rule is testable in isolation:
 * a year is a leap year when it is divisible by 4, except centuries, which must be divisible
 * by 400. 2028 and 2000 are leap years; 2027 and 1900 are not.
 *
 * @property year proleptic Gregorian year.
 * @property month `1..12`.
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
    }

    /** Monotonic index used for ordering and for month arithmetic that crosses year boundaries. */
    val monthIndex: Int get() = year * MONTHS_PER_YEAR + (month - 1)

    /** `true` when this month is February in a leap year. */
    val isLeapYear: Boolean get() = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** Number of days in this month: 28 or 29 for February, 30 or 31 elsewhere. */
    val daysInMonth: Int
        get() = when {
            month != FEBRUARY -> DAYS_IN_MONTH[month - 1]
            isLeapYear -> 29
            else -> 28
        }

    /** Epoch day of the 1st of this month — the inclusive start of the month. */
    val firstEpochDay: Long get() = LocalDate.of(year, month, 1).toEpochDay()

    /** Epoch day of the last day of this month — the inclusive end of the month. */
    val lastEpochDay: Long get() = firstEpochDay + daysInMonth - 1L

    /** Epoch day of the day after this month — the **exclusive** end used by DAO queries. */
    val endEpochDayExclusive: Long get() = firstEpochDay + daysInMonth

    /** Every epoch day in this month, inclusive at both ends. */
    val epochDayRange: LongRange get() = firstEpochDay..lastEpochDay

    /** `true` when [epochDay] falls inside this month. */
    fun contains(epochDay: Long): Boolean = epochDay in epochDayRange

    /** This month shifted by [months], rolling across year boundaries in either direction. */
    fun plusMonths(months: Int): YearMonth = ofMonthIndex(monthIndex + months)

    /** This month shifted back by [months]. */
    fun minusMonths(months: Int): YearMonth = plusMonths(-months)

    /** The [day]-th day of this month as a [LocalDate]. */
    fun atDay(day: Int): LocalDate {
        require(day in 1..daysInMonth) { "day must be in 1..$daysInMonth for $year-$month, was $day" }
        return LocalDate.of(year, month, day)
    }

    /** The first day of this month as a [LocalDate]. */
    fun atStartOfMonth(): LocalDate = LocalDate.of(year, month, 1)

    override fun compareTo(other: YearMonth): Int = monthIndex.compareTo(other.monthIndex)

    /** ISO-ish `2026-08`, so debug output and logs stay readable. */
    override fun toString(): String = buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(month.toString().padStart(2, '0'))
    }

    companion object {
        const val MONTHS_PER_YEAR: Int = 12

        private const val FEBRUARY: Int = 2
        private val DAYS_IN_MONTH: IntArray = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        fun of(year: Int, month: Int): YearMonth = YearMonth(year, month)

        /** The month containing [date]. */
        fun fromDate(date: LocalDate): YearMonth = YearMonth(date.year, date.monthValue)

        /** The month containing [epochDay]. */
        fun fromEpochDay(epochDay: Long): YearMonth = fromDate(LocalDate.ofEpochDay(epochDay))

        /** Inverse of [monthIndex]; accepts indexes outside the representable year range. */
        fun ofMonthIndex(monthIndex: Int): YearMonth {
            val yearOf = Math.floorDiv(monthIndex, MONTHS_PER_YEAR)
            val monthOffset = Math.floorMod(monthIndex, MONTHS_PER_YEAR)
            return YearMonth(year = yearOf, month = monthOffset + 1)
        }

        /** Every month of [year], January first. Exactly 12 elements, even for an empty year. */
        fun ofYear(year: Int): List<YearMonth> = (1..MONTHS_PER_YEAR).map { month -> YearMonth(year, month) }
    }
}
