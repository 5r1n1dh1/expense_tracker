package com.srinidhi.kharch.ui.glass

import androidx.compose.ui.graphics.toArgb
import com.srinidhi.kharch.ui.theme.DarkBackdropEnd
import com.srinidhi.kharch.ui.theme.DarkBackdropStart
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.GlassTokens
import com.srinidhi.kharch.ui.theme.HighContrastStrokeAlpha
import com.srinidhi.kharch.ui.theme.HighContrastTintAlpha
import com.srinidhi.kharch.ui.theme.LightBackdropEnd
import com.srinidhi.kharch.ui.theme.LightBackdropStart
import com.srinidhi.kharch.ui.theme.glassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The single most important test in the repo (plan §8.4.2).
 *
 * Legibility is enforced structurally, not by eye. This composites EVERY
 * (tier x scheme x high-contrast x intensity) combination over BOTH a light and
 * a dark worst-case backdrop, then asserts:
 *
 *   - headlines and large numbers  >= 7:1
 *   - body text                    >= 4.5:1
 *
 * A failing contrast assertion fails CI. Nothing here duplicates a token value:
 * every colour is read straight out of the production tokens in
 * `ui/theme/GlassTokens.kt`, so editing a token changes what is tested.
 */
class GlassContrastTest {

    // ── The maths itself, proven first ──────────────────────────────────────

    @Test
    fun `contrast ratio of white on black is exactly twenty one`() {
        assertEquals(21.0, contrastRatio(WHITE, BLACK), EPSILON)
        assertEquals(21.0, contrastRatio(BLACK, WHITE), EPSILON)
    }

    @Test
    fun `contrast ratio of a colour on itself is exactly one`() {
        assertEquals(1.0, contrastRatio(MID_GREY, MID_GREY), EPSILON)
        // 0xFF123456 exceeds Int range, so it has to be narrowed explicitly.
        val teal = 0xFF123456.toInt()
        assertEquals(1.0, contrastRatio(teal, teal), EPSILON)
    }

    @Test
    fun `compositing an opaque colour replaces the backdrop`() {
        assertEquals(MID_GREY.toLong(), compositeOver(BLACK, MID_GREY).toLong())
        assertEquals(MID_GREY.toLong(), compositeOver(WHITE, MID_GREY).toLong())
    }

    @Test
    fun `compositing nothing leaves the backdrop untouched`() {
        assertEquals(BLACK.toLong(), compositeOver(BLACK, TRANSPARENT).toLong())
        assertEquals(WHITE.toLong(), compositeOver(WHITE, TRANSPARENT).toLong())
    }

    // ── Headline / large-number contrast, both schemes ──────────────────────

