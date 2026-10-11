package com.srinidhi.kharch.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Raw colour constants for the two schemes (plan §8.5, decision D8).
 *
 * These are the ONLY place a hex literal appears. GlassTokens composes them into
 * per-tier tokens; Theme.kt composes them into Material color schemes. Light and
 * dark are NOT inversions of one another — the inner stroke in particular flips
 * from a bright rim (dark) to a dark rim (light), because a white rim on a
 * white panel is invisible.
 */

// ── Brand ────────────────────────────────────────────────────────────────────

/** Brand indigo — start of the gradient backdrop. Mirrors `@color/brand_indigo`. */
val BrandIndigo = Color(0xFF4F5DD3)

/** Brand teal — end of the gradient backdrop. Mirrors `@color/brand_teal`. */
val BrandTeal = Color(0xFF2BB3A3)

// ── Dark scheme (§8.5) ────────────────────────────────────────────────────────

/** Dark glass tints *towards* this colour: it darkens whatever is behind it. */
val DarkGlassTint = Color(0xFF0B1020)

/** Dark scheme inner stroke is WHITE — a bright top-left rim that replaces elevation. */
val DarkInnerStroke = Color(0xFFFFFFFF)

/** Primary text on glass: headlines and large amounts. */
val DarkTextPrimary = Color(0xFFF5F7FA)

/** Secondary text on glass: body copy, supporting lines. */
val DarkTextSecondary = Color(0xFFC3CCDC)

/** Filled-button / accent surface, dark scheme: brand indigo two shades brighter. */
val DarkAccentFill = Color(0xFF8E9BFF)

/** Label colour that sits on [DarkAccentFill]. */
val DarkOnAccentFill = Color(0xFF0B1020)

/** Credit / refund accent, dark scheme. */
val DarkCredit = Color(0xFF7FD1AE)

/** Expense accent, dark scheme: brand indigo, two shades brighter. */
val DarkExpenseAccent = Color(0xFF8E9BFF)

/** Secondary expense accent, dark scheme: brand teal, two shades brighter. */
val DarkExpenseAccentAlt = Color(0xFF5FE0CE)

// ── Light scheme (§8.5) ───────────────────────────────────────────────────────

/** Light glass tints towards white: it lightens whatever is behind it. */
val LightGlassTint = Color(0xFFFFFFFF)

/** Light scheme inner stroke is a DARK rim — a white rim would vanish entirely. */
val LightInnerStroke = Color(0xFF0B1020)

/** Primary text on glass, light scheme. */
val LightTextPrimary = Color(0xFF0B1020)

/** Secondary text on glass, light scheme. */
val LightTextSecondary = Color(0xFF3A4257)

/** Filled-button / accent surface, light scheme: the brand indigo itself. */
val LightAccentFill = Color(0xFF4F5DD3)

/** Label colour that sits on [LightAccentFill]. */
val LightOnAccentFill = Color(0xFFFFFFFF)

/** Credit / refund accent, light scheme. */
val LightCredit = Color(0xFF0E6B4F)

/** Expense accent, light scheme: brand indigo, two shades darker. */
val LightExpenseAccent = Color(0xFF2B3AA8)

/** Secondary expense accent, light scheme: brand teal, two shades darker. */
val LightExpenseAccentAlt = Color(0xFF14736A)

// ── Legibility scrims (§8.4.3, §8.4.6) ───────────────────────────────────────

/**
 * Default scrim, dark scheme: nudges local contrast up without flattening the
 * frost. The alpha itself is per tier, not here.
 */
val DarkScrim = Color(0xFF0B1020)

/** Default scrim, light scheme. Alpha is per tier. */
val LightScrim = Color(0xFFFFFFFF)

/**
 * High-contrast scrim, dark scheme — near-opaque, alpha included (mirrors
 * `@color/glass_scrim_dark`). The alpha in this literal is the one that ships;
 * high-contrast mode does not re-derive it.
 */
val DarkScrimHighContrast = Color(0xF20B1020)

/** High-contrast scrim, light scheme — near-opaque (mirrors `@color/glass_scrim_light`). */
val LightScrimHighContrast = Color(0xE6FFFFFF)

// ── Backdrop (§8.5) ───────────────────────────────────────────────────────────

/** Brand gradient dimmed by 55 % — the dark scheme's static backdrop. */
val DarkBackdropStart = Color(0xFF242A5F)

/** Brand gradient dimmed by 55 % — the dark scheme's static backdrop. */
val DarkBackdropEnd = Color(0xFF135149)

/** Same gradient lifted 25 % towards white — the light scheme's static backdrop. */
val LightBackdropStart = Color(0xFF7B86DE)

/** Same gradient lifted 25 % towards white — the light scheme's static backdrop. */
val LightBackdropEnd = Color(0xFF60C6BA)

/**
 * Overlay applied over the raw gradient to reach the dimmed/lifted backdrop
 * above: 55 % black in dark, 25 % white in light.
 */
val DarkBackdropOverlay = Color(0x8C000000)

/** See [DarkBackdropOverlay]. */
val LightBackdropOverlay = Color(0x40FFFFFF)

// ── Material scheme surfaces ──────────────────────────────────────────────────

/** Light scheme page surface, visible where no glass sits on top. */
val LightPageBackground = Color(0xFFF6F8FC)

/** Dark scheme raised surface (cards behind glass, sheets). */
val DarkSurfaceVariant = Color(0xFF23283A)

/** Light scheme raised surface. */
val LightSurfaceVariant = Color(0xFFE2E5EF)

/** Shared scrim colour for dialogs and sheets. */
val ScrimBlack = Color(0xFF000000)
