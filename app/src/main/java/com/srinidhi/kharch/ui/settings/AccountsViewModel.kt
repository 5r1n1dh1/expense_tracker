package com.srinidhi.kharch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srinidhi.kharch.data.auth.GoogleSignInClient
import com.srinidhi.kharch.data.local.ExpenseDao
import com.srinidhi.kharch.data.workspace.RemovalOutcome
import com.srinidhi.kharch.data.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    private val signInClient: GoogleSignInClient,
    private val expenseDao: ExpenseDao,
) : ViewModel() {

    private val showChoiceSheetFlow = MutableStateFlow(false)
    private val removeBlockedReasonFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        workspaceManager.observeWorkspace(),
        workspaceManager.activeAuthUid,
        expenseDao.observeAllLive(),
        showChoiceSheetFlow,
        removeBlockedReasonFlow,
    ) { workspace, activeUid, expenses, showChoice, removeBlocked ->
        val user = signInClient.currentUser()
        val members = workspace?.memberUids?.map { uid ->
            val isActive = uid == activeUid || uid == user?.uid
            AccountMember(
                uid = uid,
                email = if (uid == user?.uid) user.email else null,
                displayName = if (uid == user?.uid) user.displayName else null,
                photoUri = if (uid == user?.uid) user.photoUri else null,
                isActive = isActive,
            )
        } ?: emptyList()

        AccountsUiState(
            ledgerName = workspace?.name ?: "Kharch",
            memberCount = workspace?.memberUids?.size ?: 1,
            expenseCount = expenses.size,
            lastSynced = "Synced",
            members = members,
            showChoiceSheet = showChoice,
            availableLedgers = 1,
            removeBlockedReason = removeBlocked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState(),
    )

    fun onLinkAccountClicked() {
        showChoiceSheetFlow.value = true
    }

    fun dismissChoice() {
        showChoiceSheetFlow.value = false
    }

    fun joinLedger() {
        dismissChoice()
    }

    fun startSeparateLedger() {
        dismissChoice()
        viewModelScope.launch {
            val user = signInClient.currentUser() ?: return@launch
            workspaceManager.createWorkspace(user.uid, "New Ledger")
        }
    }

    fun removeAccount(uid: String) {
        viewModelScope.launch {
            when (workspaceManager.removeAccount(uid)) {
                is RemovalOutcome.LastMember -> {
                    removeBlockedReasonFlow.value = "Cannot remove the only member of the ledger."
                }
                is RemovalOutcome.Removed -> {
                    removeBlockedReasonFlow.value = null
                }
                is RemovalOutcome.NoWorkspace -> {
                    removeBlockedReasonFlow.value = null
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signInClient.signOut()
        }
    }
}
