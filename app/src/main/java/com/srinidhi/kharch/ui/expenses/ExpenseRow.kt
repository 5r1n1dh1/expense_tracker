package com.srinidhi.kharch.ui.expenses

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.srinidhi.kharch.core.model.Expense

@Composable
fun ExpenseRow(expense: Expense, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = expense.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        val signed = expense.money
        val amountText = if (signed.isCredit) {
            "−₹${signed.magnitude.toRupees()}"
        } else {
            "₹${signed.magnitude.toRupees()}"
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
