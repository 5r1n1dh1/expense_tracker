package com.srinidhi.kharch.ui.theme

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.srinidhi.kharch.ui.glass.GlassIntensity
import com.srinidhi.kharch.ui.glass.LocalFrostedBackdrop
import com.srinidhi.kharch.ui.glass.LocalGlassIntensity
import com.srinidhi.kharch.ui.glass.rememberFrostedBackdrop

/*
 * Kharch's theme root (plan §8.5, decision D8).
 *
 * Light and dark are both first-class. The Material color schemes below are a
 * convenience layer for ordinary Material components; the glass rendering is
 * driven entirely by [GlassTheme], which is what the contrast test pins.
 */

/** Brand Material 3 scheme, dark. Used when dynamic colour is off or unavailable. */
val KharchDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkAccentFill,
    onPrimary = DarkOnAccentFill,
    primaryContainer = Color(0xFF2E3270),
    onPrimaryContainer = Color(0xFFE3E4FF),
    secondary = DarkExpenseAccentAlt,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFF7DFBE8),
    tertiary = DarkCredit,
    onTertiary = Color(0xFF00382A),
    background = DarkGlassTint,
    onBackground = DarkTextPrimary,
    surface = DarkGlassTint,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF8D93A8),
    outlineVariant = Color(0xFF434859),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkGlassTint,
    inversePrimary = LightAccentFill,
    scrim = ScrimBlack,
)

/** Brand Material 3 scheme, light. */
val KharchLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightAccentFill,
    onPrimary = LightOnAccentFill,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = LightExpenseAccentAlt,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA7F2E4),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = LightCredit,
    onTertiary = Color(0xFFFFFFFF),
    background = LightPageBackground,
    onBackground = LightTextPrimary,
    surface = LightGlassTint,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFF757A8C),
    outlineVariant = Color(0xFFC3C6D4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2B303E),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = DarkAccentFill,
    scrim = ScrimBlack,
)

/** The resolved glass theme. Provided by [KharchTheme]. */
val LocalGlassTheme: ProvidableCompositionLocal<GlassTheme> = compositionLocalOf {
    glassTheme(dark = true)
}

/** True when high-contrast mode is active (user setting OR system `high_text_contrast`). */
val LocalHighContrast: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * The app theme.
 *
 * @param darkTheme dark scheme. Defaults to the system setting.
 * @param dynamicColor use Material You (API 31+) when the platform supports it;
 *   `false` pins the brand gradient (§8.5).
 * @param glassIntensity the user's glass strength (D10: Exaggerated).
 * @param highContrast §8.4.6. Auto-enabled when the system reports
 *   `high_text_contrast`, even if this stays `false`.
 * @param content the whole screen. Reads [LocalGlassTheme], [LocalGlassIntensity],
 *   [LocalHighContrast] and [LocalFrostedBackdrop].
 */
@Composable
fun KharchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    glassIntensity: GlassIntensity = GlassIntensity.Default,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = darkTheme
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = remember(dark, supportsDynamic, context) {
        when {
            supportsDynamic && dark -> dynamicDarkColorScheme(context)
            supportsDynamic -> dynamicLightColorScheme(context)
            dark -> KharchDarkColorScheme
            else -> KharchLightColorScheme
        }
    }

    val systemHighContrast = rememberHighTextContrast(context)
    val glass = remember(dark, highContrast, systemHighContrast, glassIntensity, colorScheme) {
        // Material You drives the accent hue on API 31+ in both schemes (§8.5);
        // otherwise the glass keeps the brand accent.
        val accent = if (supportsDynamic) colorScheme.primary else null
        val onAccent = if (supportsDynamic) colorScheme.onPrimary else null
        glassTheme(
            dark = dark,
            highContrast = highContrast || systemHighContrast,
            intensity = glassIntensity,
            accentFill = accent,
            onAccentFill = onAccent,
        )
    }

    // Built off the main thread inside rememberFrostedBackdrop; null until it
    // lands, at which point every GlassSurface picks it up automatically.
    val backdrop = rememberFrostedBackdrop()

    CompositionLocalProvider(
        LocalGlassTheme provides glass,
        LocalGlassIntensity provides glassIntensity,
        LocalHighContrast provides glass.highContrast,
        LocalFrostedBackdrop provides backdrop,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KharchTypography,
            shapes = KharchShapes,
            content = content,
        )
    }
}

/** The active glass theme. */
@Composable
fun currentGlassTheme(): GlassTheme = LocalGlassTheme.current

/**
 * Resolved tokens for [tier].
 *
 * Text colour always comes from `currentGlassTheme().colors`, not from here —
 * per-tier text colour does not exist, because legibility is guaranteed by the
 * scrim instead (§8.4.1).
 *
 * @param intensity override for this surface only. Normally leave it at the
 *   default, which is whatever `KharchTheme` was given.
 */
@Composable
fun glassTokens(
    tier: GlassTier = GlassTier.Exaggerated,
    intensity: GlassIntensity = LocalGlassIntensity.current,
): GlassTokens = LocalGlassTheme.current.tokens(tier, intensity)

/**
 * The platform's "high text contrast" accessibility switch.
 *
 * Public since API 34 only; below that the user setting is the only source.
 */
@Composable
fun rememberHighTextContrast(context: Context = LocalContext.current): Boolean =
    remember(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager?.isHighContrastTextEnabled == true
        } else {
            false
        }
    }

