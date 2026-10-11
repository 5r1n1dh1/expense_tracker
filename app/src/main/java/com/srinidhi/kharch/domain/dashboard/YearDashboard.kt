package com.srinidhi.kharch.domain.dashboard

import com.srinidhi.kharch.core.model.Money

/** Bars are always drawn for twelve months, even when nothing was tracked (D9). */
const val MONTHS_IN_YEAR: Int = 12

/** The longest a calendar year can be; February of a leap year (e.g. 2028) has 29 days. */
const val DAYS_IN_MAX_YEAR: Int = 366

/**
 * The year-over-year comparison of §9, pinned to one period.
 *
 * The three figures travel together on purpose. A dashboard that showed a part-year next to
 * a whole year — or a delta computed from two different windows — is how an expense app ends
 * up claiming spending tripled when nothing happened.
 *
 * @property currentPeriod this year, up to today when the year is still running.
 * @property previousPeriod the same span one year earlier, 29 February clamped to the 28th.
 * @property delta [currentPeriod] − [previousPeriod].
 */
data class YearOverYear(
    val currentPeriod: Money,
    val previousPeriod: Money,
    val delta: Money,
)

/**
 * Counting figures of a [YearDashboard].
 *
 * @property entryCount tracked rows inside the year, tombstones excluded.
 * @property dayCount distinct calendar days carrying at least one entry. A day that fully
 *   refunds itself still counts: the user tracked something.
 * @property maxDay heaviest single-day net in the year; ₹0 when there is no activity.
 * @property avgPerActiveDay [netTotal][YearDashboard.netTotal] ÷ [dayCount], rounded to the
 *   nearest paise.
 */
data class YearStats(
    val entryCount: Int,
    val dayCount: Int,
    val maxDay: Money,
    val avgPerActiveDay: Money,
)

/**
 * An immutable snapshot of one calendar year of the shared ledger (§9).
 *
 * Everything is derived in a single pass over the year's expenses using `Long` paise
 * arithmetic, so two devices holding the same rows (D1) produce an equal value and neither
 * can drift from the other.
 *
 * Sign convention (D5): an expense contributes `+amount`, a refund contributes `−amount`.
 * A [monthlyNet] entry, a [categoryNet] entry and [netTotal] may therefore all be negative,
 * and the UI draws those bars downwards against a zero baseline instead of clamping them.
 *
 * @property monthlyNet exactly [MONTHS_IN_YEAR] signed totals, index 0 = January.
 * @property categoryNet signed net per category id, ordered by first appearance.
 * @property grossSpend sum of expense magnitudes — never negative.
 * @property totalRefunds sum of refund magnitudes — a negative number (D5).
 * @property netTotal [grossSpend] + [totalRefunds].
 * @property chartScalePaise one shared scale for all twelve bars, never a per-month one.
 */
data class YearDashboard(
    val year: Int,
    val monthlyNet: List<Money>,
    val categoryNet: Map<String, Money>,
    val grossSpend: Money,
    val totalRefunds: Money,
    val netTotal: Money,
    val yearOverYear: YearOverYear,
    val stats: YearStats,
    val chartScalePaise: Long,
) {
    init {
        require(monthlyNet.size == MONTHS_IN_YEAR) {
            "the chart always has $MONTHS_IN_YEAR bar slots (D9), got ${monthlyNet.size}"
        }
        require(chartScalePaise >= 1L) { "a zero scale would make every bar an undefined division" }
    }

    /** Tracked rows inside the year, tombstones excluded. */
    val entryCount: Int
        get() = stats.entryCount

    /** Distinct calendar days that carried at least one entry. */
    val dayCount: Int
        get() = stats.dayCount

    /** Heaviest single-day net inside the year; ₹0 when there is no activity. */
    val maxDay: Money
        get() = stats.maxDay

    /** Net total divided by the tracked days, rounded to the nearest paise. */
    val avgPerActiveDay: Money
        get() = stats.avgPerActiveDay

    /** This year's net over the period that [previousYearSamePeriodNet] covers. */
    val currentPeriodNet: Money
        get() = yearOverYear.currentPeriod

    /** Last year's net over the same period. */
    val previousYearSamePeriodNet: Money
        get() = yearOverYear.previousPeriod

    /** This period against last year, in the same direction as every other figure. */
    val yoyDelta: Money
        get() = yearOverYear.delta

    /** True when the year holds no tracked entry — the empty state still has twelve bars. */
    val isEmpty: Boolean
        get() = entryCount == 0

    /** True when refunds pushed at least one month below zero (D5). */
    val hasNegativeMonth: Boolean
        get() = monthlyNet.any { money -> money.isCredit }

    /** Signed net for a 1-based [month] (1 = January … 12 = December). */
    fun netForMonth(month: Int): Money {
        require(month in 1..MONTHS_IN_YEAR) { "month must be 1..$MONTHS_IN_YEAR, got $month" }
        return monthlyNet[month - 1]
    }

    /**
     * Height of the [month] bar as a signed thousandth of [chartScalePaise].
     *
     * One shared scale is the whole point (§9): a month that refunds more than it spends
     * draws downwards against the same baseline instead of rescaling itself to full height,
     * which is exactly what would make the year view lie. Integer per-mille keeps the UI out
     * of float maths on money.
     */
    fun barPermille(month: Int): Int {
        val paise = netForMonth(month).paise
        return ((paise * PERMILLE) / chartScalePaise).toInt()
    }

    private companion object {
        const val PERMILLE = 1_000L
    }
}
