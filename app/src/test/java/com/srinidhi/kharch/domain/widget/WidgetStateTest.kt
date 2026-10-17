package com.srinidhi.kharch.domain.widget

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.time.FakeClock
import com.srinidhi.kharch.core.model.YearMonth
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget summary aggregation, including net-negative months (plan §13.2 D5).
 */
class WidgetStateTest {

    @Test
    fun `net month subtracts refunds from charges`() {
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
                    kind = ExpenseKind.EXPENSE,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 1,
                    categoryId = "sys_food",
                ),
                ExpenseDomain(
                    id = "e2",
                    amountPaise = 5000L,
                    kind = ExpenseKind.REFUND,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 2,
                    categoryId = "sys_food",
                ),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(-2000L, summary.netPaise)
        assertTrue(summary.netPaise < 0L)
    }

    @Test
    fun `an all refund month is net negative`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 1))
        val summary = aggregateWidgetSummary(
            expenses = listOf(
                ExpenseDomain(
                    id = "e1",
                    amountPaise = 4000L,
                    kind = ExpenseKind.REFUND,
                    yearMonth = YearMonth.of(2026, 8),
                    dayOfMonth = 1,
                    categoryId = "sys_food",
                ),
            ),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(-4000L, summary.netPaise)
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

    @Test
    fun `empty month nets zero`() {
        val clock = FakeClock()
        clock.setTo(LocalDate.of(2026, 8, 15))
        val summary = aggregateWidgetSummary(
            expenses = emptyList(),
            clock = clock,
            month = YearMonth.of(2026, 8),
        )

        assertEquals(0L, summary.netPaise)
        assertEquals(0L, summary.todayPaise)
    }
}
