package com.srinidhi.kharch.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The four things the sync pill says (plan §6.7).
 *
 * Deliberately a data type and not a `String`. The UI needs a *state*, not a sentence — the
 * same `Synced` state renders as "Synced 2m ago" in a pill and "last synced 2 minutes ago" in
 * a sheet, and a status pill that is handed a pre-formatted string cannot do localisation,
 * relative time, or an accessibility label that says something different from what is on
 * screen.
 */
sealed interface SyncStatus {

    /** A sync is running right now. [pending] rows are still waiting to go. */
    data class Syncing(val pending: Int) : SyncStatus

    /** Quiescent and everything has landed. [atMillis] is the last successful sync. */
    data class Synced(val pending: Int, val atMillis: Long) : SyncStatus

    /** Not connected, or the last attempt failed transiently. [pending] rows are queued. */
    data class Offline(val pending: Int) : SyncStatus

    /**
     * The Spark daily quota is gone (§6.8).
     *
     * Nothing is retried until the reset, because retrying is the runaway loop the governor
     * exists to stop. [message] names the exact reset time, which is the difference between
     * an actionable status and "try again later".
     */
    data class QuotaLocked(val pending: Int, val message: String, val resetAtMillis: Long) : SyncStatus
}

/** The internal snapshot [SyncStatus] is derived from. */
data class SyncSnapshot(
    val syncing: Boolean = false,
    val pending: Int = 0,
    val lastSyncAtMillis: Long = 0L,
    val offline: Boolean = false,
    val quotaMessage: String? = null,
    val resetAtMillis: Long = 0L,
) {
    fun toStatus(): SyncStatus = when {
        quotaMessage != null -> SyncStatus.QuotaLocked(pending, quotaMessage, resetAtMillis)
        syncing -> SyncStatus.Syncing(pending)
        offline -> SyncStatus.Offline(pending)
        lastSyncAtMillis > 0L -> SyncStatus.Synced(pending, lastSyncAtMillis)
        else -> SyncStatus.Offline(pending)
    }
}

/**
 * The process-wide holder for the current [SyncSnapshot].
 *
 * A singleton so the coordinator, the worker and any UI surface read the same value: a status
 * that two components disagree about is worse than no status at all. The coordinator is the
 * only writer.
 */
@Singleton
class SyncStateHolder @Inject constructor() {
    private val state = MutableStateFlow(SyncSnapshot())

    /** The current status, as a [Flow] for the UI to collect. */
    val status: Flow<SyncStatus> = state.map { it.toStatus() }

    /** The current status as a [StateFlow], for consumers that need an immediate value. */
    val statusState: StateFlow<SyncStatus> = MutableStateFlow(state.value.toStatus()).also { mirror ->
        state.map { it.toStatus() }.onEach(mirror::emit).launchIn(scope = SyncStateScope)
    }

    internal fun update(transform: (SyncSnapshot) -> SyncSnapshot) {
        state.update(transform)
    }
}
