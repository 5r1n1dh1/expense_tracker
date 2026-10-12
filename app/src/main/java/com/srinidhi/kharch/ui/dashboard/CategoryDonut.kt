package com.srinidhi.kharch.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.core.model.Money
import com.srinidhi.kharch.ui.motion.Motion
import com.srinidhi.kharch.ui.motion.rememberReducedMotion
import com.srinidhi.kharch.ui.theme.currentGlassTheme
import kotlin.math.abs

/*
 * The category split (§7.2A): a hollow donut, a sweep-in animation, and a ghosted
 * treatment for categories whose net went below zero.
 *
 * The sweep is driven by "share per mille" exactly as the bar chart's heights are, so the
 * donut never divides money by a float at the call site: a thousandth is a share, and the
 * amount behind it stays a Long count of paise the whole way to the formatter.
 */

/** The unit the slices are sized in. */
private const val SHARE_PERMILLE = 1_000

/** A full turn, in degrees. */
private const val FULL_TURN = 360f

/** Where the sweep starts: twelve o'clock, reading clockwise. */
private const val START_ANGLE = -90f

/** Gap between neighbouring slices, in degrees. */
private const val SLICE_GAP = 1.5f

/** The hole, as a fraction of the donut's radius. Leaves the centre readable. */
private const val HOLE_FRACTION = 0.62f

/** Alpha of the ring drawn when there is nothing to show. */
private const val EMPTY_TRACK_ALPHA = 0.14f

/** Alpha of a ghosted (net-negative) slice's ring. */
private const val GHOST_ALPHA = 0.6f

/** Dash lengths for the ghosted slice treatment, in dp. */
private val GHOST_DASH = 3.dp

/** Donut diameter. The centre holds the year's net total. */
private val DONUT_SIZE: Dp = 200.dp

/** Ring thickness, derived so the hole stays [HOLE_FRACTION] of the diameter. */
private val DONUT_STROKE: Dp = DONUT_SIZE * (1f - HOLE_FRACTION) / 2f

/**
 * One category's slice of the donut.
 *
 * @property categoryId the category this slice came from.
 * @property label the category's display name, resolved by the caller.
 * @property colour the slice colour, resolved from the category's semantic token.
 * @property net the category's signed net; negative when refunds outweighed spend (D5).
 * @property isCredit `true` for a net-negative slice, which is drawn ghosted.
 * @property sharePermille the slice's share of the ring in thousandths of the whole year.
 */
data class DonutSlice(
    val categoryId: String,
    val label: String,
    val colour: Color,
    val net: Money,
    val isCredit: Boolean,
    val sharePermille: Int,
)

/**
 * Shares the year's category nets into donut slices, biggest first.
 *
 * The weight is the **magnitude** of a category's net: a category whose charges and
 * refunds cancel out takes no ring at all, and a category that ended below zero keeps a
 * full-size slice with the ghosted treatment rather than being hidden. Slices are ordered
 * by share descending with the category id as the tiebreak, so two devices holding the
 * same rows (D1) render the same ring.
 *
 * @param categoryNet signed net per category id, as aggregated by the domain layer.
 * @param labels display name per category id. Missing names fall back to [FALLBACK_LABEL].
 * @param colours slice colour per category id, from [categoryColour].
 */
fun donutSlices(
    categoryNet: Map<String, Money>,
    labels: Map<String, String>,
    colours: Map<String, Color>,
): List<DonutSlice> {
    val total = categoryNet.values.sumOf { money -> abs(money.paise) }
    if (total == 0L) return emptyList()
    return categoryNet.entries
        .map { (id, net) ->
            DonutSlice(
                categoryId = id,
                label = labels[id] ?: UNCATEGORISED_LABEL,
                colour = colours[id] ?: categoryColour(id),
                net = net,
                isCredit = net.isCredit,
                sharePermille = ((abs(net.paise) * SHARE_PERMILLE) / total).toInt(),
            )
        }
        .sortedWith(compareByDescending<DonutSlice> { it.sharePermille }.thenBy { it.categoryId })
}

/** Shown for a category whose row is gone but whose history is not (D4). */
internal const val UNCATEGORISED_LABEL: String = "Uncategorised"

/**
 * The donut.
 *
 * @param slices the year's slices, from [donutSlices]. An empty list still draws a
 *   readable empty ring, so the card never collapses.
 * @param modifier layout only.
 * @param centreContent drawn inside the hole — the year's net total lives there.
 */
@Composable
fun CategoryDonut(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centreContent: (@Composable () -> Unit)? = null,
) {
    val glass = currentGlassTheme()
    val reducedMotion = rememberReducedMotion()
    val sweep = remember(reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(slices, reducedMotion) {
        when {
            reducedMotion -> sweep.snapTo(1f)
            slices.isEmpty() -> sweep.snapTo(1f)
            else -> sweep.animateTo(targetValue = 1f, animationSpec = Motion.Emphasized)
        }
    }
    val trackColor = glass.colors.onSecondary
    val ghostColor = glass.colors.credit
    val strokePx = with(LocalDensity.current) { DONUT_STROKE.toPx() }
    val dashPx = with(LocalDensity.current) { GHOST_DASH.toPx() }

    Box(modifier = modifier.size(DONUT_SIZE), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRing(
                slices = slices,
                progress = sweep.value,
                strokePx = strokePx,
                trackColor = trackColor,
                ghostColor = ghostColor,
                dashPx = dashPx,
            )
        }
        if (centreContent != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                centreContent()
            }
        }
    }
}

/** Draws the ring: the empty track when there are no slices, otherwise the slices. */
private fun DrawScope.drawRing(
    slices: List<DonutSlice>,
    progress: Float,
    strokePx: Float,
    trackColor: Color,
    ghostColor: Color,
    dashPx: Float,
) {
    val diameter = minOf(size.width, size.height) - strokePx
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)
    val plain = Stroke(width = strokePx)
    val ghosted = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx), 0f),
    )

    if (slices.isEmpty()) {
        drawArc(
            color = trackColor.copy(alpha = EMPTY_TRACK_ALPHA),
            startAngle = 0f,
            sweepAngle = FULL_TURN,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = plain,
        )
        return
    }

    val totalShare = slices.sumOf { slice -> slice.sharePermille }.coerceAtLeast(1)
    var cursor = START_ANGLE
    slices.forEach { slice ->
        val full = slice.sharePermille.toFloat() / totalShare * FULL_TURN
        val visible = full * progress
        if (visible > SLICE_GAP) {
            drawArc(
                color = if (slice.isCredit) ghostColor else slice.colour,
                startAngle = cursor + SLICE_GAP / 2f,
                sweepAngle = visible - SLICE_GAP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = if (slice.isCredit) GHOST_ALPHA else 1f,
                style = if (slice.isCredit) ghosted else plain,
            )
        }
        cursor += visible
    }
}
