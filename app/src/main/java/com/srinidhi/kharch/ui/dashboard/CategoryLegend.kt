package com.srinidhi.kharch.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.ui.motion.Motion
import com.srinidhi.kharch.ui.motion.rememberReducedMotion
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The donut's legend (§7.2A): per-category NET totals, biggest first, with a proportion
 * bar per row.
 *
 * The net is the headline on purpose. "How much did I spend" and "how much came back"
 * are two questions, and a category that nets to zero after a refund genuinely earned
 * nothing — showing the gross there would be the lie the dashboard exists to avoid (D5).
 */

/** The colour swatch in front of a legend row. */
private val SWATCH_SIZE = 10.dp

/** Thickness of a row's proportion bar. */
private val BAR_HEIGHT = 4.dp

/** Alpha of the unfilled part of a proportion bar. */
private const val BAR_TRACK_ALPHA = 0.18f

/** Tag prefix a legend row is found by in UI tests. */
private const val LEGEND_ROW_TAG_PREFIX = "legend-row-"

/**
 * The legend for [slices].
 *
 * @param slices the year's slices, already ordered largest first.
 * @param modifier layout only.
 * @param onCategorySelected optional drill-down; tapping a row filters the Expenses list.
 */
@Composable
fun CategoryLegend(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    onCategorySelected: ((String) -> Unit)? = null,
) {
    val formatter = InrFormatter.Default
    val largest = slices.maxOfOrNull { slice -> slice.sharePermille } ?: 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slices.forEach { slice ->
            LegendRow(
                slice = slice,
                largestShare = largest,
                formatter = formatter,
                onClick = onCategorySelected,
            )
        }
    }
}

/** One legend row: swatch, name, an explicit credit label, the net and its proportion. */
@Composable
private fun LegendRow(
    slice: DonutSlice,
    largestShare: Int,
    formatter: InrFormatter,
    onClick: ((String) -> Unit)?,
) {
    val glass = currentGlassTheme()
    val reducedMotion = rememberReducedMotion()
    val fraction = if (largestShare <= 0) 0f else slice.sharePermille.toFloat() / largestShare
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else Motion.Emphasized,
        label = "legend-proportion-${slice.categoryId}",
    )
    val clickable = onClick != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LEGEND_ROW_TAG_PREFIX + slice.categoryId)
            .then(
                if (clickable) {
                    Modifier.clickable(role = Role.Button) { onClick?.invoke(slice.categoryId) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(if (slice.isCredit) glass.colors.credit else slice.colour),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = slice.label,
                style = MaterialTheme.typography.titleSmall,
                color = glass.colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (slice.isCredit) {
                Spacer(modifier = Modifier.width(8.dp))
                CreditLabel(colour = glass.colors.credit)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatter.format(slice.net),
                style = MaterialTheme.typography.labelLarge,
                color = if (slice.isCredit) glass.colors.credit else glass.colors.onPrimary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        ProportionBar(fraction = animated, colour = slice.colour, credit = slice.isCredit)
    }
}

/** The "credit" word that accompanies a ghosted slice — never colour alone (§7.2A). */
@Composable
private fun CreditLabel(colour: Color) {
    Text(
        text = "credit",
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(colour.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A row's share of the largest category. */
@Composable
private fun ProportionBar(fraction: Float, colour: Color, credit: Boolean) {
    val glass = currentGlassTheme()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(CircleShape)
            .background(glass.colors.onSecondary.copy(alpha = BAR_TRACK_ALPHA)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(if (credit) glass.colors.credit else colour),
        )
    }
}
