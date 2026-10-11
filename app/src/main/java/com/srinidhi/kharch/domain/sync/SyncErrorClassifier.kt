package com.srinidhi.kharch.domain.sync

/**
 * How the sync engine should react to a failure, independent of which SDK produced it.
 *
 * The domain layer must not name a Firebase type (§4: domain is pure Kotlin), so the remote
 * data source maps its exceptions onto this and the policy lives here where it can be tested
 * exhaustively.
 */
enum class SyncErrorKind {
    /**
     * The daily quota is spent. Spark hard-stops with no overage and no grace, so this is
     * never retried — retrying is precisely the runaway loop §6.8 exists to prevent.
     */
    RESOURCE_EXHAUSTED,

    /** 500 / 503 / unavailable / deadline-exceeded — worth another attempt, with backoff. */
    TRANSIENT,

    /** Unauthenticated, permission-denied, not-found, invalid-argument — retrying cannot help. */
    PERMANENT,

    /** Unrecognised: treated as transient so the operation is not silently dropped. */
    UNKNOWN,
}

/** Classifies SDK-specific failures into the domain's [SyncErrorKind] vocabulary. */
object SyncErrorClassifier {

    /**
     * Maps a gRPC/Firestore status code to a policy.
     *
     * `RESOURCE_EXHAUSTED` is matched both as a gRPC code name and as the numeric `8`, and
     * HTTP `429` is folded into it as well: on the free tier an over-limit response looks
     * exactly like exhaustion, and guessing "just a rate limit" would send the sync loop back
     * out to be stopped again.
     */
    fun classify(code: String): SyncErrorKind = when (code) {
        "resource-exhausted", "RESOURCE_EXHAUSTED", "8" -> SyncErrorKind.RESOURCE_EXHAUSTED
        "unavailable", "UNAVAILABLE", "14", "deadline-exceeded", "DEADLINE_EXCEEDED", "4",
        "internal", "INTERNAL", "13", "aborted", "ABORTED", "10" -> SyncErrorKind.TRANSIENT
        "unauthenticated", "permission-denied", "not-found", "invalid-argument" -> SyncErrorKind.PERMANENT
        else -> SyncErrorKind.UNKNOWN
    }

    /** Maps an HTTP status to a policy; used by the REST-shaped paths and by tests. */
    fun classify(status: Int): SyncErrorKind = when (status) {
        429 -> SyncErrorKind.RESOURCE_EXHAUSTED
        408, 500, 502, 503, 504 -> SyncErrorKind.TRANSIENT
        400, 401, 403, 404, 409 -> SyncErrorKind.PERMANENT
        else -> SyncErrorKind.UNKNOWN
    }
}
