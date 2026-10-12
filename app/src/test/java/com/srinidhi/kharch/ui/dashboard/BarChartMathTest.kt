package com.srinidhi.kharch.ui.dashboard

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.domain.dashboard.GetYearlyDashboard
import com.srinidhi.kharch.domain.dashboard.MONTHS_IN_YEAR
import com.srinidhi.kharch.domain.dashboard.YearDashboard
import com.srinidhi.kharch.domain.expense
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The chart's geometry, on the JVM (§7.2A, D5).
 *
 * Every assertion here is a claim about money rather than about pixels: a positive month
 * grows up, a net-negative month grows down against the same baseline, a zero month keeps a
 * visible track, and no month is ever rescaled to fill the chart. That last one is the claim
 * that stops the year view from lying.
 */
class BarChartMathTest {

    private val clock = FakeClock()
    private val useCase = GetYearlyDashboard(clock, FakeClock.KOLKATA)
    private val today = LocalDate.of(2026, 12, 31)

    /** A year with one positive month, one net-negative month, and ten empty ones. */
    private fun mixedYear(): YearDashboard = useCase.calculate(
        listOf(
            expense("a", LocalDate.of(2026, 3, 10), 1_000L),
            expense("b", LocalDate.of(2026, 6, 10), 500L, kind = ExpenseKind.REFUND),
        ),
        2026,
        today,
    )

    /** A year where every month is at or above zero. */
    private fun positiveYear(): YearDashboard = useCase.calculate(
        listOf(
            expense("a", LocalDate.of(2026, 1, 10), 300L),
            expense("b", LocalDate.of(2026, 4, 10), 600L),
        ),
        2026,
        today,
    )

    /** A year whose only tracked month refunded more than it spent. */
    private fun refundOnlyYear(): YearDashboard = useCase.calculate(
        listOf(
            expense("a", LocalDate.of(2026, 1, 10), 400L),
            expense("b", LocalDate.of(2026, 1, 20), 1_000L, kind = ExpenseKind.REFUND),
        ),
        2026,
        today,
    )

    @Test
    fun `a positive month is drawn above the zero axis`() {
        val axis = BarChartMath.axis(hasNegativeMonth = false)
        val placement = BarChartMath.placement(750, axis)
        assertFalse("a positive permille must grow upwards", placement.growsDown)
        assertTrue("the bar must start above the baseline", placement.top < axis.baseline)
        assertEquals(0.75f, placement.height, EPSILON)
    }

    @Test
    fun `a net negative month is drawn downwards and never clamped to zero`() {
        val axis = BarChartMath.axis(hasNegativeMonth = true)
        val placement = BarChartMath.placement(-400, axis)
        assertTrue("a negative permille must grow downwards", placement.growsDown)
        assertEquals("the bar must start exactly on the axis", axis.baseline, placement.top, EPSILON)
        assertTrue("the bar must extend into the downside room", placement.height > 0f)
        assertEquals(0.2f, placement.height, EPSILON)
    }

    @Test
    fun `a zero month draws no bar but still renders a visible baseline track`() {
        val axis = BarChartMath.axis(hasNegativeMonth = false)
        assertEquals(0f, BarChartMath.placement(0, axis).height, EPSILON)
        val track = BarChartMath.baselineTrack(axis)
        assertTrue("a zero month must keep a visible track", track.height > 0f)
        assertTrue("the track must sit on the baseline", track.top <= axis.baseline)
    }

    @Test
    fun `the scale is shared across the year and never re-derived per month`() {
        val axis = BarChartMath.axis(hasNegativeMonth = false)
        val full = BarChartMath.placement(1_000, axis)
        val half = BarChartMath.placement(500, axis)
        val quarter = BarChartMath.placement(250, axis)
        // One shared denominator: every month is a fraction of the same thousand.
        assertEquals(full.height, half.height * 2f, EPSILON)
        assertEquals(full.height, quarter.height * 4f, EPSILON)
        // A per-month scale would normalise the 500 slot to fill the track; it must not.
        assertTrue("a mid month must not fill the track", half.height < 1f)
    }

    @Test
    fun `a net negative month is measured against the same scale as a positive one`() {
        val dashboard = mixedYear()
        val axis = BarChartMath.axis(dashboard.hasNegativeMonth)
        val positive = BarChartMath.placement(dashboard.barPermille(3), axis)
        val negative = BarChartMath.placement(dashboard.barPermille(6), axis)
        // March spent 1,000 and June refunded 500 against the year's single scale.
        assertTrue(positive.growsDown.not())
        assertTrue(negative.growsDown)
        assertEquals(
            "the 1,000 month must be exactly twice the -500 month",
            positive.height,
            negative.height * 2f,
            EPSILON,
        )
    }

