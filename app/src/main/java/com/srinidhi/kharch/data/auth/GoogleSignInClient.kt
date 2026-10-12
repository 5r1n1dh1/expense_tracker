package com.srinidhi.kharch.data.auth

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.DirectExecutor
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.GetSignInWithGoogleOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.CustomCredential
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.srinidhi.kharch.R
import com.srinidhi.kharch.data.awaitTask
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Credential Manager based Google sign-in (plan §6.1, decision D3).
 *
 * Uses `GetSignInWithGoogleOption` and **not** the multi-account
 * `GetMultipleSignInWithGoogleOption`: the multi variant was still alpha when this was
 * written, so linking a second account is done by issuing a **second single-option
 * request** instead. Credential Manager's own chooser already lists every account on the
 * device, which is exactly the §6.2 "link another Google account" behaviour — without
 * depending on an alpha API.
 *
 * The legacy `play-services-auth` `GoogleSignInClient` is deliberately not used anywhere
 * (plan §11.2): it drags in the whole GMS stack and Google has deprecated it in favour of
 * Credential Manager.
 *
 * Firebase types do not escape this file. The ID token is exchanged for a Firebase session
 * internally and only a [GoogleUser] comes back out, so the workspace layer has no Firebase
 * surface at all (plan §4: `domain` is pure Kotlin and `data/` keeps its remote coupling in
 * the one place it belongs).
 */
class GoogleSignInClient internal constructor(
    private val context: Context,
    private val webClientId: String,
    private val credentialManager: CredentialManager,
    private val exchange: (String) -> Task<com.google.firebase.auth.AuthResult>,
    private val existingSession: () -> FirebaseUser?,
) {

    /** Production wiring. Reads the web client id from `R.string.default_web_client_id`. */
    constructor(context: Context) : this(
        context = context.applicationContext,
        webClientId = context.getString(R.string.default_web_client_id),
        credentialManager = CredentialManager.create(context),
        exchange = ::exchangeWithFirebase,
        existingSession = { FirebaseAuth.getInstance().currentUser },
    )

    /**
     * Shows the Credential Manager sheet and reports who signed in.
     *
     * A fresh nonce is generated per attempt and carried into the token: Firebase rejects a
     * replayed ID token, which is what stops a sheet that was abandoned and re-shown from
     * being answered with the previous attempt's token.
     */
    suspend fun signIn(): GoogleSignInResult {
        val nonce = UUID.randomUUID().toString()
        val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val response = requestCredential(request)
            .getOrElse { failure -> return recoverFrom(failure as GetCredentialException) }
        val credential = readGoogleCredential(response)
            .getOrElse { failure -> return GoogleSignInResult.Failed(failure.message ?: UNRECOGNISED_CREDENTIAL) }
        val session = exchange(credential.idToken).awaitTask()
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
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun requestCredential(request: GetCredentialRequest): Result<GetCredentialResponse> =
        suspendCancellableCoroutine { continuation ->
            try {
                credentialManager.getCredentialAsync(
                    context = context,
                    request = request,
                    cancellationSignal = null,
                    executor = DirectExecutor,
                    callback = object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                        override fun onResult(result: GetCredentialResponse) {
                            continuation.resumeWith(Result.success(result))
                        }

                        override fun onError(exception: GetCredentialException) {
                            continuation.resumeWith(Result.failure(exception))
                        }
                    },
                )
            } catch (failure: GetCredentialException) {
                continuation.resumeWith(Result.failure(failure))
            }
        }

    private fun readGoogleCredential(response: GetCredentialResponse): Result<GoogleIdTokenCredential> {
        val credential = response.credential
        if (credential !is androidx.credentials.CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return Result.failure(IllegalStateException(UNRECOGNISED_CREDENTIAL))
        }
        return runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
    }

    private fun recoverFrom(exception: GetCredentialException): GoogleSignInResult = when (exception) {
        // The user closed the sheet, or the device has no credential to offer. Neither is a
        // failure: the first is the user's right, and the second is normal on a fresh device.
        is GetCredentialCancellationException, is NoCredentialException -> GoogleSignInResult.Cancelled
        else -> GoogleSignInResult.Failed(exception.message ?: exception.type)
    }

    private fun toGoogleUser(user: FirebaseUser): GoogleUser = GoogleUser(
        uid = user.uid,
        email = user.email,
        displayName = user.displayName,
        photoUri = user.photoUrl?.toString(),
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

/** Credential Manager posts its own result off the calling thread, so no hop is needed here. */
private val DirectExecutor = Executor { runnable -> runnable.run() }
