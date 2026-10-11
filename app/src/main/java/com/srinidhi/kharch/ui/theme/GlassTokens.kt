package com.srinidhi.kharch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.srinidhi.kharch.ui.glass.GlassIntensity

/*
 * The glass token system (plan §8).
 *
 * A GlassTokens value is the complete, self-contained description of ONE glass
 * tier in ONE scheme. Nothing here reads the environment at draw time, which is
 * what makes the whole system contrast-testable in a plain JVM unit test
 * (GlassContrastTest reads these production values directly).
 *
 * Tier values are the tuned constants from §8.3. They are deliberately
 * aggressive: heavy frost is the product differentiator (D10), and legibility is
 * *verified* rather than eyeballed — see GlassContrastTest.
 */

/** Maximum surface alpha, even when the user cranks intensity up (§8.3). */
const val MaxTintAlpha = 0.94f

/** High-contrast surface alpha (§8.4.6): every tier jumps to at least this. */
const val HighContrastTintAlpha = 0.85f

/** High-contrast inner-stroke alpha (§8.4.6). */
const val HighContrastStrokeAlpha = 0.35f

/** Never let a scrim become fully opaque — the surface must still read as glass. */
const val MaxScrimAlpha = 0.96f

/**
 * How much scrim alpha is added per unit of "missing" intensity. Softening the
 * frost (GlassIntensity.Subtle) makes the panel more see-through, so the scrim
 * compensates and legibility never regresses with the user's intensity setting.
 */
const val ScrimIntensityCompensation = 0.5f

/** Fraction of the surface height the sheen sweep occupies (§8.4.4: top ~30 %). */
const val SheenHeightFractionDark = 0.42f

/** Narrower sweep in light mode, which needs a stronger highlight to survive white (§8.5). */
const val SheenHeightFractionLight = 0.30f

/**
 * The four glass tiers (§8.3). [Exaggerated] is the default and the ceiling:
 * past this point legibility degrades faster than the glass effect improves.
 */
enum class GlassTier(val blurSigma: Float, val tintAlpha: Float, val strokeAlpha: Float) {
    /** User-selectable. */
    Subtle(2.5f, 0.50f, 0.12f),

    /** User-selectable. */
    Regular(3.5f, 0.66f, 0.16f),

    /** DEFAULT (D10) — heavy frost, unmistakably glass. */
    Exaggerated(5.0f, 0.82f, 0.22f),

    /** Inputs only: no backdrop image, needs the strongest scrim. */
    Inset(0.0f, 0.36f, 0.10f),
    ;

    /** `Inset` sits on a sheet; every other tier reads the frosted backdrop. */
    val drawsBackdrop: Boolean get() = this != Inset

    /** The tier used when a caller does not care. */
    companion object {
        /** Default tier (D10). */
        val Default: GlassTier = Exaggerated
    }
}

/**
 * One glass tier, fully resolved for one scheme at one intensity and one
 * high-contrast setting.
 *
 * - [tint] at [tintAlpha] is the translucent body of the panel.
 * - [innerStroke] at [strokeAlpha] is the 1 dp rim that replaces elevation; the
 *   drawing code ramps it from bright (top-left) to faint (bottom-right).
 * - [sheen] is the diagonal highlight that sells "glass", masked by
 *   [sheenFraction] to the top of the panel so it never crosses text (§8.4.4).
 * - [scrim] is the legibility lift applied *under* content. Its alpha is the
 *   MAXIMUM in the rendered gradient, so contrast tests that use it directly
 *   are conservative rather than optimistic.
 *
 * @property blurSigma design-space sigma for this tier. The rendered frost always
 *   comes from the single pre-baked backdrop (§8.1); the tier is realised
 *   through tint and scrim alpha, never through a per-surface blur.
 */
@Immutable
data class GlassTokens(
    val tier: GlassTier,
    val blurSigma: Float,
    val tint: Color,
    val tintAlpha: Float,
    val innerStroke: Color,
    val strokeAlpha: Float,
    val sheenColors: List<Color>,
    val sheenFraction: Float,
    val scrim: Color?,
) {
    /** Translucent panel body colour, alpha already applied. */
    val effectiveTint: Color get() = tint.copy(alpha = tintAlpha.coerceIn(0f, 1f))

    /** 1 dp rim colour, alpha already applied. */
    val effectiveInnerStroke: Color get() = innerStroke.copy(alpha = strokeAlpha.coerceIn(0f, 1f))

    /** Vertical legibility ramp. The declared [scrim] alpha is the gradient's maximum. */
    val scrimBrush: Brush by lazy {
        val base = scrim
        if (base == null) {
            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                0.00f to base.copy(alpha = base.alpha * ScrimTopFalloff),
                0.55f to base,
                1.00f to base.copy(alpha = base.alpha * ScrimBottomFalloff),
            )
        }
    }

    /**
     * Diagonal sheen gradient, built once per token instance.
     *
     * `end` is left infinite so the shader resolves to the *drawn band*, not to
     * a couple of pixels: callers draw this into a `Size(width, height *
     * sheenFraction)` rect, so the sweep follows the panel's diagonal exactly.
     *
     * Deliberately lazy: the tokens themselves stay a pure colour/float record so
     * the JVM contrast test can read them without loading any graphics classes.
     */
    val sheen: Brush by lazy { Brush.linearGradient(colors = sheenColors) }
}

