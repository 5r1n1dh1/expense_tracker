package com.srinidhi.kharch.core.model

/**
 * A spending category (decision D4).
 *
 * Twelve categories ship with the app and the user may create, rename, reorder and hide more.
 * A category that is in use is hidden, never deleted, so history always keeps rendering.
 *
 * @property id `"sys_food"` for a seeded category, or a UUID for a user-created one.
 * @property iconKey key into the bundled symbol grid — never a raw drawable name.
 * @property colorToken a **semantic** colour token, never a raw hex, so the colour re-derives
 *   correctly under dark mode, high contrast and dynamic colour.
 * @property sortOrder contiguous display position, `0..n-1`.
 * @property isSystem `true` for the twelve seeded categories (hideable and renameable, but not
 *   deletable).
 * @property isHidden hidden categories are not offered in the Add sheet or filter chips, but
 *   existing expenses keep rendering them.
 */
data class Category(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorToken: String,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isHidden: Boolean = false,
    val updatedAt: Long = 0L,
)
