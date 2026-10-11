package com.srinidhi.kharch.ui.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.srinidhi.kharch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The pre-baked frosted backdrop (plan §8.1 / §8.2).
 *
 * THE ONE RULE: blur is free here because nothing live is ever blurred.
 * A single downsampled bitmap is built once per process and every glass surface
 * reuses it as its backdrop. Content scrolls ABOVE the glass, never behind it,
 * so there is nothing to re-blur per frame.
 *
 * Explicitly NOT used anywhere in this app: live blur on scrolling content,
 * per-frame RenderEffect, or RenderScript. All three are jank sources, and the
 * first is additionally a detekt build failure.
 */

/** Frost build parameters and the cost budget CI holds us to (§8.2). */
object Frost {
    /** 1/16 of the linear resolution (~30x80 px on a 1080p phone). */
    const val SCALE = 0.0625f

    /** Sigma at the *scaled* size → visually ~48 px at full resolution. */
    const val SIGMA = 3.0f

    /** Floor so tiny or odd displays still produce a usable bitmap. */
    const val MinEdge = 16

    /** ARGB_8888 is 4 bytes per pixel. */
    const val BytesPerPixel = 4

    /** Cost budget: the build stays under this (§8.2). */
    const val BudgetMs = 4L

    /** Cost budget: a 1/16-scale ARGB_8888 bitmap stays far below this. */
    const val BudgetBytes = 64L * 1024L

    /** Target width in pixels for a display of [displayWidthPixels]. */
    fun targetWidth(displayWidthPixels: Int): Int =
        (displayWidthPixels * SCALE).roundToInt().coerceAtLeast(MinEdge)

    /** Target height in pixels for a display of [displayHeightPixels]. */
    fun targetHeight(displayHeightPixels: Int): Int =
        (displayHeightPixels * SCALE).roundToInt().coerceAtLeast(MinEdge)

    /** Bytes the baked bitmap will occupy — asserted against [BudgetBytes] in CI. */
    fun estimatedBytes(width: Int, height: Int): Int = width * height * BytesPerPixel
}

/** Where the frosted backdrop's source pixels come from (§8.2). */
enum class FrostSource {
    /** System wallpaper when the platform exposes one, else the brand gradient. */
    Auto,

    /** System wallpaper only; degrades to `null` when unavailable (no gradient fallback). */
    Wallpaper,

    /** The bundled brand gradient drawable. Always available. */
    BrandGradient,
}

/**
 * The process-wide backdrop, shared by every glass surface via
 * [LocalFrostedBackdrop]. `null` until the build lands, and permanently `null`
 * if the build fails — callers fall back to the brand gradient.
 */
val LocalFrostedBackdrop = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * Builds (once) and returns the downsampled, pre-blurred backdrop, or `null` if
 * it cannot be built on this device.
 *
 * The work happens on [Dispatchers.Default], so it is off the first-frame
 * critical path: callers render the brand gradient until the bitmap lands. The
 * result is cached process-wide, so N glass surfaces cost exactly one build.
 */
@Composable
fun rememberFrostedBackdrop(source: FrostSource = FrostSource.Auto): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    val layout = Triple(source, configuration.screenWidthDp, configuration.screenHeightDp)
    val cached = FrostCache.peek(context, layout.first)
    val backdrop by produceState<ImageBitmap?>(initialValue = cached, key1 = context, key2 = layout) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                FrostCache.get(context, layout.first)
            }
        }
    }
    return backdrop
}

/**
 * Layer 0 + 1 of the app shell: the static brand gradient with the frosted
 * backdrop composited on top, then content.
 *
 * The gradient is always drawn, never conditionally: when the frosted bitmap is
 * still building (or failed to build) this composable *is* the fallback, which is
 * exactly what §8.1 asks for.
 *
 * @param overlay drawn above the image. Pass
 *   `LocalGlassTheme.current.colors.backdropOverlay` to reach the §8.5 backdrop
 *   (55 % dim in dark, 25 % lift in light).
 */
@Composable
fun FrostedBackdrop(
    modifier: Modifier = Modifier,
    source: FrostSource = FrostSource.Auto,
    overlay: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val backdrop = rememberFrostedBackdrop(source)
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.brand_gradient),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (overlay != Color.Transparent) {
            Box(modifier = Modifier.matchParentSize().background(overlay))
        }
        content()
    }
}

// ── Build (off the main thread, process-cached) ───────────────────────────────

private object FrostCache {
    private val lock = Any()
    private var entry: Entry? = null

    data class Key(val width: Int, val height: Int, val source: FrostSource)

    class Entry(val key: Key, val bitmap: ImageBitmap?)

    /** Already-built bitmap for this exact configuration, without building one. */
    fun peek(context: Context, source: FrostSource): ImageBitmap? =
        synchronized(lock) { entry?.takeIf { it.key == keyFor(context, source) }?.bitmap }

    fun get(context: Context, source: FrostSource): ImageBitmap? = synchronized(lock) {
        val key = keyFor(context, source)
        val existing = entry
        if (existing != null && existing.key == key) {
            existing.bitmap
        } else {
            val built = runCatching { buildFrostedBackdrop(context, source) }.getOrNull()
            entry = Entry(key, built)
            built
        }
    }

    private fun keyFor(context: Context, source: FrostSource): Key {
        val metrics = context.resources.displayMetrics
        return Key(
            width = Frost.targetWidth(metrics.widthPixels),
            height = Frost.targetHeight(metrics.heightPixels),
            source = source,
        )
    }
}

/**
 * Draws the source at 1/16 scale and applies a one-shot blur.
 *
 * Never called on the main thread — [rememberFrostedBackdrop] hops to
 * [Dispatchers.Default] first. Roughly 30x80 px, so the whole thing is
 * microseconds of work.
 */
internal fun buildFrostedBackdrop(context: Context, source: FrostSource): ImageBitmap {
    val metrics = context.resources.displayMetrics
    val width = Frost.targetWidth(metrics.widthPixels)
    val height = Frost.targetHeight(metrics.heightPixels)

    val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(scaled).drawBackdropSource(context, source, width, height)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // API 29–30: no RenderEffect. The 1/16 downsample alone is already
        // low-frequency, so upscaling it reads as frosted glass — and no
        // third-party blur dependency is needed.
        return scaled.asImageBitmap()
    }

    // API 31+: one-shot RenderEffect on a ~30x80 bitmap — sub-millisecond.
    val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        renderEffect = RenderEffect.createBlurEffect(Frost.SIGMA, Frost.SIGMA, Shader.TileMode.CLAMP)
    }
    Canvas(blurred).drawBitmap(scaled, 0f, 0f, paint)
    scaled.recycle()
    return blurred.asImageBitmap()
}

private fun Canvas.drawBackdropSource(context: Context, source: FrostSource, width: Int, height: Int) {
    // Opaque black underneath: a frosted panel with a transparent hole in it
    // would let raw content show through, which is never what we want.
    drawColor(android.graphics.Color.BLACK)
    val gradient = R.drawable.brand_gradient
    val wallpaper = runCatching { WallpaperManager.getInstance(context).fastDrawable }.getOrNull()
    val drawable = when (source) {
        FrostSource.BrandGradient -> runCatching { context.getDrawable(gradient) }.getOrNull()
        FrostSource.Wallpaper -> wallpaper
        FrostSource.Auto -> wallpaper ?: runCatching { context.getDrawable(gradient) }.getOrNull()
    } ?: return
    drawable.setBounds(0, 0, width, height)
    drawable.draw(this)
}
