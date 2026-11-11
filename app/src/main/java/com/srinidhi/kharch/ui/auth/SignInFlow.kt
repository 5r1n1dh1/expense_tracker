package com.srinidhi.kharch.ui.auth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
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
        data class NeedsSetup(val user: GoogleUser) : SignInFlowResult
        data object AlreadySignedIn : SignInFlowResult
        data object Cancelled : SignInFlowResult
        data class Failed(val reason: String) : SignInFlowResult
    }

    /** Starts onboarding; creates Credential Manager intent and delegates to launcher. */
    suspend fun startFirstRun(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): SignInFlowResult {
        val existing = signInClient.currentUser()
        if (existing != null) {
            val wid = workspaceManager.workspaceId()
            return if (wid != null) {
                SignInFlowResult.Success(existing, wid)
            } else {
                SignInFlowResult.NeedsSetup(existing)
            }
        }
        val pendingIntent = signInClient.createSignInIntent()
        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        launcher.launch(request)
        return SignInFlowResult.Cancelled
    }

    /** Handles result from launcher; preserves profile and triggers workspace sync. */
    suspend fun handleSignInResult(intent: Intent): SignInFlowResult = try {
        val result = signInClient.handleSignInResult(intent)
        when (result) {
            is GoogleSignInResult.Success -> {
                val user = result.user
                val existingWid = workspaceManager.workspaceId()
                if (existingWid != null) {
                    val workspace = workspaceManager.ensureWorkspace(user.uid)
                    settingsStore.setActiveAuth(user.uid, workspace.id)
                    SignInFlowResult.Success(user, workspace.id)
                } else {
                    SignInFlowResult.NeedsSetup(user)
                }
            }
            is GoogleSignInResult.Cancelled -> SignInFlowResult.Cancelled
            is GoogleSignInResult.Failed -> SignInFlowResult.Failed(result.reason)
        }
    } catch (e: Exception) {
        SignInFlowResult.Failed(e.message ?: "Unknown error")
    }

    /** Completes first-run setup with chosen ledger name and sets active auth. */
    suspend fun completeSetup(uid: String, ledgerName: String): String {
        val name = ledgerName.trim().ifEmpty { "Personal Expenses" }
        val workspace = workspaceManager.createWorkspace(uid, name)
        settingsStore.setActiveAuth(uid, workspace.id)
        return workspace.id
    }

    /** Join-vs-start choice sheet (§6.2). Default = join existing workspace. */
    suspend fun presentChoiceSheet(uid: String, join: Boolean): String = if (join) {
        workspaceManager.ensureWorkspace(uid).id
    } else {
        workspaceManager.createWorkspace(uid, "Kharch").id
    }
}

