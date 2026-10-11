package com.srinidhi.kharch.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/*
 * Haptics (plan §7.4).
 *
 * Every haptic goes through here, and every haptic has a settings toggle. When
 * haptics are disabled the call is a no-op rather than a missing call, so screen
 * code never has to branch on the setting.
 */

/** The haptic vocabulary Kharch uses. */
enum class KharchHaptic {
    /** Keypad digit tap. */
    KeyTap,

    /** Save / confirm. */
    Confirm,

    /** Row or chip long-press. */
    LongPress,

    /** Move a text handle, or drag a category chip. */
    Drag,

    /** Swipe-to-delete committed. */
    Selection,

    /** Rejected action. */
    Error,
}

/** Thin wrapper over platform haptic feedback with an on/off switch. */
@Stable
interface HapticsController {
    /** False when the user turned haptics off — every call is then a no-op. */
    val enabled: Boolean

    /** Fires [kind]. Call from a UI callback. */
    fun perform(kind: KharchHaptic)
}

/** Controller that does nothing. Used outside a themed composition and in tests. */
val NoHaptics: HapticsController = object : HapticsController {
    override val enabled: Boolean = false
    override fun perform(kind: KharchHaptic): Unit = Unit
}

/** The active controller. Provided by [ProvideHaptics]. */
val LocalHaptics = staticCompositionLocalOf<HapticsController> { NoHaptics }

/**
 * Builds a [HapticsController] bound to the current [View].
 *
 * @param enabled the user's haptic setting. When false the returned controller
 *   silently ignores every request.
 */
@Composable
fun rememberHapticsController(enabled: Boolean): HapticsController {
    val view = LocalView.current
    return remember(view, enabled) { ViewHaptics(view, enabled) }
}

/**
 * Supplies [LocalHaptics] for the subtree.
 *
 * `KharchTheme` deliberately does NOT do this: haptics are a user preference
 * owned by the settings layer, so the app root wraps the nav host in this.
 */
@Composable
fun ProvideHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val controller = rememberHapticsController(enabled)
    CompositionLocalProvider(LocalHaptics provides controller, content = content)
}

private class ViewHaptics(
    private val view: View,
    override val enabled: Boolean,
) : HapticsController {
    override fun perform(kind: KharchHaptic) {
        if (!enabled) return
        view.performHapticFeedback(constantFor(kind))
    }
}

private fun constantFor(kind: KharchHaptic): Int = when (kind) {
    KharchHaptic.KeyTap -> HapticFeedbackConstants.KEYBOARD_TAP
    KharchHaptic.Confirm -> confirmConstant()
    KharchHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
    KharchHaptic.Drag -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    KharchHaptic.Selection -> HapticFeedbackConstants.VIRTUAL_KEY
    KharchHaptic.Error -> rejectConstant()
}

// API 30 added CONFIRM/REJECT; minSdk is 29, so both degrade to LONG_PRESS.
private fun confirmConstant(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }

private fun rejectConstant(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
