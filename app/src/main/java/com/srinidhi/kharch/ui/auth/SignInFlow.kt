package com.srinidhi.kharch.ui.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.srinidhi.kharch.data.auth.GoogleSignInClient
import com.srinidhi.kharch.data.auth.GoogleSignInResult
import com.srinidhi.kharch.data.auth.GoogleUser
import com.srinidhi.kharch.data.prefs.SettingsStore
import com.srinidhi.kharch.data.workspace.WorkspaceManager
import javax.inject.Inject
import javax.inject.Singleton

/** First-run / onboarding sign-in flow (§6.1, §6.2). Uses Credential Manager intent. */
@Singleton
class SignInFlow @Inject constructor(
    private val signInClient: GoogleSignInClient,
    private val settingsStore: SettingsStore,
    private val workspaceManager: WorkspaceManager,
) {

    sealed interface SignInFlowResult {
        data class Success(val user: GoogleUser, val workspaceId: String) : SignInFlowResult
        data object AlreadySignedIn : SignInFlowResult
        data object Cancelled : SignInFlowResult
        data class Failed(val reason: String) : SignInFlowResult
    }

    /** Starts onboarding; creates Credential Manager intent and delegates to launcher. */
    suspend fun startFirstRun(
        context: Context,
        launcher: ActivityResultLauncher<PendingIntent>,
    ): SignInFlowResult {
        val existing = signInClient.currentUser()
        if (existing != null) {
            val wid = workspaceManager.workspaceId() ?: return SignInFlowResult.AlreadySignedIn
            return SignInFlowResult.Success(existing, wid)
        }
        val intent = signInClient.createSignInIntent()
        launcher.launch(intent)
        return SignInFlowResult.Cancelled
    }

    /** Handles result from launcher; perserves profile and triggers workspace sync. */
    suspend fun handleSignInResult(intent: Intent): SignInFlowResult = try {
        val result = signInClient.handleSignInResult(intent)
        when (result) {
            is GoogleSignInResult.Success -> {
                val user = result.user
                val workspace = workspaceManager.ensureWorkspace(user.uid)
                settingsStore.setActiveAuth(user.uid, workspace.id)
                SignInFlowResult.Success(user, workspace.id)
            }
            is GoogleSignInResult.Cancelled -> SignInFlowResult.Cancelled
            is GoogleSignInResult.Failed -> SignInFlowResult.Failed(result.reason)
        }
    } catch (e: Exception) {
        SignInFlowResult.Failed(e.message ?: "Unknown error")
    }

    /** Join-vs-start choice sheet (§6.2). Default = join existing workspace. */
    suspend fun presentChoiceSheet(uid: String, join: Boolean): String = if (join) {
        workspaceManager.ensureWorkspace(uid).id
    } else {
        workspaceManager.createWorkspace(uid, "Kharch").id
    }
}
