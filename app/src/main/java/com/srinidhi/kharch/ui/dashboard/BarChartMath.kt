package com.srinidhi.kharch.ui.dashboard

import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.domain.dashboard.MONTHS_IN_YEAR
import com.srinidhi.kharch.domain.dashboard.YearDashboard
import kotlin.math.abs
import kotlin.math.sign

/**
 * One bar of the twelve-month chart, resolved against the **shared** year scale (§9).
 *
 * @property month 1-based calendar month, 1 = January.
 * @property net the month's signed net, negative when refunds outweigh spend (D5).
 * @property signedPermille signed thousandth of [YearDashboard.chartScalePaise], straight
 *   from `YearDashboard.barPermille`. The sign is the direction, the magnitude is the
 *   height — the UI never divides money by anything itself.
 * @property isCredit `true` when the month nets below zero, so the bar grows downwards.
 * @property isZero `true` when the month nets to exactly zero; it still renders a track.
 */
data class BarSlot(
    val month: Int,
    val net: Money,
    val signedPermille: Int,
    val isCredit: Boolean,
    val isZero: Boolean,
    val isCurrentMonth: Boolean,
    val isSelected: Boolean,
    val isPeak: Boolean,
)

/**
 * Where the zero axis sits inside a chart track, as a fraction from the top.
 *
 * The axis is **not** per-month. It moves once for the whole year, and only when a month
 * actually went below zero (D5): every month below zero draws downwards against that one
 * axis, so the year reads as a single shared scale instead of twelve normalised ones.
 *
 * @property baseline fraction down the track where zero is drawn, 1 = the bottom edge.
 * @property centred `true` when at least one month is net negative.
 */
data class ChartAxis(val baseline: Float, val centred: Boolean)

/**
 * Where one bar sits inside its track, as fractions of the track height.
 *
 * Fractions rather than pixels: the maths is then testable on the JVM without a density,
 * and the canvas simply multiplies by `size.height` at draw time.
 *
 * @property top fraction from the top of the track to the top of the bar.
 * @property height fraction of the track the bar covers, 0 when the month nets to zero.
 * @property growsDown `true` for a net-negative month.
 */
data class BarPlacement(val top: Float, val height: Float, val growsDown: Boolean)

/**
 * The bar chart's geometry: everything the canvas draws that is not a colour.
 *
 * Pure Kotlin on purpose. Every rule here is a claim about money — that a negative month
 * grows down, that a zero month is not clamped away, that one scale covers the year — and
 * those claims are asserted in `BarChartMathTest` without an emulator.
 */
object BarChartMath {

    /** The unit `barPermille` reports in. */
    private const val MAX_PERMILLE = 1_000

    /** The unit above as a float, for the one division that turns it into a fraction. */
    private const val MAX_PERMILLE_F = 1_000f

    /** Height of the zero-month baseline track, as a fraction of the chart track. */
    private const val MIN_TRACK = 0.02f

    /** Zero axis when every month is at or above zero: the bottom of the track. */
    private const val BOTTOM_AXIS = 1f

    /** Zero axis once a month is net negative (D5): the centre of the track. */
    private const val CENTRED_AXIS = 0.5f

    /** Extra resistance applied to a swipe that has no year left to step to (§7.3). */
    private const val BLOCKED_RUBBER_SCALE = 0.3f

    /**
     * The zero axis for a year.
     *
     * [hasNegativeMonth] is a property of the whole year, never of the bar being drawn, so
     * the axis cannot shift under the user when they tap a different month.
     */
    fun axis(hasNegativeMonth: Boolean): ChartAxis =
        if (hasNegativeMonth) ChartAxis(CENTRED_AXIS, true) else ChartAxis(BOTTOM_AXIS, false)

    /**
     * Where a bar of [signedPermille] is drawn against [axis].
     *
     * A negative permille grows **downwards** from the axis into the downside room and is
     * never clamped to zero and never rescaled to fill the track — clamping would silently
     * report "no refunds this month" and rescaling would make one month look like the year.
     */
    fun placement(signedPermille: Int, axis: ChartAxis): BarPlacement {
        val growsDown = signedPermille < 0
        val room = if (growsDown) 1f - axis.baseline else axis.baseline
        val extent = abs(signedPermille).coerceAtMost(MAX_PERMILLE) / MAX_PERMILLE_F * room
        val top = if (growsDown) axis.baseline else axis.baseline - extent
        return BarPlacement(top = top, height = extent, growsDown = growsDown)
    }

    /**
     * The visible baseline track.
     *
     * A month that nets to zero must still be a tappable, visible target (D9): an empty
     * track keeps the bar's touch region alive and keeps the twelve slots at identical
     * bounds whether or not there is data behind them.
     */
    fun baselineTrack(axis: ChartAxis): BarPlacement = BarPlacement(
        top = (axis.baseline - MIN_TRACK / 2f).coerceIn(0f, 1f - MIN_TRACK),
        height = MIN_TRACK,
        growsDown = false,
    )

    /**
     * The month with the heaviest positive net, or `null` when no month earns anything.
     *
     * A year of pure refunds has no peak — awarding the glow to the least-negative month
     * would read as a reward for losing money.
     */
    fun peakMonth(dashboard: YearDashboard): Int? {
        var bestMonth: Int? = null
        var bestPaise = 0L
        for (month in 1..MONTHS_IN_YEAR) {
            val paise = dashboard.netForMonth(month).paise
            if (paise > bestPaise) {
                bestPaise = paise
                bestMonth = month
            }
        }
        return bestMonth
    }

    /**
     * All twelve slots, January first, in the order the Row draws them.
     *
     * Always exactly [MONTHS_IN_YEAR] entries: an empty year still has twelve bar slots so
     * the layout cannot reflow when the first expense lands (D9).
     */
    fun slots(
        dashboard: YearDashboard,
        currentMonth: Int?,
        selectedMonth: Int?,
    ): List<BarSlot> {
        val peak = peakMonth(dashboard)
        return (1..MONTHS_IN_YEAR).map { month ->
            val net = dashboard.netForMonth(month)
            BarSlot(
                month = month,
                net = net,
                signedPermille = dashboard.barPermille(month),
                isCredit = net.isCredit,
                isZero = net.paise == 0L,
                isCurrentMonth = month == currentMonth,
                isSelected = month == selectedMonth,
                isPeak = month == peak,
            )
        }
    }

    /** True when a raw drag of [rawOffset] is far enough to step the year (§7.3). */
    fun shouldStepYear(rawOffset: Float, thresholdPx: Float): Boolean =
        abs(rawOffset) >= thresholdPx && rawOffset != 0f

    /**
     * The visual offset of a drag, rubber-banded.
     *
     * The bars resist rather than flying off, approaching [limitPx] asymptotically, and
     * resist [BLOCKED_RUBBER_SCALE] times harder when there is no year left to step to.
     * The decision to change year uses the *raw* offset ([shouldStepYear]); damping the
     * visual offset is what makes the rubber band honest instead of just smaller.
     */
    fun swipeOffset(rawOffset: Float, blocked: Boolean, limitPx: Float): Float {
        if (rawOffset == 0f || limitPx <= 0f) return 0f
        val limit = if (blocked) limitPx * BLOCKED_RUBBER_SCALE else limitPx
        val magnitude = abs(rawOffset)
        return sign(rawOffset) * limit * magnitude / (limit + magnitude)
    }
}
