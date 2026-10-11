package com.srinidhi.kharch.ui

import com.srinidhi.kharch.BuildConfig

/**
 * Build metadata surfaced in Settings → About and in pre-filled bug reports (P1).
 *
 * These are `val`, not `const val`: `BuildConfig.DEBUG` is a generated `Boolean`
 * field rather than a compile-time constant, so it cannot back a `const`.
 */
object BuildInfo {
    val VERSION_NAME: String = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    val DEBUG: Boolean = BuildConfig.DEBUG
}