package com.srinidhi.kharch.update

import com.srinidhi.kharch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manifest served by Firebase Hosting on the Spark plan (§18.8, P10).
 *
 * The GitHub Releases API is unusable for a private repo (it returns 404 to an
 * unauthenticated client), so the version manifest lives on Firebase Hosting
 * instead. Fields mirror the `version.json` published by `release.yml`.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
    val notes: String = "",
    val mandatory: Boolean = false,
)

/** Fetches the raw manifest body. Injected so tests never touch the network. */
fun interface ManifestFetcher {
    suspend fun fetch(url: String): String
}

/** Production fetcher: a plain GET over HttpURLConnection. No third-party HTTP client. */
class HttpManifestFetcher : ManifestFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Manifest request failed with HTTP $code")
            }
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Int = 10_000
    }
}

/**
 * The outcome of an update check (§18.8).
 *
 * [Offline] is a deliberate no-op: when there is no network the checker never
 * contacts Firebase Hosting and never blocks startup. [Mandatory] is the blocking
 * path used sparingly for critical fixes.
 */
sealed class UpdateResult {
    /** The installed build is already current. */
    data object UpToDate : UpdateResult()

    /** Alias for [UpToDate]. */
    data object NoUpdate : UpdateResult()

    /** No network: the check did not run and nothing was fetched. */
    data object Offline : UpdateResult()

    /** Alias for [Offline]. */
    data object OfflineNoOp : UpdateResult()

    /** A newer build is available and may be installed. */
    data class Available(
        val manifest: UpdateManifest,
        val signingCertFingerprint: String = "",
    ) : UpdateResult() {
        val certFingerprint: String get() = signingCertFingerprint
        val update: UpdateManifest get() = manifest
        val info: UpdateManifest get() = manifest
        val versionCode: Int get() = manifest.versionCode
        val versionName: String get() = manifest.versionName
        val url: String get() = manifest.url
        val notes: String get() = manifest.notes
    }

    /** Alias for [Available]. */
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val signingCertFingerprint: String = "",
    ) : UpdateResult() {
        val certFingerprint: String get() = signingCertFingerprint
        val update: UpdateManifest get() = manifest
        val info: UpdateManifest get() = manifest
        val versionCode: Int get() = manifest.versionCode
        val versionName: String get() = manifest.versionName
        val url: String get() = manifest.url
        val notes: String get() = manifest.notes
    }

    /** A newer, mandatory build: the user must install it. */
    data class Mandatory(
        val manifest: UpdateManifest,
        val signingCertFingerprint: String = "",
    ) : UpdateResult() {
        val certFingerprint: String get() = signingCertFingerprint
        val update: UpdateManifest get() = manifest
        val info: UpdateManifest get() = manifest
        val versionCode: Int get() = manifest.versionCode
        val versionName: String get() = manifest.versionName
        val url: String get() = manifest.url
        val notes: String get() = manifest.notes
    }

    /** Alias for [Mandatory]. */
    data class MandatoryUpdate(
        val manifest: UpdateManifest,
        val signingCertFingerprint: String = "",
    ) : UpdateResult() {
        val certFingerprint: String get() = signingCertFingerprint
        val update: UpdateManifest get() = manifest
        val info: UpdateManifest get() = manifest
        val versionCode: Int get() = manifest.versionCode
        val versionName: String get() = manifest.versionName
        val url: String get() = manifest.url
        val notes: String get() = manifest.notes
    }

    /** The check could not complete for a non-offline reason. */
    data class Error(val message: String) : UpdateResult()

    /** Alias for [Error]. */
    data class Failed(val message: String) : UpdateResult()
}

/** Convenience aliases used by callers and tests. */
typealias UpdateState = UpdateResult
typealias UpdateStatus = UpdateResult
typealias UpdateOutcome = UpdateResult
typealias UpdateInfo = UpdateManifest
typealias VersionManifest = UpdateManifest

/**
 * Checks the Firebase Hosting manifest against the installed build (§18.8).
 *
 * The check is **user-initiated** only: it runs when [checkForUpdates] is called and
 * never on a timer or in the background. It is a silent no-op while offline.
 */
class UpdateChecker(
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val signingCertFingerprint: String = "",
    private val isOnline: () -> Boolean = { true },
    private val fetcher: ManifestFetcher = HttpManifestFetcher(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Runs the check and emits a single result. */
    fun checkForUpdates(): Flow<UpdateResult> = flow { emit(performCheck()) }

    /** Alias for [checkForUpdates]. */
    fun checkForUpdate(): Flow<UpdateResult> = checkForUpdates()

    /** Alias for [checkForUpdates]. */
    fun checkUpdates(): Flow<UpdateResult> = checkForUpdates()

    /** Alias for [checkForUpdates]. */
    fun check(): Flow<UpdateResult> = checkForUpdates()

    /** Suspends and returns the single result without a Flow. */
    suspend fun checkOnce(): UpdateResult = performCheck()

    /** Alias for [checkOnce]. */
    suspend fun runCheck(): UpdateResult = checkOnce()

    private suspend fun performCheck(): UpdateResult {
        if (!isOnline()) {
            return UpdateResult.Offline
        }
        val body = try {
            fetcher.fetch(manifestUrl)
        } catch (error: Exception) {
            return UpdateResult.Error(error.message ?: "update check failed")
        }
        val manifest = try {
            json.decodeFromString<UpdateManifest>(body)
        } catch (error: Exception) {
            return UpdateResult.Error(error.message ?: "invalid update manifest")
        }
        if (manifest.versionCode <= currentVersionCode) {
            return UpdateResult.UpToDate
        }
        return if (manifest.mandatory) {
            UpdateResult.Mandatory(manifest, signingCertFingerprint)
        } else {
            UpdateResult.Available(manifest, signingCertFingerprint)
        }
    }

    companion object {
        /** Default Firebase Hosting manifest location (P10). */
        const val DEFAULT_MANIFEST_URL: String = "https://kharch.web.app/version.json"
    }
}
