package com.srinidhi.kharch.update

import app.cash.turbine.test
import com.srinidhi.kharch.update.UpdateChecker
import com.srinidhi.kharch.update.UpdateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A manifest source the test drives by hand. */
class FakeManifestFetcher(
    private val body: String? = null,
    private val error: Throwable? = null,
) : ManifestFetcher {
    var fetchCalls: Int = 0
        private set

    override suspend fun fetch(url: String): String {
        fetchCalls += 1
        error?.let { throw it }
        return body ?: error("no body configured")
    }
}

class UpdateCheckerTest {

    private fun manifest(versionCode: Int, mandatory: Boolean = false): String =
        "{\"versionCode\":$versionCode,\"versionName\":\"1.$versionCode.0\"," +
            "\"url\":\"https://example.com/app.apk\",\"notes\":\"notes\"," +
            "\"mandatory\":$mandatory}"

    @Test
    fun `a higher version code is reported as available`() = runTest {
        val fetcher = FakeManifestFetcher(body = manifest(versionCode = 5))
        val checker = UpdateChecker(
            currentVersionCode = 3,
            isOnline = { true },
            fetcher = fetcher,
            signingCertFingerprint = "AA:BB",
        )

        checker.checkForUpdates().test {
            val result = awaitItem()
            assertTrue(result is UpdateResult.Available)
            val available = result as UpdateResult.Available
            assertEquals(5, available.versionCode)
            assertEquals("AA:BB", available.certFingerprint)
            awaitComplete()
        }
        assertEquals(1, fetcher.fetchCalls)
    }

    @Test
    fun `an equal or lower version code means up to date`() = runTest {
        val fetcher = FakeManifestFetcher(body = manifest(versionCode = 3))
        val checker = UpdateChecker(
            currentVersionCode = 3,
            isOnline = { true },
            fetcher = fetcher,
        )

        checker.checkForUpdates().test {
            assertEquals(UpdateResult.UpToDate, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `mandatory updates take the blocking path`() = runTest {
        val fetcher = FakeManifestFetcher(body = manifest(versionCode = 7, mandatory = true))
        val checker = UpdateChecker(
            currentVersionCode = 3,
            isOnline = { true },
            fetcher = fetcher,
        )

        checker.checkForUpdates().test {
            val result = awaitItem()
            assertTrue(result is UpdateResult.Mandatory)
            val mandatory = result as UpdateResult.Mandatory
            assertEquals(7, mandatory.versionCode)
            awaitComplete()
        }
    }

    @Test
    fun `offline is a silent no op and never fetches`() = runTest {
        val fetcher = FakeManifestFetcher(body = manifest(versionCode = 99))
        val checker = UpdateChecker(
            currentVersionCode = 1,
            isOnline = { false },
            fetcher = fetcher,
        )

        checker.checkForUpdates().test {
            assertEquals(UpdateResult.Offline, awaitItem())
            awaitComplete()
        }
        assertEquals(0, fetcher.fetchCalls)
    }

    @Test
    fun `a fetch failure surfaces as an error result`() = runTest {
        val fetcher = FakeManifestFetcher(error = IllegalStateException("boom"))
        val checker = UpdateChecker(
            currentVersionCode = 1,
            isOnline = { true },
            fetcher = fetcher,
        )

        checker.checkForUpdates().test {
            val result = awaitItem()
            assertTrue(result is UpdateResult.Error)
            awaitComplete()
        }
    }

    @Test
    fun `alias flows behave identically`() = runTest {
        val fetcher = FakeManifestFetcher(body = manifest(versionCode = 2))
        val checker = UpdateChecker(
            currentVersionCode = 1,
            isOnline = { true },
            fetcher = fetcher,
        )

        checker.check().test {
            assertTrue(awaitItem() is UpdateResult.Available)
            awaitComplete()
        }
    }
}
