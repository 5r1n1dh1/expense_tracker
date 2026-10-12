package com.srinidhi.kharch.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The year selector (§7.2A): a bottom sheet of year pills, newest first.
 *
 * Years come from the ledger, not from a hardcoded window: a year that has no rows yet is
 * still offered (it is the year the user is living in), and a year that has rows is always
 * offered, however old the data.
 */

/** Tag prefix a year option is found by in UI tests. */
private const val YEAR_OPTION_TAG_PREFIX = "dashboard-year-option-"

/**
 * The year selector sheet.
 *
 * @param years every year on offer, newest first.
 * @param selectedYear the year currently on screen.
 * @param onYearSelected invoked with the chosen year, then the sheet dismisses.
 * @param onDismissRequest invoked when the sheet is dismissed without a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSelectorSheet(
    years: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
    ) {
        val glass = currentGlassTheme()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Choose a year",
                style = MaterialTheme.typography.titleLarge,
                color = glass.colors.onPrimary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Swipe the chart to step one year at a time",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            years.chunked(OPTIONS_PER_ROW).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    chunk.forEach { year ->
                        YearOption(
                            year = year,
                            selected = year == selectedYear,
                            onClick = { onYearSelected(year) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep the last row's options the same width as every other row's.
                    repeat(OPTIONS_PER_ROW - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One year pill. */
@Composable
private fun YearOption(
    year: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentGlassTheme()
    val tier = if (selected) GlassTier.Exaggerated else GlassTier.Regular
    val textColor = if (selected) glass.colors.onPrimary else glass.colors.onSecondary
    GlassSurface(
        modifier = modifier
            .clip(GlassShape.chip)
            .clickable(role = Role.Button) { onClick() }
            .testTag(YEAR_OPTION_TAG_PREFIX + year),
        tier = tier,
        shape = GlassShape.chip,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** How many year pills fit on one row. */
private const val OPTIONS_PER_ROW = 3