    @Test
    fun `the axis moves to the centre only once a month is net negative`() {
        val bottom = BarChartMath.axis(hasNegativeMonth = false)
        assertFalse("an all-positive year draws from the bottom", bottom.centred)
        assertEquals(1f, bottom.baseline, EPSILON)

        val centred = BarChartMath.axis(hasNegativeMonth = true)
        assertTrue("a refund-heavy year centres the axis", centred.centred)
        assertEquals(0.5f, centred.baseline, EPSILON)
    }

    @Test
    fun `every slot of the year touches the same baseline`() {
        val dashboard = mixedYear()
        val axis = BarChartMath.axis(dashboard.hasNegativeMonth)
        BarChartMath.slots(dashboard, currentMonth = null, selectedMonth = null).forEach { slot ->
            val placement = BarChartMath.placement(slot.signedPermille, axis)
            val touched = if (placement.growsDown) {
                abs(placement.top - axis.baseline) < EPSILON
            } else {
                abs(placement.top + placement.height - axis.baseline) < EPSILON
            }
            assertTrue("month ${slot.month} must touch the shared axis", touched)
        }
    }

    @Test
    fun `every year resolves twelve bar slots whatever its contents`() {
        listOf(mixedYear(), positiveYear(), refundOnlyYear(), emptyYear()).forEach { dashboard ->
            val slots = BarChartMath.slots(dashboard, currentMonth = null, selectedMonth = null)
            assertEquals(MONTHS_IN_YEAR, slots.size)
            assertEquals((1..MONTHS_IN_YEAR).toList(), slots.map { it.month })
        }
    }

    @Test
    fun `an empty year still offers a usable shared scale`() {
        val dashboard = emptyYear()
        assertTrue("an empty year must read as empty", dashboard.isEmpty)
        assertTrue(
            "every slot must be zero",
            BarChartMath.slots(dashboard, currentMonth = null, selectedMonth = null).all { it.isZero },
        )
        assertNull("an empty year has no peak", BarChartMath.peakMonth(dashboard))
    }

    @Test
    fun `the peak month is the heaviest positive month`() {
        val dashboard = positiveYear()
        assertEquals(4, BarChartMath.peakMonth(dashboard))
        val slots = BarChartMath.slots(dashboard, currentMonth = null, selectedMonth = null)
        assertTrue("only April is the peak", slots.count { it.isPeak } == 1)
        assertTrue("April is the peak", slots[3].isPeak)
    }

    @Test
    fun `a year of pure refunds awards the glow to nobody`() {
        assertNull(BarChartMath.peakMonth(refundOnlyYear()))
    }

    @Test
    fun `marking the current month does not change any bar height`() {
        val plain = BarChartMath.slots(mixedYear(), currentMonth = null, selectedMonth = null)
        val marked = BarChartMath.slots(mixedYear(), currentMonth = 3, selectedMonth = 3)
        plain.forEachIndexed { index, slot ->
            assertEquals(slot.signedPermille, marked[index].signedPermille)
        }
        assertTrue("March is current", marked[2].isCurrentMonth)
        assertTrue("March is selected", marked[2].isSelected)
    }

    @Test
    fun `a swipe past the threshold changes the year and a shorter one does not`() {
        assertTrue(BarChartMath.shouldStepYear(80f, 56f))
        assertTrue(BarChartMath.shouldStepYear(-80f, 56f))
        assertFalse("a drag short of the threshold must not step", BarChartMath.shouldStepYear(20f, 56f))
        assertFalse("a stationary pointer must not step", BarChartMath.shouldStepYear(0f, 56f))
    }

    @Test
    fun `the swipe rubber bands instead of dragging the chart off screen`() {
        val limit = 56f
        val short = BarChartMath.swipeOffset(rawOffset = 10f, blocked = false, limitPx = limit)
        val long = BarChartMath.swipeOffset(rawOffset = 400f, blocked = false, limitPx = limit)
        assertTrue("a short drag must be nearly linear", short < 10f)
        assertTrue("a long drag must approach rather than exceed the limit", long < limit)
        assertTrue("the offset must grow monotonically", long > short)
        assertEquals(
            "left and right must be symmetric",
            short,
            BarChartMath.swipeOffset(-10f, blocked = false, limitPx = limit),
            EPSILON,
        )
    }

    @Test
    fun `a swipe with nowhere to go resists harder`() {
        val free = BarChartMath.swipeOffset(rawOffset = 40f, blocked = false, limitPx = 56f)
        val blocked = BarChartMath.swipeOffset(rawOffset = 40f, blocked = true, limitPx = 56f)
        assertTrue("the newest year must resist harder", blocked < free)
    }

    @Test
    fun `a dismissed drag leaves no offset behind`() {
        assertEquals(0f, BarChartMath.swipeOffset(0f, blocked = false, limitPx = 56f), EPSILON)
    }

    private fun emptyYear(): YearDashboard = useCase.calculate(emptyList(), 2026, today)

    private companion object {
        const val EPSILON = 0.0001f
    }
}
