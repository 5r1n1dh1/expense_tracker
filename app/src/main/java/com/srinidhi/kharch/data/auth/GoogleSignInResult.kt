package com.srinidhi.kharch.data.auth

/**
 * The outcome of one "Continue with Google" attempt (plan §6.1).
 *
 * Cancellation is a **first-class outcome, not an error**: a user who dismisses the
 * Credential Manager sheet has not broken anything, and neither has a device with no
 * Google account on it. Both are [Cancelled], so the onboarding screen can stay quiet
 * rather than showing a failure the user did not cause.
 */
sealed interface GoogleSignInResult {

    /** Signed in. [user] carries the uid that becomes a workspace membership. */
    data class Success(val user: GoogleUser) : GoogleSignInResult

    /**
     * The user closed the sheet, or no credential was available on the device.
     *
     * The credential-manager troubleshooting guide is explicit that this double as a
     * configuration signal (a repeated cancellation usually means the web client id is
     * wrong rather than that the user keeps changing their mind), so the onboarding
     * screen offers the same action again instead of escalating.
     */
    data object Cancelled : GoogleSignInResult

    /** Something is genuinely wrong — Play services, the web client id, or the network. */
    data class Failed(val reason: String) : GoogleSignInResult
}
