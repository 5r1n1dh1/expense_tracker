package com.srinidhi.kharch.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.domain.expense.ExpenseFilter
import com.srinidhi.kharch.ui.components.glassTextFieldColors
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import java.time.LocalDate

/** Test tag for the filter sheet root. */
const val FILTER_SHEET_TAG: String = "expense-filter-sheet"

/** Test tag for the filter search field. */
const val FILTER_SEARCH_TAG: String = "expense-filter-search"

/** Test tag for the filter clear action. */
const val FILTER_CLEAR_TAG: String = "expense-filter-clear"

/** Test tag for the filter apply action. */
const val FILTER_APPLY_TAG: String = "expense-filter-apply"

@Composable
fun FilterSheet(
    filter: ExpenseFilter,
    categories: List<Category>,
    onFilterChange: (ExpenseFilter) -> Unit,
    onClear: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    var queryText by remember(filter.query) { mutableStateOf(filter.query) }
    var fromText by remember(filter.fromEpochDay) {
        mutableStateOf(filter.fromEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "")
    }
    var toText by remember(filter.toEpochDay) {
        mutableStateOf(filter.toEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "")
    }
    var minText by remember(filter.minPaise) {
        mutableStateOf(filter.minPaise?.let { (it / 100).toString() } ?: "")
    }
    var maxText by remember(filter.maxPaise) {
        mutableStateOf(filter.maxPaise?.let { (it / 100).toString() } ?: "")
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FILTER_SHEET_TAG),
        tier = GlassTier.Exaggerated,
        shape = GlassShape.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Filter expenses",
                style = MaterialTheme.typography.titleLarge,
                color = glass.colors.onPrimary,
            )
            TextField(
                value = queryText,
                onValueChange = { value ->
                    queryText = value
                    onFilterChange(filter.withQuery(value))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FILTER_SEARCH_TAG),
                label = { Text("Search") },
                colors = glassTextFieldColors(),
                singleLine = true,
            )
            Text(
                text = "Date range",
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = fromText,
                    onValueChange = { value ->
                        fromText = value
                        onFilterChange(
                            filter.copy(fromEpochDay = parseEpochDay(value)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("From (yyyy-MM-dd)") },
                    colors = glassTextFieldColors(),
                    singleLine = true,
                )
                TextField(
                    value = toText,
                    onValueChange = { value ->
                        toText = value
                        onFilterChange(
                            filter.copy(toEpochDay = parseEpochDay(value)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("To (yyyy-MM-dd)") },
                    colors = glassTextFieldColors(),
                    singleLine = true,
                )
            }
            Text(
                text = "Categories",
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category.id in filter.categoryIds,
                        selectionRole = androidx.compose.ui.semantics.Role.Checkbox,
                        onClick = {
                            val next = if (category.id in filter.categoryIds) {
                                filter.categoryIds - category.id
                            } else {
                                filter.categoryIds + category.id
                            }
                            onFilterChange(filter.copy(categoryIds = next))
                        },
                    )
                }
            }
            Text(
                text = "Amount range",
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = minText,
                    onValueChange = { value ->
                        minText = value
                        onFilterChange(
                            filter.copy(minPaise = ExpenseFilter.parseAmountInput(value)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Min") },
                    colors = glassTextFieldColors(),
                    singleLine = true,
                )
                TextField(
                    value = maxText,
                    onValueChange = { value ->
                        maxText = value
                        onFilterChange(
                            filter.copy(maxPaise = ExpenseFilter.parseAmountInput(value)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Max") },
                    colors = glassTextFieldColors(),
                    singleLine = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) {
                            onClear()
                            onDismiss()
                        }
                        .testTag(FILTER_CLEAR_TAG),
                    tier = GlassTier.Inset,
                    shape = GlassShape.chip,
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.colors.onPrimary,
                    )
                }
                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .testTag(FILTER_APPLY_TAG),
                    tier = GlassTier.Inset,
                    shape = GlassShape.chip,
                ) {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.colors.onPrimary,
                    )
                }
            }
        }
    }
}

private fun parseEpochDay(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return try {
        LocalDate.parse(trimmed).toEpochDay()
    } catch (error: java.time.format.DateTimeParseException) {
        null
    }
}
