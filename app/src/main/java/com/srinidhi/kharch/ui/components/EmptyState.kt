package com.srinidhi.kharch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.motion.Motion
import com.srinidhi.kharch.ui.motion.rememberReducedMotion
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The designed empty state (D9, §8.4.9).
 *
 * Parameterised on purpose: the same component has to say
 *   "No expenses here"          → action: Add expense
 *   No results for "chai"       → action: Clear search
 *   "No expenses match filters" → action: Reset filters
 * with a matching action, so headline/subline/action are all caller-supplied.
 * Strings come from the caller's string resources, not from here — that also
 * keeps this file out of the localisation conversation.
 *
 * The orb is a REAL Exaggerated glass surface, not a flat circle. §8.4.9
 * requires the empty state to be covered by the same contrast test as populated
 * content, and that only holds if it is made of the same material.
 */

/** Diameter of the orb. */
private val DEFAULT_ORB_SIZE: Dp = 96.dp

/** Gap between the orb and the headline. */
private val ORB_GAP: Dp = 24.dp

/** Gap between the headline and the subline. */
private val TEXT_GAP: Dp = 8.dp

/** Gap between the subline and the action button. */
private val ACTION_GAP: Dp = 24.dp

/** Scale the orb settles at; it springs in from [ORB_ENTER_SCALE]. */
private const val ORB_REST_SCALE = 1f

/** Scale the orb starts from — with [Motion.Bouncy] it overshoots past 1. */
private const val ORB_ENTER_SCALE = 0.72f

/** Peak alpha of the accent bloom inside the orb. */
private const val ORB_BLOOM_ALPHA = 0.45f

/** The orb's bloom centre, as a fraction of its diameter from the top-left. */
private const val ORB_BLOOM_CENTER = 0.35f

/** The orb's bloom radius, as a fraction of its diameter. */
private const val ORB_BLOOM_RADIUS = 0.9f

/**
 * The empty state.
 *
 * @param headline one short sentence, title case, no trailing full stop.
 * @param subline one sentence saying *why* it is empty and what to do about it.
 *   This is where a quoted query goes: `No results for "chai"`.
 * @param modifier layout only.
 * @param actionLabel label of the recovery action, or null when there is none.
 * @param onAction invoked when the action is tapped. Ignored without
 *   [actionLabel].
 * @param orbSize orb diameter; shrink it in a tight layout.
 * @param orbContent optional content drawn inside the orb on top of the accent
 *   bloom (an icon or a category glyph).
 */
@Composable
fun EmptyState(
    headline: String,
    subline: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    orbSize: Dp = DEFAULT_ORB_SIZE,
    orbContent: (@Composable () -> Unit)? = null,
) {
    val glass = currentGlassTheme()
    val reducedMotion = rememberReducedMotion()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val orbScale by animateFloatAsState(
        targetValue = if (revealed) ORB_REST_SCALE else ORB_ENTER_SCALE,
        animationSpec = if (reducedMotion) snap<Float>() else Motion.Bouncy,
        label = "empty-state-orb-scale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = Motion.Fade,
        label = "empty-state-content-alpha",
    )
    val action = onAction

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            modifier = Modifier
                .size(orbSize)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                    alpha = contentAlpha
                },
            tier = GlassTier.Exaggerated,
            shape = GlassShape.orb,
        ) {
            val bloom = orbBloom(orbSize, glass.colors.accentFill, LocalDensity.current)
            Box(modifier = Modifier.fillMaxSize().background(bloom))
            orbContent?.invoke()
        }

        Spacer(modifier = Modifier.height(ORB_GAP))

        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            color = glass.colors.onPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(TEXT_GAP))

        Text(
            text = subline,
            style = MaterialTheme.typography.bodyMedium,
            color = glass.colors.onSecondary,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && action != null) {
            Spacer(modifier = Modifier.height(ACTION_GAP))
            GlassButton(text = actionLabel, onClick = action)
        }
    }
}

private fun orbBloom(size: Dp, accent: Color, density: Density): Brush {
    // Brush.radialGradient takes raw PIXELS, so the Dp has to be converted first;
    // multiplying a Dp by a Float yields another Dp, which does not compile here.
    val px = with(density) { size.toPx() }
    return Brush.radialGradient(
        colors = listOf(accent.copy(alpha = ORB_BLOOM_ALPHA), Color.Transparent),
        center = Offset(px * ORB_BLOOM_CENTER, px * ORB_BLOOM_CENTER),
        radius = px * ORB_BLOOM_RADIUS,
    )
}
