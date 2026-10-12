package com.srinidhi.kharch.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.money.InrFormatter
import com.srinidhi.kharch.domain.dashboard.YearDashboard
import com.srinidhi.kharch.ui.motion.Motion
import com.srinidhi.kharch.ui.motion.rememberReducedMotion
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import kotlinx.coroutines.delay

/*
 * The monthly split: twelve glass bars on one shared scale (§7.2A).
 *
 * The sign rules (D5) live in BarChartMath, not here. The drawing code is handed a
 * BarPlacement that already says which way a bar grows and how far, so the canvas can
 * never quietly clamp a net-negative month to zero or rescale it to fill the track.
 */

/**
 * Height of the bar track.
 *
 * The empty state's orb, headline, subline and call to action are sized to fit inside
 * exactly this height, so the populated chart and the empty orb occupy identical bounds
 * and nothing reflows when the first expense lands (D9).
 */
internal val CHART_TRACK_HEIGHT = 260.dp

/** Bar width, as a fraction of its slot. */
private const val BAR_WIDTH = 0.46f

/** Alpha of an unselected bar, against the selected bar's full opacity. */
private const val BAR_DIMMED_ALPHA = 0.45f

/** Alpha of the glow behind the best month. */
private const val PEAK_GLOW_ALPHA = 0.16f

/** Alpha of the zero-month baseline track. */
private const val TRACK_ALPHA = 0.16f

/** The 1 dp inner highlight a selected bar carries. */
private val SELECTION_STROKE = 1.dp

/** Radius of the current-month dot, as a fraction of the slot width. */
private const val CURRENT_MONTH_DOT = 0.16f

/** Tag prefix a bar is found by in UI tests. */
private const val BAR_TAG_PREFIX = "dashboard-bar-"

/**
 * The twelve-month bar chart.
 *
 * @param dashboard the year to draw. Its `chartScalePaise` is the single shared scale; the
 *   chart never derives a scale of its own.
 * @param currentMonth the month being lived through, marked with a dot. `null` when the
 *   viewer is looking at a past or future year.
 * @param selectedMonth the month whose detail sheet is open, drawn at full opacity.
 * @param onMonthSelected invoked with the 1-based month of a tapped bar.
 * @param modifier layout and the parallax gesture.
 */
