package com.kd.anddirstat.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object StorageSnapshotScheduler {
    private const val WORK_NAME = "daily_storage_snapshot"

    fun scheduleDailySnapshot(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyStorageSnapshotWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
