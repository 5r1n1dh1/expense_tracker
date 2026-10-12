package com.srinidhi.kharch.ui.addedit

import kotlinx.serialization.Serializable

/**
 * What the Add/Edit sheet was opened to do.
 *
 * Two of these are the same form with a different starting point (plan §7.2C):
 *
 *  - [ADD] — a blank sheet. `dateEpochDay` may carry "today", or a date the user picked
 *    somewhere else in the app.
 *  - [EDIT] — every field prefilled from the existing row. The only mode that writes back to
 *    the same id.
 *  - [DUPLICATE] — the long-press action. Content is prefilled, the date moves to today, and a
 *    fresh id is minted, so a duplicate is a *new* expense and never an edit.
 *  - [REFUND] — "Mark as refunded" (D5, §7.3). Opens as a linked refund of the row named by
 *    `expenseId`: the kind is fixed to `REFUND`, the amount and the link are prefilled from that
 *    row, and the category is pinned to it because a refund must share its original's category.
 */
@Serializable
enum class AddEditMode {
    ADD,
    EDIT,
    DUPLICATE,
    REFUND,
}