@Composable
fun MonthlyBarChart(
    dashboard: YearDashboard,
    currentMonth: Int?,
    selectedMonth: Int?,
    onMonthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = BarChartMath.slots(dashboard, currentMonth, selectedMonth)
    val axis = BarChartMath.axis(dashboard.hasNegativeMonth)
    val palette = rememberBarPalette()
    val strokePx = with(LocalDensity.current) { SELECTION_STROKE.toPx() }
    val formatter = InrFormatter.Default

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            slots.forEach { slot ->
                Bar(
                    slot = slot,
                    axis = axis,
                    palette = palette,
                    strokePx = strokePx,
                    year = dashboard.year,
                    onMonthSelected = onMonthSelected,
                    description = "${monthShortLabel(slot.month)} ${formatter.format(slot.net)}",
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            slots.forEach { slot ->
                Text(
                    text = monthShortLabel(slot.month),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (slot.isCurrentMonth) palette.onPrimary else palette.onSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One bar slot: the track, the bar and the touch target.
 *
 * The grow animation is staggered by [Motion.ChartStaggerMs] per month and restarts on a
 * year change, so switching years re-grows the chart instead of snapping it (§7.4).
 */
@Composable
private fun Bar(
    slot: BarSlot,
    axis: ChartAxis,
    palette: BarPalette,
    strokePx: Float,
    year: Int,
    description: String,
    onMonthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val target = BarChartMath.placement(slot.signedPermille, axis)
    val reveal = remember(year, slot.month) { Animatable(0f) }
    LaunchedEffect(year, slot.month, target.height) {
        when {
            reducedMotion -> reveal.snapTo(1f)
            target.height <= 0f -> reveal.snapTo(1f)
            else -> {
                delay((slot.month - 1) * Motion.ChartStaggerMs)
                reveal.animateTo(targetValue = 1f, animationSpec = Motion.Emphasized)
            }
        }
    }

    Box(
        modifier = modifier
            .clickable(role = Role.Button) { onMonthSelected(slot.month) }
            .testTag(BAR_TAG_PREFIX + slot.month)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTrack(axis, palette)
            drawBar(target, slot, palette, strokePx, reveal.value, axis)
        }
    }
}

/** The colours one chart draws with, resolved once per composition. */
private data class BarPalette(
    val expense: Color,
    val credit: Color,
    val highlight: Color,
    val track: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val dot: Color,
)

@Composable
private fun rememberBarPalette(): BarPalette {
    val glass = currentGlassTheme()
    val tokens = glass.defaultTokens
    return remember(glass, tokens) {
        BarPalette(
            expense = glass.colors.expenseAccent,
            credit = glass.colors.credit,
            highlight = tokens.effectiveInnerStroke,
            track = glass.colors.onSecondary,
            onPrimary = glass.colors.onPrimary,
            onSecondary = glass.colors.onSecondary,
            dot = glass.colors.onPrimary,
        )
    }
}

/** The visible baseline track, present in every slot including the zero ones (D9). */
private fun DrawScope.drawTrack(axis: ChartAxis, palette: BarPalette) {
    val track = BarChartMath.baselineTrack(axis)
    drawRoundRect(
        color = palette.track.copy(alpha = TRACK_ALPHA),
        topLeft = Offset(0f, track.top * size.height),
        size = Size(size.width, track.height * size.height),
        cornerRadius = CornerRadius(size.width / 2f),
    )
}

/** The bar itself, its peak glow, its selection highlight and its current-month dot. */
private fun DrawScope.drawBar(
    target: BarPlacement,
    slot: BarSlot,
    palette: BarPalette,
    strokePx: Float,
    reveal: Float,
    axis: ChartAxis,
) {
    val height = target.height * reveal
    if (height <= 0f) return

    val width = size.width * BAR_WIDTH
    val left = (size.width - width) / 2f
    val radius = CornerRadius(width / 2f)
    val accent = if (slot.isCredit) palette.credit else palette.expense
    val top = if (target.growsDown) target.top else target.top + (1f - reveal) * target.height
    val barTop = top * size.height
    val barHeight = height * size.height

    if (slot.isPeak) {
        drawRoundRect(
            color = accent.copy(alpha = PEAK_GLOW_ALPHA),
            topLeft = Offset(left - width * 0.2f, barTop - size.height * 0.012f),
            size = Size(width * 1.4f, barHeight + size.height * 0.024f),
            cornerRadius = CornerRadius(width * 0.7f),
        )
    }

    drawRoundRect(
        color = accent.copy(alpha = if (slot.isSelected) 1f else BAR_DIMMED_ALPHA),
        topLeft = Offset(left, barTop),
        size = Size(width, barHeight),
        cornerRadius = radius,
    )

    if (slot.isSelected) {
        drawRoundRect(
            color = palette.highlight,
            topLeft = Offset(left + strokePx / 2f, barTop + strokePx / 2f),
            size = Size(width - strokePx, barHeight - strokePx),
            cornerRadius = CornerRadius((width - strokePx) / 2f),
            style = Stroke(width = strokePx),
        )
    }

    if (slot.isCurrentMonth) {
        val dot = size.width * CURRENT_MONTH_DOT
        val lowest = size.height - dot
        val centre = if (lowest > dot) {
            (axis.baseline * size.height).coerceIn(dot, lowest)
        } else {
            size.height / 2f
        }
        drawCircle(
            color = palette.dot,
            radius = dot,
            center = Offset(size.width / 2f, centre),
        )
    }
}
