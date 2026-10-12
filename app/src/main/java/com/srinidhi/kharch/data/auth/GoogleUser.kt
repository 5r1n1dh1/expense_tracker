package com.srinidhi.kharch.data.auth

/**
 * A Google identity, reduced to the four things the ledger actually needs (plan §6.1.4).
 *
 * Deliberately free of Firebase types: the [uid] is the stable identity that becomes a
 * membership entry on the workspace document, and the other three are display-only. Nothing
 * here is ever a query key — accounts are *members*, not partitions (D1), so no query in the
 * codebase filters by uid.
 *
 * @property uid the Firebase Auth uid of the signed-in account.
 * @property email shown in the Accounts screen and in the "Synced as …" label.
 * @property displayName from the Google profile; may be absent for Workspace-style accounts.
 * @property photoUri avatar, as a string so it can be persisted without a Uri type leaking.
 */
data class GoogleUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUri: String? = null,
) {
    /** What the "Synced as …" label shows: the email when there is one, else the uid. */
    val label: String
        get() = email?.takeIf(String::isNotBlank) ?: uid
}
