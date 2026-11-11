package com.srinidhi.kharch.ui.auth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srinidhi.kharch.data.auth.GoogleSignInClient
import com.srinidhi.kharch.data.auth.GoogleUser
import com.srinidhi.kharch.data.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInFlow: SignInFlow,
    private val signInClient: GoogleSignInClient,
    private val workspaceManager: WorkspaceManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentAuth()
    }

    fun checkCurrentAuth() {
        viewModelScope.launch {
            val user = signInClient.currentUser()
            if (user == null) {
                _uiState.value = AuthUiState.Unauthenticated()
                return@launch
            }
            val wid = workspaceManager.workspaceId()
            if (wid != null) {
                _uiState.value = AuthUiState.Authenticated(user, wid)
            } else {
                _uiState.value = AuthUiState.SetupNeeded(user)
            }
        }
    }

    fun startSignIn(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        _uiState.value = AuthUiState.Unauthenticated(isLoading = true)
        viewModelScope.launch {
            try {
                val result = signInFlow.startFirstRun(launcher)
                when (result) {
                    is SignInFlow.SignInFlowResult.Success -> {
                        _uiState.value = AuthUiState.Authenticated(result.user, result.workspaceId)
                    }
                    is SignInFlow.SignInFlowResult.NeedsSetup -> {
                        _uiState.value = AuthUiState.SetupNeeded(result.user)
                    }
                    is SignInFlow.SignInFlowResult.Cancelled -> {
                        _uiState.value = AuthUiState.Unauthenticated(isLoading = false)
                    }
                    is SignInFlow.SignInFlowResult.Failed -> {
                        _uiState.value = AuthUiState.Unauthenticated(
                            isLoading = false,
                            errorMessage = result.reason,
                        )
                    }
                    is SignInFlow.SignInFlowResult.AlreadySignedIn -> {
                        val user = signInClient.currentUser()
                        val wid = workspaceManager.workspaceId()
                        if (user != null && wid != null) {
                            _uiState.value = AuthUiState.Authenticated(user, wid)
                        } else if (user != null) {
                            _uiState.value = AuthUiState.SetupNeeded(user)
                        } else {
                            _uiState.value = AuthUiState.Unauthenticated()
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Unauthenticated(
                    isLoading = false,
                    errorMessage = e.message ?: "Sign-in initialization failed",
                )
            }
        }
    }

    fun handleSignInResult(intent: Intent) {
        _uiState.value = AuthUiState.Unauthenticated(isLoading = true)
        viewModelScope.launch {
            try {
                val result = signInFlow.handleSignInResult(intent)
                when (result) {
                    is SignInFlow.SignInFlowResult.Success -> {
                        _uiState.value = AuthUiState.Authenticated(result.user, result.workspaceId)
                    }
                    is SignInFlow.SignInFlowResult.NeedsSetup -> {
                        _uiState.value = AuthUiState.SetupNeeded(result.user)
                    }
                    is SignInFlow.SignInFlowResult.Cancelled -> {
                        _uiState.value = AuthUiState.Unauthenticated(isLoading = false)
                    }
                    is SignInFlow.SignInFlowResult.Failed -> {
                        _uiState.value = AuthUiState.Unauthenticated(
                            isLoading = false,
                            errorMessage = result.reason,
                        )
                    }
                    is SignInFlow.SignInFlowResult.AlreadySignedIn -> {
                        checkCurrentAuth()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Unauthenticated(
                    isLoading = false,
                    errorMessage = e.message ?: "Sign-in failed",
                )
            }
        }
    }

    fun completeSetup(user: GoogleUser, ledgerName: String) {
        _uiState.value = AuthUiState.SetupNeeded(
            user = user,
            defaultLedgerName = ledgerName,
            isLoading = true,
        )
        viewModelScope.launch {
            try {
                val wid = signInFlow.completeSetup(user.uid, ledgerName)
                _uiState.value = AuthUiState.Authenticated(user, wid)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.SetupNeeded(
                    user = user,
                    defaultLedgerName = ledgerName,
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create ledger",
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signInClient.signOut()
            _uiState.value = AuthUiState.Unauthenticated()
        }
    }
}
