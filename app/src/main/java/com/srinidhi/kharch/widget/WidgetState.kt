package com.srinidhi.kharch.widget

import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.model.Money
import java.time.YearMonth

data class WidgetSummary(
    val monthYear: YearMonth,
    val netPaise: Long,
    val todayPaise: Long,
    val sparkline: List<Long>,
    val topCategoryNames: List<String>,
    val isSynced: Boolean,
)

fun aggregateSummary(
    expenses: List<ExpenseDomain>,
    month: YearMonth,
): WidgetSummary {
    val net = expenses.filter { it.yearMonth == month }.sumOf { it.money.paise }
    return WidgetSummary(
        monthYear = month,
        netPaise = net,
        todayPaise = 0L,
        sparkline = List(7) { 0L },
        topCategoryNames = emptyList(),
        isSynced = true,
    )
}

data class ExpenseDomain(
    val amountPaise: Long,
    val kind: ExpenseKind,
    val yearMonth: YearMonth,
)

val ExpenseDomain.money: Money
    get() = Money.of(MagnitudePaise(amountPaise), kind)
