package com.srinidhi.kharch.domain

import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.core.time.MILLIS_PER_DAY
import java.time.LocalDate

// Shared fixtures for the domain unit tests.
//
// Time comes from the app-wide `core.time.FakeClock`, so every suite in the project drives the
// same injected `Clock` the same way. What lives here is only what the domain needs on top of
// it: expenses and categories. Keeping the constructors in one file means a change to a core
// model is a one-file fix rather than a sweep through every assertion in the suite.

/**
 * An expense on [date].
 *
 * @param amountRupees the magnitude in whole rupees: tests state rupees, the domain stores
 *   paise, and this is the one place the ×100 happens.
 */
fun expense(
    id: String,
    date: LocalDate,
    amountRupees: Long,
    categoryId: String = "sys_food",
    kind: ExpenseKind = ExpenseKind.EXPENSE,
    deletedAt: Long? = null,
): Expense {
    val timestamp = date.toEpochDay() * MILLIS_PER_DAY
    return Expense(
        id = id,
        name = "Test entry",
        amount = MagnitudePaise(amountRupees * PAISE_PER_RUPEE),
        kind = kind,
        categoryId = categoryId,
        dateEpochDay = date.toEpochDay(),
        createdAt = timestamp,
        updatedAt = timestamp,
        deletedAt = deletedAt,
    )
}

/** A category row; [sortOrder] defaults to 0 so the reorder tests spell the order out. */
fun category(
    id: String,
    name: String,
    sortOrder: Int = 0,
    isSystem: Boolean = false,
    isHidden: Boolean = false,
): Category = Category(
    id = id,
    name = name,
    iconKey = "ic_test",
    colorToken = "test",
    sortOrder = sortOrder,
    isSystem = isSystem,
    isHidden = isHidden,
)

/** The twelve seeded categories of §5.2, in their shipped order. */
fun seededCategories(): List<Category> = listOf(
    category("sys_food", "Food & Drink", sortOrder = 0, isSystem = true),
    category("sys_transport", "Transport", sortOrder = 1, isSystem = true),
    category("sys_shopping", "Shopping", sortOrder = 2, isSystem = true),
    category("sys_bills", "Bills & Utilities", sortOrder = 3, isSystem = true),
    category("sys_rent", "Rent", sortOrder = 4, isSystem = true),
    category("sys_health", "Health", sortOrder = 5, isSystem = true),
    category("sys_education", "Education", sortOrder = 6, isSystem = true),
    category("sys_entertainment", "Entertainment", sortOrder = 7, isSystem = true),
    category("sys_travel", "Travel", sortOrder = 8, isSystem = true),
    category("sys_groceries", "Groceries", sortOrder = 9, isSystem = true),
    category("sys_gifts", "Gifts & Donations", sortOrder = 10, isSystem = true),
    category("sys_cash", "Cash", sortOrder = 11, isSystem = true),
    category("sys_other", "Other", sortOrder = 12, isSystem = true),
)

/** A [LocalDate] without reaching for a formatter on every call site. */
fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

/** Paise in one rupee. */
const val PAISE_PER_RUPEE: Long = 100L
