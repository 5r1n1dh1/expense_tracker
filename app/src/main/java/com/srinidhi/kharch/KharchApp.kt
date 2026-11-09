package com.srinidhi.kharch

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.srinidhi.kharch.core.startup.StartupCrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Kharch — manual-entry expense tracker with a shared Google-account ledger.
 *
 * P1 (§18.1): the app sends nothing anywhere except Firestore sync to the user's own
 * Firebase project, and the optional user-initiated update check. No Crashlytics, no
 * Analytics, no Performance Monitoring.
 *
 * Launch hardening for Android 15/16 + One UI 9 (Galaxy S23):
 * - Implements [Configuration.Provider] so WorkManager is initialised through
 *   Hilt's [HiltWorkerFactory] instead of the removed default initializer.
 *   Without this, `WorkManager.getInstance()` throws on first enqueue.
 * - Installs [StartupCrashReporter] before anything else. It persists the
 *   stack trace to a private file on uncaught exceptions so a "doesn't open"
 *   report can be read on-device — nothing leaves the device (P1).
 */
@HiltAndroidApp
class KharchApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        StartupCrashReporter.install(this)
        // WorkManager is initialised lazily via Configuration.Provider.
        // Do NOT call WorkManager.getInstance() here.
    }
}