    @Test
    fun `dark scheme keeps headline text at seven to one or better`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(true, false, intensity, TextRole.Headline, HEADLINE_MIN)
        }
    }

    @Test
    fun `light scheme keeps headline text at seven to one or better`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(false, false, intensity, TextRole.Headline, HEADLINE_MIN)
        }
    }

    // ── Body-text contrast, both schemes ────────────────────────────────────

    @Test
    fun `dark scheme keeps body text at four and a half to one or better`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(true, false, intensity, TextRole.Body, BODY_MIN)
        }
    }

    @Test
    fun `light scheme keeps body text at four and a half to one or better`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(false, false, intensity, TextRole.Body, BODY_MIN)
        }
    }

    // ── High-contrast mode (§8.4.6) ─────────────────────────────────────────

    @Test
    fun `high contrast keeps the dark scheme legible`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(true, true, intensity, TextRole.Headline, HEADLINE_MIN)
            assertEveryTier(true, true, intensity, TextRole.Body, BODY_MIN)
        }
    }

    @Test
    fun `high contrast keeps the light scheme legible`() {
        GlassIntensity.settingsValues.forEach { intensity ->
            assertEveryTier(false, true, intensity, TextRole.Headline, HEADLINE_MIN)
            assertEveryTier(false, true, intensity, TextRole.Body, BODY_MIN)
        }
    }

    @Test
    fun `high contrast raises every tier to the documented surface floor`() {
        listOf(true, false).forEach { dark ->
            val theme = glassTheme(dark = dark, highContrast = true)
            GlassTier.entries.forEach { tier ->
                val label = "${schemeName(dark)} / ${tier.name}"
                assertTrue(
                    "$label surface alpha ${theme.tokens(tier).tintAlpha} is below $HighContrastTintAlpha",
                    theme.tokens(tier).tintAlpha >= HighContrastTintAlpha,
                )
                assertFloatEquals(
                    label = "$label stroke alpha",
                    expected = HighContrastStrokeAlpha,
                    actual = theme.tokens(tier).strokeAlpha,
                )
            }
        }
    }

    @Test
    fun `high contrast makes the legibility scrim near-opaque`() {
        listOf(true, false).forEach { dark ->
            val theme = glassTheme(dark = dark, highContrast = true)
            GlassTier.entries.forEach { tier ->
                val scrim = theme.tokens(tier).scrim
                assertTrue("${schemeName(dark)} / ${tier.name} has no scrim", scrim != null)
                assertTrue(
                    "${schemeName(dark)} / ${tier.name} scrim alpha ${scrim?.alpha} is not near-opaque",
                    scrim != null && scrim.alpha >= HIGH_CONTRAST_SCRIM_MIN,
                )
            }
        }
    }

    // ── Structural guarantees, so a token edit cannot quietly bypass the test ─

    @Test
    fun `every tier carries a legibility scrim so no text sits on a bare tint`() {
        listOf(true, false).forEach { dark ->
            GlassIntensity.settingsValues.forEach { intensity ->
                val theme = glassTheme(dark = dark, intensity = intensity)
                assertTrue(theme.hasScrim)
                GlassTier.entries.forEach { tier ->
                    assertTrue(
                        "${schemeName(dark)} / ${tier.name} / ${intensity.name} has no scrim",
                        theme.tokens(tier).scrim != null,
                    )
                }
            }
        }
    }

    @Test
    fun `the default tier survives a worst case backdrop even without the scrim`() {
        listOf(true, false).forEach { dark ->
            val theme = glassTheme(dark = dark, intensity = GlassIntensity.Normal)
            val tokens = theme.tokens(GlassTier.Exaggerated)
            WORST_CASE_BACKDROPS.forEach { (name, backdrop) ->
                val surface = compositeOver(backdrop, tokens.effectiveTint.toArgb())
                val label = "${schemeName(dark)} / $name / no scrim"
                assertRatio("$label headline", HEADLINE_MIN, contrastRatio(theme.colors.onPrimary.toArgb(), surface))
                assertRatio("$label body", BODY_MIN, contrastRatio(theme.colors.onSecondary.toArgb(), surface))
            }
        }
    }

    @Test
    fun `the primary button label is legible on the accent fill in both schemes`() {
        listOf(true, false).forEach { dark ->
            val colors = glassTheme(dark = dark).colors
            assertRatio(
                label = "${schemeName(dark)} button label on the accent fill",
                minRatio = BODY_MIN,
                actual = contrastRatio(colors.onAccentFill.toArgb(), colors.accentFill.toArgb()),
            )
        }
    }

    @Test
    fun `the credit accent is distinguishable from every surface it can sit on`() {
        // §8.4.7: colour is decorative for deltas — a refund also carries a label
        // and a "refund" word — so 3:1 is the bar here, not the 4.5:1 body floor.
        listOf(true, false).forEach { dark ->
            val theme = glassTheme(dark = dark)
            val credit = theme.colors.credit.toArgb()
            val tokens = theme.tokens(GlassTier.Exaggerated)
            WORST_CASE_BACKDROPS.forEach { (name, backdrop) ->
                assertRatio(
                    label = "${schemeName(dark)} credit accent on $name",
                    minRatio = CREDIT_MIN,
                    actual = contrastRatio(credit, surfaceBehindText(tokens, backdrop)),
                )
            }
        }
    }

    @Test
    fun `exaggerated glass is the default tier and the default intensity`() {
        assertEquals(GlassTier.Exaggerated, GlassTier.Default)
        assertEquals(GlassIntensity.Exaggerated, GlassIntensity.Default)
        assertEquals(GlassIntensity.Exaggerated, glassTheme(dark = true).intensity)
    }

    @Test
    fun `tier alphas stay at the tuned values the plan pins`() {
        // Pins §8.3. Dialling the frost back because it looks "glossier" in a
        // screenshot is exactly the regression this file exists to catch, so the
        // constants are asserted rather than trusted.
        assertFloatEquals("Subtle tint", 0.50f, GlassTier.Subtle.tintAlpha)
        assertFloatEquals("Regular tint", 0.66f, GlassTier.Regular.tintAlpha)
        assertFloatEquals("Exaggerated tint", 0.82f, GlassTier.Exaggerated.tintAlpha)
        assertFloatEquals("Inset tint", 0.36f, GlassTier.Inset.tintAlpha)
        assertTrue("Inset must not draw the frosted backdrop", !GlassTier.Inset.drawsBackdrop)
        GlassTier.entries.forEach { tier ->
            assertTrue("${tier.name} surface alpha is below the floor", tier.tintAlpha >= 0.36f)
        }
    }

    @Test
    fun `the frosted backdrop stays inside its build budget`() {
        val width = Frost.targetWidth(1080)
        val height = Frost.targetHeight(2400)
        assertEquals(68L, width.toLong())
        assertEquals(150L, height.toLong())
        assertTrue(
            "backdrop is too large to be free: ${Frost.estimatedBytes(width, height)} bytes",
            Frost.estimatedBytes(width, height) <= Frost.BudgetBytes,
        )
    }

    // ── Harness ─────────────────────────────────────────────────────────────

    /** Which text colour a contrast floor applies to. */
    private enum class TextRole(val label: String) {
        Headline("headline"),
        Body("body"),
    }

    /** Asserts every tier, every intensity and every worst-case backdrop at once. */
    private fun assertEveryTier(
        dark: Boolean,
        highContrast: Boolean,
        intensity: GlassIntensity,
        role: TextRole,
        minRatio: Double,
    ) {
        val theme = glassTheme(dark = dark, highContrast = highContrast, intensity = intensity)
        val text = when (role) {
            TextRole.Headline -> theme.colors.onPrimary
            TextRole.Body -> theme.colors.onSecondary
        }
        GlassTier.entries.forEach { tier ->
            val tokens = theme.tokens(tier, intensity)
            WORST_CASE_BACKDROPS.forEach { (name, backdrop) ->
                val label = "${schemeName(dark)} / ${tier.name} / ${intensity.name} / $name"
                assertRatio(
                    label = "$label ${role.label}",
                    minRatio = minRatio,
                    actual = contrastRatio(text.toArgb(), surfaceBehindText(tokens, backdrop)),
                )
            }
        }
    }

    private fun assertRatio(label: String, minRatio: Double, actual: Double) {
        assertTrue(
            "$label: ${fmt(actual)}:1 is below the ${fmt(minRatio)}:1 floor",
            actual >= minRatio,
        )
    }

    private fun assertFloatEquals(label: String, expected: Float, actual: Float) {
        assertTrue("$label: expected $expected but was $actual", abs(expected - actual) < EPSILON_F)
    }

    private fun schemeName(dark: Boolean): String = if (dark) "dark" else "light"
}

