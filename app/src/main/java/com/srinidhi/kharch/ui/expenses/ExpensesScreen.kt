package com.srinidhi.kharch.ui.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srinidhi.kharch.ui.glass.GlassSurface

@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { Text("Expenses", modifier = Modifier.padding(16.dp)) },
    ) { padding ->
        GlassSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                state.sections.forEach { section ->
                    section.expenses.forEach { expense ->
                        ExpenseRow(expense = expense)
                    }
                }
                if (!state.hasRows) {
                    Text("No expenses yet.", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
