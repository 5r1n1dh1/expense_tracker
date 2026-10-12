package com.srinidhi.kharch.ui.expenses

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.core.model.Category
import com.srinidhi.kharch.ui.glass.GlassShape
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.glass.GlassTier
import com.srinidhi.kharch.ui.motion.KharchHaptic
import com.srinidhi.kharch.ui.motion.LocalHaptics
import com.srinidhi.kharch.ui.theme.GlassColors
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/*
 * The category's visual identity: glyph, tint, and the chip that carries both.
 *
 * Two indirections live here and both exist for the same reason — a category is data, not a
 * drawable reference.
 *
 *  1. `iconKey` → vector drawable. `Category.iconKey` is a *key into the bundled symbol grid*
 *     (plan §5.2), never a raw resource name. It survives a category being renamed, synced to
 *     another device, or exported to JSON. material-icons-extended is deliberately not a
 *     dependency (plan §11.2, ~20 MB), so the twelve seeded categories ship their own vectors.
 *  2. `colorToken` → colour. The token is *semantic* (plan §5.2), never a hex, so the tint
 *     re-derives under dark mode, high contrast and Material You instead of being baked in.
 *     There is no way to express "twelve distinct tokens" without a palette, so the twelve map
 *     onto four semantic colours at three strengths — twelve slots, every one of them derived
 *     from a contrast-tested token.
 *
 * Rule §8.4.7: a category colour is *always* paired with an icon and a text label. The tint here
 * is a wash behind the glyph and the label is the chip's own text, so the colour is never the
 * only thing identifying a category.
 */

/** Alpha of the tint wash behind a glyph. Low enough that the glyph keeps full contrast. */
private const val GLYPH_TINT_ALPHA = 0.20f

/** The three tint strengths, so the four semantic colours yield twelve distinguishable slots. */
private val TINT_STRENGTHS = listOf(0.16f, 0.26f, 0.36f)

/** How many semantic base colours the palette rotates through. */
private const val TINT_BASES_SIZE = 4

/** Total palette slots: bases × strengths. */
private const val TINT_SLOT_COUNT = 12

/** Side of a category glyph chip. */
private val GLYPH_CHIP_SIZE = 32.dp

/** Glyph size inside its chip. */
private val GLYPH_SIZE = 18.dp

/** Minimum height of a category chip. Also its touch target (plan §13.2). */
private val CHIP_MIN_HEIGHT = 44.dp

/** The drawable for [iconKey], or the generic "more" glyph when the key is unknown. */
@DrawableRes
fun categoryGlyphRes(iconKey: String): Int = when (iconKey) {
    "restaurant" -> R.drawable.ic_cat_restaurant
    "directions_bus" -> R.drawable.ic_cat_directions_bus
    "shopping_bag" -> R.drawable.ic_cat_shopping_bag
    "receipt_long" -> R.drawable.ic_cat_receipt_long
    "home" -> R.drawable.ic_cat_home
    "favorite" -> R.drawable.ic_cat_favorite
    "school" -> R.drawable.ic_cat_school
    "movie" -> R.drawable.ic_cat_movie
    "flight" -> R.drawable.ic_cat_flight
    "local_grocery_store" -> R.drawable.ic_cat_local_grocery_store
    "payments" -> R.drawable.ic_cat_payments
    "more_horiz" -> R.drawable.ic_cat_more_horiz
    // An unknown key is not an error: it is a category created by a newer build and synced down
    // to this one. It must still render, so it renders as "more".
    else -> R.drawable.ic_cat_more_horiz
}

/**
 * The tint for a category's [colorToken].
 *
 * Deterministic in the token string, not in insertion order: a category that moves in the list
 * keeps its colour, and two devices agree without having to agree on an ordering.
 */
@Composable
fun categoryTint(colorToken: String): Color {
    val glass = currentGlassTheme()
    val slot = slotFor(colorToken)
    val base = tintBase(glass, slot % TINT_BASES_SIZE)
    val strength = TINT_STRENGTHS[slot / TINT_BASES_SIZE % TINT_STRENGTHS.size]
    return base.copy(alpha = strength)
}

/** The 0..11 palette slot a token occupies. Stable across sessions and devices. */
private fun slotFor(colorToken: String): Int {
    val hash = colorToken.hashCode()
    return (hash % TINT_SLOT_COUNT + TINT_SLOT_COUNT) % TINT_SLOT_COUNT
}

/** The base colour for slot [index], rotating through the four semantic hues. */
private fun tintBase(glass: GlassColors, index: Int): Color = when (index) {
    0 -> glass.accentFill
    1 -> glass.expenseAccent
    2 -> glass.credit
    else -> glass.expenseAccentAlt
}

/**
 * A category pill: the glyph in a tinted glass chip, then the name.
 *
 * Used by the Add sheet (single-select) and the filter sheet (multi-select); the caller decides
 * how selection is announced through [selectionRole] — `Role.RadioButton` for the Add sheet's
 * one-of-many, `Role.Checkbox` for the filter's any-of-many. Selection is never expressed by the
 * tint alone either way: the glyph chip's wash strengthens, which reads as a change of state
 * without depending on hue discrimination.
 *
 * @param selected drives the tint strength.
 * @param onClick fired on tap, preceded by a [KharchHaptic.Selection].
 * @param testTag stable tag for tests; omit for decorative uses.
 */
@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionRole: Role = Role.RadioButton,
    testTag: String? = null,
) {
    val glass = currentGlassTheme()
    val haptics = LocalHaptics.current
    val tint = if (selected) {
        categoryTint(category.colorToken).copy(alpha = GLYPH_TINT_ALPHA * 2f)
    } else {
        categoryTint(category.colorToken)
    }
    val tagModifier = if (testTag == null) Modifier else Modifier.testTag(testTag)
    GlassSurface(
        modifier = modifier
            .heightIn(min = CHIP_MIN_HEIGHT)
            .selectable(selected = selected, role = selectionRole) {
                haptics.perform(KharchHaptic.Selection)
                onClick()
            }
            .then(tagModifier),
        tier = GlassTier.Inset,
        shape = GlassShape.chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(GLYPH_CHIP_SIZE)
                    .background(tint, GlassShape.orb),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = categoryGlyphRes(category.iconKey)),
                    contentDescription = null,
                    modifier = Modifier.size(GLYPH_SIZE),
                    tint = glass.colors.onPrimary,
                )
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                color = glass.colors.onPrimary,
            )
        }
    }
}
