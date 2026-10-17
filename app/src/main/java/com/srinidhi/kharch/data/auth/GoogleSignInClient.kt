package com.srinidhi.kharch.data.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.srinidhi.kharch.R
import com.srinidhi.kharch.data.awaitTask
import java.util.UUID

/**
 * Credential Manager based Google sign-in (plan §6.1, decision D3).
 *
 * Uses `play-services-auth:22.0.0`'s `Identity.getSignInClient()` API — the modern replacement
 * for the legacy `play-services-auth` `GoogleSignInClient`. Firebase types do not escape this
 * file: the ID token is exchanged for a Firebase session internally and only a [GoogleUser]
 * comes back out, so the workspace layer has no Firebase surface at all (plan §4: `domain` is
 * pure Kotlin and `data/` keeps its remote coupling in the one place it belongs).
 */
class GoogleSignInClient internal constructor(
    private val context: Context,
    private val webClientId: String,
    private val signInClient: SignInClient,
    private val exchange: (String) -> Task<com.google.firebase.auth.AuthResult>,
    private val existingSession: () -> FirebaseUser?,
) {

    /** Production wiring. Reads the web client id from `R.string.default_web_client_id`. */
    constructor(context: Context) : this(
        context = context.applicationContext,
        webClientId = context.getString(R.string.default_web_client_id),
        signInClient = Identity.getSignInClient(context),
        exchange = ::exchangeWithFirebase,
        existingSession = { FirebaseAuth.getInstance().currentUser },
    )

    /**
     * Creates a pending intent for Google sign-in. The caller launches this intent (via
     * `ActivityResultLauncher` or `startActivityForResult`) and passes the result Intent to
     * [handleSignInResult].
     */
    suspend fun createSignInIntent(): PendingIntent {
        val nonce = UUID.randomUUID().toString()

        val request = GetSignInIntentRequest.Builder()
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()

        return signInClient.getSignInIntent(request).awaitTask()
    }

    /**
     * Processes the result of a [createSignInIntent] launch and returns the authenticated user.
     *
     * Call this from your Activity's [ActivityResultLauncher] callback with the result Intent.
     */
    suspend fun handleSignInResult(intent: Intent): GoogleSignInResult {
        val credential = signInClient.getSignInCredentialFromIntent(intent)
        val idToken = credential.id
        if (idToken.isNullOrEmpty()) {
            return GoogleSignInResult.Failed("No ID token in sign-in credential")
        }
        val session = exchange(idToken).awaitTask()
        val user = session.user ?: return GoogleSignInResult.Failed(MISSING_FIREBASE_USER)
        return GoogleSignInResult.Success(toGoogleUser(user))
    }

    /**
     * The account already signed in on this device, or `null`.
     *
     * Used so a returning user skips onboarding entirely (§6.1 step 3): the local workspace
     * row already exists and the Firebase session is still valid.
     */
    fun currentUser(): GoogleUser? = existingSession()?.let(::toGoogleUser)

    /**
     * Signs this device out of both Firebase and Credential Manager.
     *
     * This is what "Remove" on an account row does (§6.2): it revokes *this device's*
     * credential. The ledger and every expense survive, because no data is owned by a
     * member — removing an account is a credential change, not a data operation.
     */
    suspend fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /** Converts a Firebase user into the workspace-facing [GoogleUser] (Firebase types stay in this file). */
    private fun toGoogleUser(firebaseUser: FirebaseUser): GoogleUser = GoogleUser(
        uid = firebaseUser.uid,
        email = firebaseUser.email,
        displayName = firebaseUser.displayName,
        photoUri = firebaseUser.photoUrl?.toString(),
    )

    companion object {
        private const val UNRECOGNISED_CREDENTIAL = "Credential Manager returned an unusable credential"
        private const val MISSING_FIREBASE_USER = "Firebase returned no user for the Google credential"

        /**
         * Exchange a Google ID token for a Firebase session.
         *
         * A named top-level function rather than an inline lambda so the production path is
         * one identifiable thing, and so a test can substitute the [exchange] seam without
         * initialising Firebase on a JVM.
         */
        internal fun exchangeWithFirebase(idToken: String): Task<com.google.firebase.auth.AuthResult> {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            return FirebaseAuth.getInstance().signInWithCredential(credential)
        }
    }
}

/**
 * Factory for creating [GoogleSignInClient] instances.
 */
object GoogleSignInClientFactory {
    fun create(context: Context): GoogleSignInClient {
        return GoogleSignInClient(
            context = context.applicationContext,
            webClientId = context.getString(R.string.default_web_client_id),
            signInClient = Identity.getSignInClient(context),
            exchange = ::exchangeWithFirebase,
            existingSession = { FirebaseAuth.getInstance().currentUser },
        )
    }
}