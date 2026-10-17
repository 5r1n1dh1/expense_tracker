package com.srinidhi.kharch.ui.expenses

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.ui.theme.currentGlassTheme

fun expenseRowTag(expenseId: String): String = "expense-row-$expenseId"

const val EXPENSE_ROW_AMOUNT_TAG: String = "expense-row-amount"

const val EXPENSE_ROW_REFUND_BADGE_TAG: String = "expense-row-refund-badge"

@Composable
fun ExpenseRow(expense: Expense, modifier: Modifier = Modifier) {
    val glass = currentGlassTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(expenseRowTag(expense.id))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = expense.name,
            style = MaterialTheme.typography.bodyLarge,
            color = glass.colors.onPrimary,
            modifier = Modifier.weight(1f),
        )
        if (expense.isRefund) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = REFUND_GLYPH,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.credit,
                modifier = Modifier.testTag(EXPENSE_ROW_REFUND_BADGE_TAG),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = REFUND_LABEL,
                style = MaterialTheme.typography.labelSmall,
                color = glass.colors.credit,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = InrFormatter.Default.format(expense.money),
            style = MaterialTheme.typography.bodyMedium,
            color = if (expense.isRefund) glass.colors.credit else glass.colors.onPrimary,
            modifier = Modifier.testTag(EXPENSE_ROW_AMOUNT_TAG),
        )
    }
}

private const val REFUND_GLYPH: String = "↩"

private const val REFUND_LABEL: String = "refund"
