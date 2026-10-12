package com.srinidhi.kharch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.srinidhi.kharch.ui.BuildInfo
import com.srinidhi.kharch.ui.motion.ProvideHaptics

/**
 * The app shell.
 *
 * This file is a **fixed seam**, not a feature. Two agents were building features
 * against this app in parallel — sign-in (Phase 2/3) and the expense navigation
 * (Phase 5) — and both needed the same `setContent { … }` body. Left alone they
 * would each have rewritten it, which is a guaranteed collision.
 *
 * The shell is now three nested layers with explicit owners:
 *
 * - **Owned by me (the orchestrator)** — the wrapper itself, [KharchAuthGate], and
 *   the markers below. Do not delete or rename them.
 * - **[ProvideHaptics]** — wrapped here so every screen below it reads a single
 *   controller. Phase 5 wires the user setting; that is one parameter, not this file.
 * - **[nav] region** — the ONLY place Phase 5's navigation belongs.
 *
 * To add a destination: put it inside the `nav()` lambda. Do not restructure the
 * wrapper, and do not add a second `KharchTheme`.
 */
@Composable
fun KharchRoot(
    // ── Region: NAVIGATION (Phase 5 owns everything inside nav) ──────────────────
    nav: @Composable () -> Unit = { Text(text = "Kharch ${BuildInfo.VERSION_NAME}") },
) {
    com.srinidhi.kharch.ui.theme.KharchTheme {
        ProvideHaptics {
            KharchAuthGate(nav = nav)
        }
    }
}

/**
 * Sign-in gate: renders its [nav] content once a Google account is resolved,
 * onboarding otherwise.
 *
 * Phase 2/3 fills in the body of [com.srinidhi.kharch.ui.auth] and swaps this out.
 * Until then every screen still renders, so Phase 5 and Phase 6 UI work is not
 * blocked on auth — and, crucially, nothing has to modify this file to get a
 * working UI out of CI.
 */
@Composable
fun KharchAuthGate(nav: @Composable () -> Unit) {
    nav()
}
