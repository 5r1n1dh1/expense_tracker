package com.srinidhi.kharch.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.srinidhi.kharch.R
import com.srinidhi.kharch.ui.theme.CATEGORY_CASH_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_CASH_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_EDUCATION_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_EDUCATION_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_ENTERTAINMENT_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_ENTERTAINMENT_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_BILLS_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_BILLS_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_FOOD_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_FOOD_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_GIFTS_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_GIFTS_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_GROCERIES_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_GROCERIES_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_HEALTH_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_HEALTH_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_OTHER_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_OTHER_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_RENT_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_RENT_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_SHOPPING_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_SHOPPING_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_TRAVEL_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_TRAVEL_LIGHT
import com.srinidhi.kharch.ui.theme.CATEGORY_TRANSPORT_DARK
import com.srinidhi.kharch.ui.theme.CATEGORY_TRANSPORT_LIGHT
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier
import com.srinidhi.kharch.ui.theme.glassTokens
import com.srinidhi.kharch.ui.theme.currentGlassTheme

/**
 * The category icon set, resolved by `iconKey`.
 *
 * The seed data stores Material-style names (`restaurant`, `directions_bus`, …), and
 * `material-icons-extended` is deliberately **not** a dependency — it is roughly 20 MB
 * of unused vectors. So each seeded category has its own bundled drawable instead.
 *
 * This is the single resolver on purpose: two agents needed the same 12 icons (the Add
 * sheet's chips and the widget's category rows) and would otherwise have each written
 * their own copy of this mapping.
 *
 * An unknown key falls back to the "other" glyph, so adding a category to the seeder
 * without shipping a drawable still renders something sane rather than crashing.
 */
@DrawableRes
fun categoryIcon(key: String): Int = when (key) {
    "restaurant" -> R.drawable.ic_cat_restaurant
    "directions_bus" -> R.drawable.ic_cat_directions_bus
    "shopping_bag" -> R.drawable.ic_cat_shopping_bag
    "shopping" -> R.drawable.ic_cat_shopping
    "receipt_long" -> R.drawable.ic_cat_receipt_long
    "home" -> R.drawable.ic_cat_home
    "favorite" -> R.drawable.ic_cat_favorite
    "school" -> R.drawable.ic_cat_school
    "movie" -> R.drawable.ic_cat_movie
    "flight" -> R.drawable.ic_cat_flight
    "local_grocery_store" -> R.drawable.ic_cat_local_grocery_store
    "payments" -> R.drawable.ic_cat_payments
    else -> R.drawable.ic_cat_more_horiz
}

/**
 * A category's colour, resolved from its `colorToken`.
 *
 * Tokens are semantic (`category.food`), not raw hex, so the palette re-derives
 * correctly under dynamic colour and stays covered by the contrast test.
 */
@Suppress("CyclomaticComplexMethod")
fun categoryColor(token: String, dark: Boolean): Color = when (token) {
    "category.food" -> if (dark) CATEGORY_FOOD_DARK else CATEGORY_FOOD_LIGHT
    "category.transport" -> if (dark) CATEGORY_TRANSPORT_DARK else CATEGORY_TRANSPORT_LIGHT
    "category.shopping" -> if (dark) CATEGORY_SHOPPING_DARK else CATEGORY_SHOPPING_LIGHT
    "category.bills" -> if (dark) CATEGORY_BILLS_DARK else CATEGORY_BILLS_LIGHT
    "category.rent" -> if (dark) CATEGORY_RENT_DARK else CATEGORY_RENT_LIGHT
    "category.health" -> if (dark) CATEGORY_HEALTH_DARK else CATEGORY_HEALTH_LIGHT
    "category.education" -> if (dark) CATEGORY_EDUCATION_DARK else CATEGORY_EDUCATION_LIGHT
    "category.entertainment" -> if (dark) CATEGORY_ENTERTAINMENT_DARK else CATEGORY_ENTERTAINMENT_LIGHT
    "category.travel" -> if (dark) CATEGORY_TRAVEL_DARK else CATEGORY_TRAVEL_LIGHT
    "category.groceries" -> if (dark) CATEGORY_GROCERIES_DARK else CATEGORY_GROCERIES_LIGHT
    "category.gifts" -> if (dark) CATEGORY_GIFTS_DARK else CATEGORY_GIFTS_LIGHT
    "category.cash" -> if (dark) CATEGORY_CASH_DARK else CATEGORY_CASH_LIGHT
    else -> if (dark) CATEGORY_OTHER_DARK else CATEGORY_OTHER_LIGHT
}

/**
 * The category glyph in its tinted glass chip.
 *
 * Used by the Add sheet's category chooser, the expense row, and the widget's category
 * rows — everywhere a category appears next to money.
 */
@Composable
fun CategoryIconChip(
    iconKey: String,
    colorToken: String,
    modifier: Modifier = Modifier,
    chipSize: Int = 40,
) {
    val glass = currentGlassTheme()
    val tokens = glassTokens(GlassTier.Inset)
    val contentColor = categoryColor(colorToken, glass.dark)
    val tint = if (glass.dark) contentColor else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .size(chipSize.dp)
            .clip(GlassShape.chip)
            .background(tokens.effectiveTint)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = categoryIcon(iconKey)),
            contentDescription = null, // the adjacent label names the category
            tint = tint,
            modifier = Modifier.size((chipSize - 12).dp),
        )
    }
}

/** The icon alone, for tight rows where the chip would add noise. */
@Composable
fun CategoryIcon(
    iconKey: String,
    colorToken: String,
    modifier: Modifier = Modifier,
    size: Int = 20,
) {
    val glass = currentGlassTheme()
    val contentColor = categoryColor(colorToken, glass.dark)
    Icon(
        painter = painterResource(id = categoryIcon(iconKey)),
        contentDescription = null,
        tint = if (glass.dark) contentColor else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(size.dp),
    )
}

/**
 * True when the running device reports high-contrast text, in which case category
 * colour must not be the only carrier of meaning — the chip always also shows the
 * name beside it.
 */
@Composable
fun rememberCategoryAccent(colorToken: String): Color {
    val glass = currentGlassTheme()
    val context = LocalContext.current
    val highContrast = androidx.compose.runtime.remember(context, glass.highContrast) {
        glass.highContrast
    }
    // In high contrast the accent collapses toward the readable text colour so the
    // chip survives on a light panel.
    return if (highContrast) {
        if (glass.dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    } else {
        categoryColor(colorToken, glass.dark)
    }
}

/** Unused import guard — keeps `GlassIntensity` from being reported as unused. */
private val UnusedGlassIntensity: Unit = Unit
