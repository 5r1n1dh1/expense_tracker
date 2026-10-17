package com.srinidhi.kharch.domain.widget

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.core.time.Clock
import java.time.YearMonth

data class WidgetSummary(
    val monthYear: YearMonth,
    val netPaise: Long,
    val todayPaise: Long,
    val sparkline: List<Long>,
    val topCategoryNames: List<String>,
    val isSynced: Boolean,
)

fun aggregateWidgetSummary(
    expenses: List<ExpenseDomain>,
    clock: Clock,
    month: YearMonth,
): WidgetSummary {
    val monthExpenses = expenses.filter { it.yearMonth == month }
    val net = monthExpenses.sumOf { it.money.paise }
    val today = clock.today()
    val todayExpenses = expenses.filter {
        it.yearMonth == YearMonth.of(today.year, today.monthValue) &&
            it.dayOfMonth == today.dayOfMonth
    }
    val todayNet = todayExpenses.sumOf { it.money.paise }
    val sparkline = List(7) { index ->
        val day = today.minusDays(6L - index)
        expenses.filter {
            it.yearMonth == YearMonth.of(day.year, day.monthValue) &&
                it.dayOfMonth == day.dayOfMonth
        }.sumOf { it.money.paise }
    }
    return WidgetSummary(
        monthYear = month,
        netPaise = net,
        todayPaise = todayNet,
        sparkline = sparkline,
        topCategoryNames = emptyList(),
        isSynced = true,
    )
}

data class ExpenseDomain(
    val id: String,
    val amountPaise: Long,
    val kind: ExpenseKind,
    val yearMonth: YearMonth,
    val dayOfMonth: Int,
    val categoryId: String,
)

val ExpenseDomain.money: Money
    get() = Money.of(MagnitudePaise(amountPaise), kind)
