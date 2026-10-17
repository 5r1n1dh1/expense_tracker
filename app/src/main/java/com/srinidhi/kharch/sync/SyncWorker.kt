package com.srinidhi.kharch.sync

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.srinidhi.kharch.data.prefs.SettingsStore
import com.srinidhi.kharch.domain.sync.QuotaGovernor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** WorkManager periodic (15 min) and expedited sync worker (§6.3, §3.1). */
class SyncWorker @Inject constructor(
    appContext: android.content.Context,
    params: WorkerParameters,
    private val coordinator: SyncCoordinator,
    private val settingsStore: SettingsStore,
    private val governor: QuotaGovernor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val activeUid = runBlocking { settingsStore.activeAuthUid.first() }
        if (activeUid == null) return Result.success()
        if (governor.currentStatus().isLocked) return Result.retry()
        return try {
            coordinator.requestSync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "kharch_sync_periodic"
        private const val EXPEDITED_TAG = "kharch_sync_expedited"

        fun enqueueExpedited(manager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(EXPEDITED_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            manager.enqueue(request)
        }

        fun enqueuePeriodic(manager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
