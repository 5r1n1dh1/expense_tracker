package com.srinidhi.kharch.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.core.model.Expense
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.category.CategoryRules
import com.srinidhi.kharch.domain.dashboard.CategoryTotal
import com.srinidhi.kharch.domain.dashboard.MonthlyBreakdown
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.KharchType
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import kotlin.math.abs

/*
 * The drill-down (§9): one month, its category split and the refunds that came back.
 *
 * The sheet lists the refunds outright rather than only netting them away, because
 * "I got ₹4,000 back" and "I spent ₹4,000 less" are different facts about the same month,
 * and conflating them is how a ledger stops being trustworthy (D5).
 */

/** Inner inset of the sheet. */
private val SHEET_PADDING = PaddingValues(horizontal = 20.dp, vertical = 8.dp)

/** Width of the sheet's grab handle. */
private val HANDLE_WIDTH = 40.dp

/** Thickness of a category row's proportion bar. */
private val ROW_BAR_HEIGHT = 4.dp

/** Alpha of the unfilled part of a proportion bar, and of the grab handle. */
private const val TRACK_ALPHA = 0.18f

/** Size of the refund glyph. */
private val REFUND_ICON_SIZE = 16.dp

/**
 * The month detail sheet.
 *
 * @param breakdown the month to show, aggregated by `GetMonthlyBreakdown`.
 * @param categories every category, used to name the breakdown's rows (D4).
 * @param onDismissRequest invoked when the sheet is dismissed.
 * @param modifier layout only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDetailSheet(
    breakdown: MonthlyBreakdown,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labels = remember(categories) {
        CategoryRules.visibleCategories(categories).associate { category -> category.id to category.name }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
    ) {
        GlassSurface(
            tier = GlassTier.Exaggerated,
            shape = GlassShape.sheet,
            contentPadding = SHEET_PADDING,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetHandle()
                Spacer(modifier = Modifier.height(16.dp))
                SheetHeadline(breakdown = breakdown)
                Spacer(modifier = Modifier.height(20.dp))
                CategorySection(breakdown = breakdown, labels = labels)
                if (breakdown.refunds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    RefundSection(refunds = breakdown.refunds, labels = labels)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** A small glass handle so the sheet reads as draggable. */
@Composable
private fun SheetHandle() {
    val glass = currentGlassTheme()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDLE_WIDTH)
            .clip(CircleShape)
            .background(glass.colors.onSecondary.copy(alpha = TRACK_ALPHA)),
    )
}

/** The month's title and its gross / refunds / net trio. */
@Composable
private fun SheetHeadline(breakdown: MonthlyBreakdown) {
    val glass = currentGlassTheme()
    val formatter = InrFormatter.Default
    Column {
        Text(
            text = yearMonthLabel(breakdown.year, breakdown.month),
            style = MaterialTheme.typography.titleLarge,
            color = glass.colors.onPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatter.format(breakdown.netTotal),
            style = MaterialTheme.typography.headlineSmall,
            color = if (breakdown.isNetNegative) glass.colors.credit else glass.colors.onPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${formatter.format(breakdown.grossSpend)} spent · " +
                "${formatter.format(breakdown.totalRefunds)} back",
            style = MaterialTheme.typography.bodyMedium,
            color = glass.colors.onSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${breakdown.entryCount} entries · ${breakdown.dayCount} days tracked",
            style = KharchType.caption,
            color = glass.colors.onSecondary,
        )
    }
}

/** The month's category split, expandable past the top slice. */
@Composable
private fun CategorySection(breakdown: MonthlyBreakdown, labels: Map<String, String>) {
    val glass = currentGlassTheme()
    var expanded by remember(breakdown.year, breakdown.month) { mutableStateOf(false) }
    val shown = if (expanded) {
        breakdown.categoryTotals
    } else {
        breakdown.topCategories(MonthlyBreakdown.DEFAULT_TOP_CATEGORY_COUNT)
    }
    val remaining = breakdown.categoryTotals.size - shown.size
    val largest = breakdown.categoryTotals.firstOrNull()?.net?.paise ?: 0L

    Column {
        Text(
            text = "By category",
            style = KharchType.sectionLabel,
            color = glass.colors.onSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (shown.isEmpty()) {
            Text(
                text = "No expenses this month",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.colors.onSecondary,
            )
        } else {
            shown.forEach { total ->
                CategoryRow(total = total, label = labels[total.categoryId], largest = largest)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (remaining > 0) {
            Text(
                text = "and $remaining more categories",
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (expanded) "Show fewer" else "Show all",
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.expenseAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else if (expanded) {
            Text(
                text = "Show fewer",
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.expenseAccent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** One category row with its share of the month. */
@Composable
private fun CategoryRow(total: CategoryTotal, label: String?, largest: Long) {
    val glass = currentGlassTheme()
    val formatter = InrFormatter.Default
    val share = if (largest <= 0L) 0f else abs(total.net.paise).toFloat() / abs(largest).toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.takeUnless { it.isNullOrEmpty() } ?: UNCATEGORISED_LABEL,
                style = MaterialTheme.typography.titleSmall,
                color = glass.colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatter.format(total.net),
                style = KharchType.amountRow,
                color = if (total.net.isCredit) glass.colors.credit else glass.colors.onPrimary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_BAR_HEIGHT)
                .clip(CircleShape)
                .background(glass.colors.onSecondary.copy(alpha = TRACK_ALPHA)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(ROW_BAR_HEIGHT)
                    .clip(CircleShape)
                    .background(glass.colors.expenseAccent),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "${total.entryCount} entries",
            style = KharchType.caption,
            color = glass.colors.onSecondary,
        )
    }
}

/** The refunds recorded in the month, oldest first (D5). */
@Composable
private fun RefundSection(refunds: List<Expense>, labels: Map<String, String>) {
    val glass = currentGlassTheme()
    val formatter = InrFormatter.Default
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Refunds",
            style = KharchType.sectionLabel,
            color = glass.colors.onSecondary,
        )
        refunds.forEach { refund ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_refund),
                    contentDescription = "Refund",
                    tint = glass.colors.credit,
                    modifier = Modifier.size(REFUND_ICON_SIZE),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = refund.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.colors.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatter.format(refund.money),
                    style = KharchType.amountRow,
                    color = glass.colors.credit,
                    maxLines = 1,
                )
            }
            Text(
                text = labels[refund.categoryId].takeUnless { it.isNullOrEmpty() } ?: UNCATEGORISED_LABEL,
                style = KharchType.caption,
                color = glass.colors.onSecondary,
            )
        }
    }
}
