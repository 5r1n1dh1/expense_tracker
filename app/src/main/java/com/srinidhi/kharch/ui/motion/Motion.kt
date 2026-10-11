package com.srinidhi.kharch.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Motion system (plan §7.4).
 *
 * Everything is spring-driven. The ONLY fixed-duration animation in the app is
 * opacity (Motion.Fade) — a spring on opacity overshoots into a visible flash,
 * which is exactly the artefact springs are supposed to avoid.
 */

/** Named spring presets. Use [MotionKind] for non-`Float` animated types. */
enum class MotionKind(val dampingRatio: Float, val stiffness: Float) {
    /** Primary reveals. */
    Emphasized(dampingRatio = 0.8f, stiffness = 380f),

    /** Chips, haptics, small confirmations. */
    Snappy(dampingRatio = 0.9f, stiffness = 700f),

    /** Large surfaces: sheets, full-screen reveals. Critically damped, no wobble. */
    Gentle(dampingRatio = 1.0f, stiffness = 200f),

    /** FAB morph, FABs. Deliberately a little springy. */
    Bouncy(dampingRatio = 0.65f, stiffness = 500f),
    ;

    /** A [SpringSpec] for any animatable type. */
    fun <T> spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

object Motion {
    /** Primary reveals. */
    val Emphasized: SpringSpec<Float> = MotionKind.Emphasized.spec()

    /** Chips and haptics. */
    val Snappy: SpringSpec<Float> = MotionKind.Snappy.spec()

    /** Large surfaces. */
    val Gentle: SpringSpec<Float> = MotionKind.Gentle.spec()

    /** FAB morph. */
    val Bouncy: SpringSpec<Float> = MotionKind.Bouncy.spec()

    /** The only fixed duration in the app: opacity only. */
    const val FadeMs = 180

    /** Opacity fade — safe to tween, unlike everything else. */
    val Fade: FiniteAnimationSpec<Float> = tween(durationMillis = FadeMs)

    /** Chart bars grow with a 40 ms stagger (§7.4). */
    const val ChartStaggerMs = 40L
}

/**
 * True when the user has turned animations off system-wide, or the in-app
 * "reduce motion" setting is on.
 *
 * When true, every spring collapses to an instant state change and shared
 * elements become plain fades (§7.4).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { readAnimatorScale(context) <= 0f }
}

private fun readAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)
