package com.srinidhi.kharch.ui.auth

import com.srinidhi.kharch.data.auth.GoogleUser

/** UI state for the authentication gate and setup process. */
sealed interface AuthUiState {
    data object Loading : AuthUiState

    data class Unauthenticated(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthUiState

    data class SetupNeeded(
        val user: GoogleUser,
        val defaultLedgerName: String = "Personal Expenses",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthUiState

    data class Authenticated(
        val user: GoogleUser,
        val workspaceId: String,
    ) : AuthUiState
}