/** Top of the scrim ramp, as a fraction of the scrim's declared alpha. */
private const val ScrimTopFalloff = 0.82f

/** Bottom of the scrim ramp, as a fraction of the scrim's declared alpha. */
private const val ScrimBottomFalloff = 0.90f

/**
 * The scheme-level colours every glass surface shares. These are NOT per tier:
 * text colour does not change with how frosted the panel is, only the scrim does.
 */
@Immutable
data class GlassColors(
    val onPrimary: Color,
    val onSecondary: Color,
    val accentFill: Color,
    val onAccentFill: Color,
    val credit: Color,
    val expenseAccent: Color,
    val expenseAccentAlt: Color,
    val backdropStart: Color,
    val backdropEnd: Color,
    val backdropOverlay: Color,
) {
    /** True when this is the dark scheme. */
    val dark: Boolean get() = backdropStart == DarkBackdropStart
}

/**
 * Everything a composable needs to draw glass: the resolved token set, the
 * semantic colours, and the user's intensity / high-contrast switches.
 *
 * Tokens are pre-resolved for *every* intensity so a single surface can override
 * the strength without leaving the theme or re-deriving anything (§8.3).
 */
@Immutable
class GlassTheme(
    val colors: GlassColors,
    val intensity: GlassIntensity,
    val highContrast: Boolean,
    private val byIntensity: Map<GlassIntensity, List<GlassTokens>>,
) {
    /** True when this is the dark scheme. */
    val dark: Boolean get() = colors.dark

    /** Tokens for [tier] at the user's current intensity. */
    fun tokens(tier: GlassTier, intensity: GlassIntensity = this.intensity): GlassTokens =
        byIntensity.getValue(intensity)[tier.ordinal]

    /** Tokens for the default tier ([GlassTier.Exaggerated]) at the current intensity. */
    val defaultTokens: GlassTokens get() = tokens(GlassTier.Exaggerated)

    /** True when every tier carries a scrim — i.e. text is always contrast-checked. */
    val hasScrim: Boolean get() = byIntensity.getValue(intensity).all { it.scrim != null }
}

// ── Resolution ───────────────────────────────────────────────────────────────

/** Per-tier default scrim alpha, dark scheme. Frost tier ↔ legibility trade-off. */
private val DarkScrimAlpha: Map<GlassTier, Float> = mapOf(
    GlassTier.Subtle to 0.55f,
    GlassTier.Regular to 0.45f,
    GlassTier.Exaggerated to 0.30f,
    GlassTier.Inset to 0.80f,
)

/** Per-tier default scrim alpha, light scheme. */
private val LightScrimAlpha: Map<GlassTier, Float> = mapOf(
    GlassTier.Subtle to 0.52f,
    GlassTier.Regular to 0.34f,
    GlassTier.Exaggerated to 0.22f,
    GlassTier.Inset to 0.74f,
)

/**
 * Peak sheen alpha per tier, dark scheme — broad and low-opacity white (§8.5).
 */
private val DarkSheenAlpha: Map<GlassTier, Float> = mapOf(
    GlassTier.Subtle to 0.10f,
    GlassTier.Regular to 0.12f,
    GlassTier.Exaggerated to 0.14f,
    GlassTier.Inset to 0.05f,
)

/**
 * Peak sheen alpha per tier, light scheme — narrow, and keyed off the DARK
 * inner stroke rather than white.
 *
 * §8.5 asks for a brighter sweep in light mode; on a white panel a white sweep
 * is literally invisible, so the highlight is inverted: a short, tight dark
 * wash at the top-left that reads as the shadowed edge of a glass slab. Same
 * job the bright rim does in dark mode, expressed the only way a white panel
 * can express it.
 */
private val LightSheenAlpha: Map<GlassTier, Float> = mapOf(
    GlassTier.Subtle to 0.70f,
    GlassTier.Regular to 0.78f,
    GlassTier.Exaggerated to 0.85f,
    GlassTier.Inset to 0.45f,
)

/**
 * Builds the complete glass theme. Pure, synchronous and side-effect free so the
 * JVM contrast test can build every combination without a composition.
 *
 * @param dark dark scheme when true, light scheme when false.
 * @param highContrast §8.4.6 — raises surface/stroke alpha and uses the
 *   near-opaque scrim. Visually still glass, far more legible.
 * @param intensity the user's glass intensity (D10 default is Exaggerated).
 * @param accentFill optional Material You primary, overriding the brand accent.
 * @param onAccentFill optional Material You onPrimary, overriding the brand label.
 */
