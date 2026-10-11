package com.srinidhi.kharch.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The failure vocabulary the sync engine reacts to (§6.8).
 *
 * The classification is what decides lock-versus-retry, so getting it wrong either burns the
 * day's quota in a retry storm or gives up on a request that would have worked.
 */
class SyncErrorClassifierTest {

    @Test
    fun `quota exhaustion is recognised by name and by number`() {
        assertEquals(SyncErrorKind.RESOURCE_EXHAUSTED, SyncErrorClassifier.classify("resource-exhausted"))
        assertEquals(SyncErrorKind.RESOURCE_EXHAUSTED, SyncErrorClassifier.classify("RESOURCE_EXHAUSTED"))
        assertEquals(SyncErrorKind.RESOURCE_EXHAUSTED, SyncErrorClassifier.classify("8"))
        assertEquals(SyncErrorKind.RESOURCE_EXHAUSTED, SyncErrorClassifier.classify(429))
    }

    @Test
    fun `server failures are transient`() {
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify("unavailable"))
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify("deadline-exceeded"))
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify("internal"))
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify(500))
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify(503))
        assertEquals(SyncErrorKind.TRANSIENT, SyncErrorClassifier.classify(504))
    }

    @Test
    fun `auth and permission failures are permanent`() {
        assertEquals(SyncErrorKind.PERMANENT, SyncErrorClassifier.classify("unauthenticated"))
        assertEquals(SyncErrorKind.PERMANENT, SyncErrorClassifier.classify("permission-denied"))
        assertEquals(SyncErrorKind.PERMANENT, SyncErrorClassifier.classify("invalid-argument"))
        assertEquals(SyncErrorKind.PERMANENT, SyncErrorClassifier.classify(401))
        assertEquals(SyncErrorKind.PERMANENT, SyncErrorClassifier.classify(403))
    }

    @Test
    fun `anything unrecognised is retried rather than dropped`() {
        assertEquals(SyncErrorKind.UNKNOWN, SyncErrorClassifier.classify("something-new"))
        assertEquals(SyncErrorKind.UNKNOWN, SyncErrorClassifier.classify(418))
    }
}
