package com.srinidhi.kharch.ui.widget

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.YearMonth
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.domain.widget.ExpenseDomain
import com.srinidhi.kharch.domain.widget.aggregateWidgetSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aggregation behind the Kharch home-screen widget (plan §10, §7 widget tests).
 *
 * The aggregation is pure Kotlin, so the month net, the today total, the sparkline
 * and the negative-month case are all pinned here without a database, a widget
 * host or a Compose tree.
 */
class KharchWidgetTest {

    @Test
    fun `monthly net subtracts refunds from charges`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 15))
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                ExpenseDomain(
                    id = "e1",
                    amountPaise = 5000L,
                    kind = ExpenseKind.EXPENSE,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 10,
                    categoryId = "sys_food",
                ),
                ExpenseDomain(
                    id = "e2",
                    amountPaise = 2000L,
                    kind = ExpenseKind.REFUND,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 12,
                    categoryId = "sys_food",
                ),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(3000L, summary.netPaise)
    }

    @Test
    fun `a refund heavy month nets negative`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 1))
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                ExpenseDomain(
                    id = "e1",
                    amountPaise = 3000L,
                    kind = ExpenseKind.REFUND,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 1,
                    categoryId = "sys_food",
                ),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(-3000L, summary.netPaise)
        assertTrue(summary.netPaise < 0L)
    }

    @Test
    fun `sparkline contains exactly seven points`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 15))
        val summary = aggregateWidgetSummary(
            expenses = emptyList(),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(7, summary.sparkline.size)
    }

    @Test
    fun `today total aggregates the current day`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 15))
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                ExpenseDomain(
                    id = "e1",
                    amountPaise = 64000L,
                    kind = ExpenseKind.EXPENSE,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 15,
                    categoryId = "sys_food",
                ),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(64000L, summary.todayPaise)
    }
}
