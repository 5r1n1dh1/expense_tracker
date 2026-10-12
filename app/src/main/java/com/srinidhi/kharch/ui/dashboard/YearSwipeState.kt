package com.srinidhi.kharch.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Drag distance past which a horizontal swipe changes the year (§7.3). */
private val SWIPE_THRESHOLD = 56.dp

/**
 * The year-change gesture: one drag, one year, rubber-banded at the ends (§7.3).
 *
 * The state is hoisted out of the chart card because a `LazyColumn` disposes items that
 * scroll out of view — a swipe that began on a card that then scrolled away must still
 * commit. Keeping it here also means the parallax offset survives recomposition and the
 * year sheet can read it.
 *
 * @property visualOffset the damped offset the bars parallax by. Reading it during
 *   composition recomposes the chart every frame of the drag.
 */
@Stable
class YearSwipeState internal constructor(
    private val yearProvider: () -> Int,
    private val thresholdPxProvider: () -> Float,
    private val blockedProvider: (Float) -> Boolean,
    private val onYearChange: (Int) -> Unit,
) {
    /** The undamped drag, in pixels. Only the gesture writes it. */
    var rawOffset by mutableFloatStateOf(0f)
        private set

    /** The rubber-banded offset the render layer moves the bars by. */
    val visualOffset: Float
        get() = BarChartMath.swipeOffset(rawOffset, blockedProvider(rawOffset), thresholdPxProvider())

    /** The drag distance past which a swipe changes the year, in pixels. */
    val thresholdPx: Float
        get() = thresholdPxProvider()

    /** Accumulates a drag delta. Called from the horizontal drag detector. */
    fun drag(delta: Float) {
        rawOffset += delta
    }

    /** Abandons the gesture — a cancelled drag never changes the year. */
    fun reset() {
        rawOffset = 0f
    }

    /**
     * Ends the gesture, stepping one year when past the threshold.
     *
     * Dragging towards the newer years (a negative offset) steps forward; dragging towards
     * the older ones steps back. A drag that never reaches the threshold just springs
     * back, because the raw offset is cleared here rather than animated out.
     */
    fun commit() {
        val raw = rawOffset
        rawOffset = 0f
        if (BarChartMath.shouldStepYear(raw, thresholdPxProvider())) {
            onYearChange(yearProvider() + if (raw < 0f) 1 else -1)
        }
    }
}

/**
 * Remembers the swipe state for [year].
 *
 * @param year the year currently on screen, captured so a committed swipe steps from the
 *   year the user is actually looking at.
 * @param canStepNewer `false` when the viewer is already at the newest tracked year.
 * @param canStepOlder `false` when the viewer is already at the oldest tracked year.
 * @param onYearChange invoked with the year to move to, one step in either direction.
 */
@Composable
fun rememberYearSwipeState(
    year: Int,
    canStepNewer: Boolean,
    canStepOlder: Boolean,
    onYearChange: (Int) -> Unit,
): YearSwipeState {
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    return remember(year, canStepNewer, canStepOlder, thresholdPx) {
        YearSwipeState(
            yearProvider = { year },
            thresholdPxProvider = { thresholdPx },
            blockedProvider = { raw -> if (raw >= 0f) !canStepOlder else !canStepNewer },
            onYearChange = onYearChange,
        )
    }
}
