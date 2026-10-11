package com.srinidhi.kharch.ui

import com.srinidhi.kharch.BuildConfig

/** Build metadata surfaced in Settings → About and in bug reports (P1 compensation). */
object BuildInfo {
    const val VERSION_NAME: String = BuildConfig.VERSION_NAME
    const val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    const val DEBUG: Boolean = BuildConfig.DEBUG
}