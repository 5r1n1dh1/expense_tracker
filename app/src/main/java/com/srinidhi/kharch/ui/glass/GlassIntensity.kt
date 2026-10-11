package com.srinidhi.kharch.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The user's glass-intensity setting (§8.3).
 *
 * This is a *preference* layered on top of the fixed tiers: it scales the
 * surface alpha and the design sigma of every `GlassTier`. There is deliberately
 * nothing above [Exaggerated] — past that point legibility degrades faster than
 * the glass effect improves (D10).
 *
 * Softening the frost is compensated by a slightly stronger legibility scrim
 * (see `ui/theme/GlassTokens.kt`), so lowering the intensity can never push text
 * below the contrast floor asserted by `GlassContrastTest`.
 */
enum class GlassIntensity(
    val scale: Float,
    val sigmaScale: Float,
) {
    /** Thin frost, most of the wallpaper reads through. */
    Subtle(scale = 0.86f, sigmaScale = 0.70f),

    /** The tuned baseline. */
    Normal(scale = 1.00f, sigmaScale = 1.00f),

    /** DEFAULT (D10) — identical rendering to [Normal]; it is the baseline, not a boost. */
    Exaggerated(scale = 1.00f, sigmaScale = 1.00f),
    ;

    companion object {
        /** Default intensity (D10). */
        val Default: GlassIntensity = Exaggerated

        /** Every value, in settings-menu order. */
        val settingsValues: List<GlassIntensity> = listOf(Subtle, Normal, Exaggerated)

        /** Resolves a persisted name, falling back to [Default] for unknown values. */
        fun fromName(name: String?): GlassIntensity =
            settingsValues.firstOrNull { it.name == name } ?: Default
    }
}

/** Stable key for persistence (DataStore / preferences). */
val GlassIntensity.storageKey: String get() = name

/**
 * Current user intensity. Provided by `KharchTheme`; override it in a preview or
 * a test to pin the rendering.
 */
val LocalGlassIntensity = staticCompositionLocalOf { GlassIntensity.Default }
