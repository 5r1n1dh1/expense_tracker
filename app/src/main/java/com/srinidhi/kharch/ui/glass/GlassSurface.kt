package com.srinidhi.kharch.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.GlassTokens
import com.srinidhi.kharch.ui.theme.glassTokens

/*
 * The glass surface (plan §8.3).
 *
 * Draw order, and the reason for it:
 *
 *   backdrop → tint → sheen → scrim → CONTENT → inner stroke
 *
 * The sheen and the scrim are painted *under* the content, not over it. §8.4.4
 * says the sheen must never cross text; painting it before the content is how
 * that is guaranteed structurally rather than by hoping the padding is right.
 *
 * Everything drawn here is a cached bitmap plus a handful of rects — no
 * per-frame RenderEffect, and no live blur of anything scrolling.
 */

/** The 1 dp inner stroke that replaces elevation (§8.3). */
private val StrokeWidth = 1.dp

/** Fraction of the stroke's alpha at the bottom-right end of the rim ramp. */
private const val StrokeFalloff = 0.25f

/**
 * A translucent frosted panel.
 *
 * @param modifier layout and behaviour only. Put padding in [contentPadding] so
 *   the tint, sheen and scrim always cover the full panel.
 * @param tier how frosted. Defaults to [GlassTier.Exaggerated] (D10).
 * @param shape panel shape. Defaults to `MaterialTheme.shapes.extraLarge` (28 dp).
 * @param glassIntensity strength for this surface only. Defaults to
 *   `LocalGlassIntensity`, i.e. the user's setting.
 * @param contentPadding the explicit content inset (§8.4.4). Keeping text clear
 *   of the sheen band starts here.
 * @param backdrop override the frosted image. Defaults to `LocalFrostedBackdrop`;
 *   screens should never pass this.
 * @param content panel content, drawn above the scrim.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.Exaggerated,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    glassIntensity: GlassIntensity = LocalGlassIntensity.current,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backdrop: ImageBitmap? = LocalFrostedBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens: GlassTokens = glassTokens(tier, glassIntensity)
    val strokeWidthPx = with(LocalDensity.current) { StrokeWidth.toPx() }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                if (backdrop != null && tier.drawsBackdrop) {
                    drawImage(image = backdrop, dstSize = size.toIntSize())
                }
                drawRect(color = tokens.effectiveTint)

                val sheenHeight = size.height * tokens.sheenFraction
                if (sheenHeight > 0f) {
                    drawRect(brush = tokens.sheen, size = Size(size.width, sheenHeight))
                }

                if (tokens.scrim != null) {
                    drawRect(brush = tokens.scrimBrush, size = size)
                }

                drawContent()

                val stroke = tokens.effectiveInnerStroke
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(stroke, stroke.copy(alpha = stroke.alpha * StrokeFalloff)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    size = size,
                    style = Stroke(width = strokeWidthPx),
                )
            }
            .padding(contentPadding),
    ) {
        content()
    }
}
