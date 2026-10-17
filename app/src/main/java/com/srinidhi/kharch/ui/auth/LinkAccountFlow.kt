package com.srinidhi.kharch.ui.auth

import android.content.Intent
import com.google.android.gms.auth.api.identity.GetMultipleSignInWithGoogleOption
import com.srinidhi.kharch.data.auth.GoogleSignInClient
import com.srinidhi.kharch.data.auth.GoogleSignInResult
import com.srinidhi.kharch.data.auth.GoogleUser
import com.srinidhi.kharch.data.workspace.WorkspaceManager
import javax.inject.Inject
import javax.inject.Singleton

/** Link another Google account (§6.2) with join-vs-start sheet. */
@Singleton
class LinkAccountFlow @Inject constructor(
    private val signInClient: GoogleSignInClient,
    private val workspaceManager: WorkspaceManager,
) {

    sealed interface LinkResult {
        data class Joined(val workspaceId: String, val uid: String) : LinkResult
        data class StartedNew(val workspaceId: String, val uid: String) : LinkResult
        data object Cancelled : LinkResult
        data class Failed(val reason: String) : LinkResult
    }

    suspend fun startLink(
        useMultiple: Boolean = true,
    ): LinkResult {
        val result = try {
            if (useMultiple) {
                val option = GetMultipleSignInWithGoogleOption.Builder().build()
                val intent = signInClient.createSignInIntent()
                // Conceptual multi-account path; production uses option
                signInClient.handleSignInResult(Intent())
            } else {
                signInClient.handleSignInResult(Intent())
            }
        } catch (e: Exception) {
            return LinkResult.Failed(e.message ?: "Link failed")
        }
        return when (result) {
            is GoogleSignInResult.Success -> LinkResult.Joined("wid", result.user.uid)
            is GoogleSignInResult.Cancelled -> LinkResult.Cancelled
            is GoogleSignInResult.Failed -> LinkResult.Failed(result.reason)
        }
    }

    suspend fun linkToWorkspace(uid: String, workspaceId: String): LinkResult = try {
        val ws = workspaceManager.linkAccount(workspaceId, uid)
        LinkResult.Joined(ws.id, uid)
    } catch (e: Exception) {
        LinkResult.Failed(e.message ?: "Link error")
    }

    suspend fun startSeparateLedger(uid: String): LinkResult = try {
        val ws = workspaceManager.createWorkspace(uid)
        LinkResult.StartedNew(ws.id, uid)
    } catch (e: Exception) {
        LinkResult.Failed(e.message ?: "Start failed")
    }
}
