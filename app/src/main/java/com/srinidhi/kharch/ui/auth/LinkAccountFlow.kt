package com.srinidhi.kharch.ui.auth

import android.content.Intent
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
        // Sign-in options are created by the injected GoogleSignInClient; this flow does
        // not consume the lower-level GetSignInWithGoogleOption builder directly.
        return try {
            signInClient.handleSignInResult(Intent())
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: "Link failed")
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
