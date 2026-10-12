package com.srinidhi.kharch.ui.dashboard

import androidx.compose.ui.graphics.Color

/*
 * The donut's slice colours.
 *
 * `Category.colorToken` is a SEMANTIC token ("category.food"), not a hex — that is the
 * whole point of the field: the colour re-derives under dark mode, high contrast and
 * dynamic colour instead of shipping a literal that was tuned on one device. This file is
 * the single place a token becomes a colour, so the token table stays in one row-set that
 * a designer (or a later contrast test) can read at a glance.
 *
 * Unknown tokens are not an error: a user-created category carries whatever token the
 * Add sheet assigned, and a row must never fail to draw because of it. They fall through
 * to a stable, deterministic cycle next.
 */

/** The chart palette. Bright enough to read on both a dark and a light glass panel. */
private val PALETTE: List<Color> = listOf(
    Color(0xFF6E8BFF),
    Color(0xFF3FC7B4),
    Color(0xFFEF9A3C),
    Color(0xFF7ED492),
    Color(0xFFE8799B),
    Color(0xFF9C8BFF),
    Color(0xFF4FB8E8),
    Color(0xFFE46A5A),
    Color(0xFFB0CE5A),
    Color(0xFFD98CD0),
    Color(0xFF5CC3A6),
    Color(0xFFC0A98A),
)

/** The twelve seeded tokens, in display order. User tokens fall through to [PALETTE]. */
private val TOKENS: Map<String, Color> = mapOf(
    "category.food" to PALETTE[0],
    "category.transport" to PALETTE[1],
    "category.shopping" to PALETTE[2],
    "category.bills" to PALETTE[3],
    "category.rent" to PALETTE[4],
    "category.health" to PALETTE[5],
    "category.education" to PALETTE[6],
    "category.entertainment" to PALETTE[7],
    "category.travel" to PALETTE[8],
    "category.groceries" to PALETTE[9],
    "category.cash" to PALETTE[10],
    "category.other" to PALETTE[11],
)

/**
 * The slice colour for [token].
 *
 * Stable across recomposition, across processes and across devices, so two members of the
 * same workspace (D1) always see the same donut.
 */
fun categoryColour(token: String): Color =
    TOKENS[token] ?: PALETTE[(token.hashCode() and 0x7FFFFFFF) % PALETTE.size]
