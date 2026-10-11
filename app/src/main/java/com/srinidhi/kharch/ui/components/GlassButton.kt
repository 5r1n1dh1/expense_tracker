package com.srinidhi.kharch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.LocalHighContrast
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * Buttons on glass. Three variants, one code path:
 *
 *   Primary   — accent-filled slab on Exaggerated glass. The only variant whose
 *               label colour is NOT onPrimary; it is onAccentFill, which the
 *               contrast test pins at >= 4.5:1 in both schemes.
 *   Secondary — Regular glass, onPrimary label.
 *   Ghost     — Inset glass only (no backdrop image), for tertiary actions.
 */

/** The three button treatments. */
enum class GlassButtonVariant {
    /** Accent-filled. The primary call to action. */
    Primary,

    /** Regular glass with an onPrimary label. */
    Secondary,

    /** No fill beyond the inset scrim. Tertiary / destructive-adjacent actions. */
    Ghost,
}

/** Minimum touch target. */
private val MinHeight = 48.dp

/** How much accent the Primary variant lays over its glass. */
private const val AccentFillAlpha = 0.92f

/** Label alpha when disabled. */
private const val DisabledAlpha = 0.38f

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

/**
 * A button rendered on glass.
 *
 * @param text the label. Buttons here are text-first — no icon-only variants,
 *   because an icon without a label fails "never colour alone" in spirit.
 * @param onClick fired on tap, preceded by a [KharchHaptic.Confirm] tick.
 * @param modifier layout only.
 * @param variant see [GlassButtonVariant].
 * @param enabled disabled buttons keep their shape and stay in the tree.
 * @param leadingIcon optional icon, drawn before the label from the curated
 *   vector-drawable set (material-icons-extended is deliberately not a dependency).
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    val highContrast = LocalHighContrast.current
    val isPrimary = variant == GlassButtonVariant.Primary
    val tier = when (variant) {
        GlassButtonVariant.Primary -> GlassTier.Exaggerated
        GlassButtonVariant.Secondary -> GlassTier.Regular
        GlassButtonVariant.Ghost -> GlassTier.Inset
    }
    val labelColor = when {
        !enabled -> glass.colors.onPrimary.copy(alpha = DisabledAlpha)
        isPrimary -> glass.colors.onAccentFill
        else -> glass.colors.onPrimary
    }
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = if (highContrast) FontWeight.SemiBold else FontWeight.Medium,
    )
    val fillModifier = if (isPrimary && enabled) {
        Modifier.background(glass.colors.accentFill.copy(alpha = AccentFillAlpha))
    } else {
        Modifier
    }

    GlassSurface(
        modifier = modifier.heightIn(min = MinHeight),
        tier = tier,
        shape = GlassShape.chip,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(fillModifier)
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.perform(KharchHaptic.Confirm)
                    onClick()
                }
                .padding(ButtonPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = labelStyle,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}
