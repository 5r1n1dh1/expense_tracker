package com.srinidhi.kharch.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Shape scale. Glass panels want generous radii: a large radius is what makes a
 * translucent surface read as a *slab* of glass rather than a translucent
 * rectangle (§8.3).
 */

/** Material 3 shape scale. `extraLarge` (28 dp) is the default glass panel. */
val KharchShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Named shapes for glass surfaces, so screens never hard-code a radius. */
object GlassShape {
    /** Default card / panel radius. */
    val panel: Shape = RoundedCornerShape(28.dp)

    /** A slightly tighter card, for dense lists. */
    val card: Shape = RoundedCornerShape(24.dp)

    /** Bottom sheet top corners. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Inset text field. */
    val field: Shape = RoundedCornerShape(20.dp)

    /** Chips, category pills, buttons. */
    val chip: Shape = RoundedCornerShape(16.dp)

    /** The empty-state orb (§8.4.9 — the orb sits on the same Exaggerated glass). */
    val orb: Shape = CircleShape

    /** Sheets and full-bleed surfaces that must still meet the status bar. */
    val flush: Shape = RectangleShape

    /** Exposed as dp for callers that need to size a layout around the shape. */
    val panelRadius: Dp = 28.dp
}