// ── What the user actually sees under a run of text ──────────────────────────

/**
 * The colour behind a run of text on [tokens] for a given [backdrop].
 *
 * backdrop -> tier tint -> legibility scrim. This is what §8.4.1 means by
 * "never text directly on the backdrop", and it is deliberately conservative:
 * the scrim's declared alpha is the MAXIMUM of the rendered vertical ramp, and
 * the tint's alpha is exactly the one Compose composites with.
 */
private fun surfaceBehindText(tokens: GlassTokens, backdrop: Int): Int =
    compositeOver(compositeOver(backdrop, tokens.effectiveTint.toArgb()), tokens.scrim?.toArgb() ?: TRANSPARENT)

// ── Colour maths (WCAG 2.x relative luminance) ────────────────────────────────

private const val HEADLINE_MIN = 7.0
private const val BODY_MIN = 4.5
private const val CREDIT_MIN = 3.0

/** §8.4.6: in high-contrast mode the scrim becomes near-opaque. */
private const val HIGH_CONTRAST_SCRIM_MIN = 0.85f
private const val EPSILON = 1e-4
private const val EPSILON_F = 1e-4f

// `val`, not `const val`: an Int literal this large needs `.toInt()`, and a call
// is not a compile-time constant.
private val WHITE: Int = 0xFFFFFFFF.toInt()
private val BLACK: Int = 0xFF000000.toInt()
private val MID_GREY: Int = 0xFF808080.toInt()
private const val TRANSPARENT = 0x00000000

/**
 * Worst-case backdrops (§8.4.1). Pure white and pure black bracket anything a
 * wallpaper or a gradient can produce; the brand endpoints are the real ones,
 * read from the production palette rather than re-typed here.
 */
private val WORST_CASE_BACKDROPS: List<Pair<String, Int>> = listOf(
    "pure white wallpaper" to WHITE,
    "pure black wallpaper" to BLACK,
    "brand backdrop start (dark)" to DarkBackdropStart.toArgb(),
    "brand backdrop end (dark)" to DarkBackdropEnd.toArgb(),
    "brand backdrop start (light)" to LightBackdropStart.toArgb(),
    "brand backdrop end (light)" to LightBackdropEnd.toArgb(),
)

private fun channel(argb: Int, shift: Int): Double = ((argb shr shift) and 0xFF).toDouble()

private fun linearise(value: Double): Double =
    if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

/** WCAG relative luminance of an opaque sRGB ARGB integer. */
private fun relativeLuminance(argb: Int): Double =
    0.2126 * linearise(channel(argb, 16) / 255.0) +
        0.7152 * linearise(channel(argb, 8) / 255.0) +
        0.0722 * linearise(channel(argb, 0) / 255.0)

/** WCAG contrast ratio between two sRGB ARGB integers. */
private fun contrastRatio(foreground: Int, background: Int): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Straight (non-premultiplied) source-over compositing, the way the GPU blends. */
private fun compositeOver(base: Int, over: Int): Int {
    val alpha = channel(over, 24) / 255.0
    if (alpha <= 0.0) return base
    fun blend(shift: Int): Int = (
        channel(over, shift) * alpha + channel(base, shift) * (1.0 - alpha)
        ).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
}

private fun fmt(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