fun glassTheme(
    dark: Boolean,
    highContrast: Boolean = false,
    intensity: GlassIntensity = GlassIntensity.Default,
    accentFill: Color? = null,
    onAccentFill: Color? = null,
): GlassTheme {
    val colors = glassColors(dark = dark, accentFill = accentFill, onAccentFill = onAccentFill)
    val byIntensity = GlassIntensity.settingsValues.associateWith { setting ->
        GlassTier.entries.map { tier ->
            tokensFor(colors = colors, tier = tier, intensity = setting, highContrast = highContrast)
        }
    }
    return GlassTheme(
        colors = colors,
        intensity = intensity,
        highContrast = highContrast,
        byIntensity = byIntensity,
    )
}

/** The semantic colour set for one scheme, optionally tinted by Material You. */
fun glassColors(dark: Boolean, accentFill: Color? = null, onAccentFill: Color? = null): GlassColors =
    if (dark) {
        GlassColors(
            onPrimary = DarkTextPrimary,
            onSecondary = DarkTextSecondary,
            accentFill = accentFill ?: DarkAccentFill,
            onAccentFill = onAccentFill ?: DarkOnAccentFill,
            credit = DarkCredit,
            expenseAccent = DarkExpenseAccent,
            expenseAccentAlt = DarkExpenseAccentAlt,
            backdropStart = DarkBackdropStart,
            backdropEnd = DarkBackdropEnd,
            backdropOverlay = DarkBackdropOverlay,
        )
    } else {
        GlassColors(
            onPrimary = LightTextPrimary,
            onSecondary = LightTextSecondary,
            accentFill = accentFill ?: LightAccentFill,
            onAccentFill = onAccentFill ?: LightOnAccentFill,
            credit = LightCredit,
            expenseAccent = LightExpenseAccent,
            expenseAccentAlt = LightExpenseAccentAlt,
            backdropStart = LightBackdropStart,
            backdropEnd = LightBackdropEnd,
            backdropOverlay = LightBackdropOverlay,
        )
    }

private fun tokensFor(
    colors: GlassColors,
    tier: GlassTier,
    intensity: GlassIntensity,
    highContrast: Boolean,
): GlassTokens {
    val dark = colors.dark
    val scaledAlpha = (tier.tintAlpha * intensity.scale).coerceIn(0f, MaxTintAlpha)
    val tintAlpha = if (highContrast) maxOf(scaledAlpha, HighContrastTintAlpha) else scaledAlpha
    val strokeAlpha = if (highContrast) HighContrastStrokeAlpha else tier.strokeAlpha

    // The scrim compensates for a softened frost, so lowering the intensity
    // setting can never cost legibility.
    val scrimCompensation = (1f - intensity.scale) * ScrimIntensityCompensation
    val scrimAlpha = (baseScrimAlpha(dark, tier) + scrimCompensation).coerceIn(0f, MaxScrimAlpha)
    val scrimColor = if (highContrast) {
        if (dark) DarkScrimHighContrast else LightScrimHighContrast
    } else if (dark) {
        DarkScrim
    } else {
        LightScrim
    }

    val sheenTable = if (dark) DarkSheenAlpha else LightSheenAlpha
    val sheenGain = if (highContrast) 0.6f else 1f
    val sheenPeak = sheenTable.getValue(tier) * sheenGain
    val sheenBase = if (dark) DarkInnerStroke else LightInnerStroke

    // In high-contrast mode the scrim colour keeps its own near-opaque alpha
    // (§8.4.6) — the per-tier ramp above is for the normal schemes only. The
    // tint alpha is pinned to HighContrastTintAlpha regardless of intensity in
    // that mode, so the compensation is not needed here.
    val scrim = if (highContrast) scrimColor else scrimColor.copy(alpha = scrimAlpha)

    return GlassTokens(
        tier = tier,
        blurSigma = tier.blurSigma * intensity.sigmaScale,
        tint = if (dark) DarkGlassTint else LightGlassTint,
        tintAlpha = tintAlpha,
        innerStroke = if (dark) DarkInnerStroke else LightInnerStroke,
        strokeAlpha = strokeAlpha,
        sheenColors = listOf(
            sheenBase.copy(alpha = sheenPeak),
            sheenBase.copy(alpha = sheenPeak * 0.36f),
            sheenBase.copy(alpha = 0f),
        ),
        sheenFraction = if (dark) SheenHeightFractionDark else SheenHeightFractionLight,
        // Every tier carries a scrim: text never sits on a bare tint (§8.4.1).
        scrim = scrim,
    )
}

private fun baseScrimAlpha(dark: Boolean, tier: GlassTier): Float =
    if (dark) DarkScrimAlpha.getValue(tier) else LightScrimAlpha.getValue(tier)
