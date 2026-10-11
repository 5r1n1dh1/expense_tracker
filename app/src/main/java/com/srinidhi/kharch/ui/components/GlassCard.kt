package com.srinidhi.kharch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.glass.GlassTier
import com.srinidhi.kharch.ui.motion.HapticsController
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics

/*
 * The generic glass card: a GlassSurface with a content inset and a column body.
 * Screen-specific cards (Dashboard hero, expense row) build on top of this.
 */

/** Default inner inset. 20 dp keeps the sheen band clear of the first text line. */
private val CardPadding = PaddingValues(20.dp)

/**
 * A frosted card.
 *
 * @param modifier layout and behaviour only; insets go in [contentPadding].
 * @param tier frost strength; defaults to the Exaggerated default (D10).
 * @param shape defaults to `MaterialTheme.shapes.extraLarge` (28 dp).
 * @param contentPadding inset between the panel edge and [content].
 * @param onClick when non-null the card becomes a semantic button with a ripple
 *   and a light haptic. Keep it null for purely decorative panels.
 * @param haptics override for previews/tests; defaults to `LocalHaptics`.
 * @param content card body, laid out in a [ColumnScope].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.Exaggerated,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    contentPadding: PaddingValues = CardPadding,
    onClick: (() -> Unit)? = null,
    haptics: HapticsController? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hapticsController = haptics ?: LocalHaptics.current
    val action = onClick
    GlassSurface(
        modifier = modifier,
        tier = tier,
        shape = shape,
        contentPadding = contentPadding,
    ) {
        val clickableModifier = if (action == null) {
            Modifier
        } else {
            Modifier.clickable {
                hapticsController.perform(KharchHaptic.Selection)
                action()
            }
        }
        Column(modifier = Modifier.fillMaxWidth().then(clickableModifier)) {
            content()
        }
    }
}
