package com.srinidhi.kharch.data.remote

import com.srinidhi.kharch.domain.sync.SyncErrorKind

/**
 * A remote failure already classified into the domain's vocabulary.
 *
 * Classification happens here, at the Firebase boundary, because `domain` must not name a
 * Firebase type (plan §4): the coordinator decides policy from [kind] and never learns which
 * SDK produced the failure.
 */
class SyncException(val kind: SyncErrorKind, message: String?) : Exception(message)

/**
 * Maps any throwable onto something the coordinator can act on.
 *
 * [kotlinx.coroutines.CancellationException] is re-thrown untouched: cancelling a sync is not a
 * remote fault, and turning it into a [SyncException] would make a cancelled coroutine look
 * like a network failure that deserves a retry.
 */
fun Throwable.toSyncException(): Throwable = when (this) {
    is SyncException -> this
    is kotlinx.coroutines.CancellationException -> this
    is com.google.firebase.FirebaseException -> SyncException(classifyFirestore(this), message)
    else -> SyncException(SyncErrorKind.UNKNOWN, message)
}

/**
 * Firestore's own status codes, mapped onto [SyncErrorKind].
 *
 * `RESOURCE_EXHAUSTED` is the one that matters (plan §6.8): Spark has no overage and no grace,
 * so the API stops answering until midnight Pacific. Recognising it here is what lets the
 * coordinator refuse to retry instead of hammering a quota that is already gone.
 */
private fun classifyFirestore(exception: com.google.firebase.FirebaseException): SyncErrorKind {
    val code = (exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
        ?: return SyncErrorKind.TRANSIENT
    return FirestoreCodePolicy.of(code)
}

/** The finite mapping from Firestore's `Code` enum to the domain's [SyncErrorKind]. */
private object FirestoreCodePolicy {

    fun of(code: FirestoreCode): SyncErrorKind = mapped[code] ?: SyncErrorKind.UNKNOWN

    private val mapped: Map<FirestoreCode, SyncErrorKind> = mapOf(
        FirestoreCode.RESOURCE_EXHAUSTED to SyncErrorKind.RESOURCE_EXHAUSTED,
        FirestoreCode.UNAVAILABLE to SyncErrorKind.TRANSIENT,
        FirestoreCode.INTERNAL to SyncErrorKind.TRANSIENT,
        FirestoreCode.ABORTED to SyncErrorKind.TRANSIENT,
        FirestoreCode.DEADLINE_EXCEEDED to SyncErrorKind.TRANSIENT,
        FirestoreCode.PERMISSION_DENIED to SyncErrorKind.PERMANENT,
        FirestoreCode.UNAUTHENTICATED to SyncErrorKind.PERMANENT,
        FirestoreCode.NOT_FOUND to SyncErrorKind.PERMANENT,
        FirestoreCode.INVALID_ARGUMENT to SyncErrorKind.PERMANENT,
    )
}

/** Alias so the table above reads as policy rather than as a fully-qualified type. */
private typealias FirestoreCode = com.google.firebase.firestore.FirebaseFirestoreException.Code

/** The workspace document `name` / `schemaVersion` fallbacks for a partially-written document. */
internal object WorkspaceDefaults {
    const val NAME: String = "Kharch"

    const val SCHEMA_VERSION: Int = 1
}

/** The workspace document does not exist, so its membership cannot be changed. */
class MissingWorkspaceException(workspaceId: String) :
    Exception("Workspace $workspaceId does not exist in Firestore")

/** The last remaining member cannot be removed (plan §5.3, §6.2). */
class LastMemberException(uid: String) :
    Exception("Cannot remove $uid: it is the last member of the ledger")
