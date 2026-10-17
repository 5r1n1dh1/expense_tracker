package com.srinidhi.kharch.ui.expenses

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.model.ExpenseKind
import com.srinidhi.kharch.core.model.MagnitudePaise
import com.srinidhi.kharch.ui.theme.KharchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExpenseRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val expense = Expense(
        id = "e1",
        name = "Lunch",
        amount = MagnitudePaise(12_345L),
        kind = ExpenseKind.EXPENSE,
        categoryId = "sys_food",
        dateEpochDay = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val refund = Expense(
        id = "r1",
        name = "Refund lunch",
        amount = MagnitudePaise(5_000L),
        kind = ExpenseKind.REFUND,
        categoryId = "sys_food",
        dateEpochDay = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `renders the expense name`() {
        composeTestRule.setContent {
            KharchTheme(darkTheme = false, dynamicColor = false) {
                ExpenseRow(expense = expense)
            }
        }

        composeTestRule.onNodeWithText("Lunch").assertIsDisplayed()
    }

    @Test
    fun `renders the amount for an expense`() {
        composeTestRule.setContent {
            KharchTheme(darkTheme = false, dynamicColor = false) {
                ExpenseRow(expense = expense)
            }
        }

        composeTestRule.onNodeWithTag(EXPENSE_ROW_AMOUNT_TAG)
            .assertTextEquals("₹123.45")
    }

    @Test
    fun `renders a refund badge and credit amount`() {
        composeTestRule.setContent {
            KharchTheme(darkTheme = false, dynamicColor = false) {
                ExpenseRow(expense = refund)
            }
        }

        composeTestRule.onNodeWithTag(EXPENSE_ROW_REFUND_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("refund").assertIsDisplayed()
        composeTestRule.onNodeWithTag(EXPENSE_ROW_AMOUNT_TAG)
            .assertTextEquals("-₹50")
    }
}
