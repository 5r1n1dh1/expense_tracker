package com.srinidhi.kharch.ui.components

/** Visual weight of a [GlassButton], from a quiet secondary action to the primary one. */
enum class GlassButtonVariant {
    /** Accent-filled. The primary call to action. */
    Primary,

    /** Regular glass with an onPrimary label. */
    Secondary,

    /** No fill beyond the inset scrim. Tertiary / destructive-adjacent actions. */
    Ghost,
}
