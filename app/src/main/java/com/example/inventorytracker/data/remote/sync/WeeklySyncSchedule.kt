package com.example.inventorytracker.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.inventorytracker.InventoryApplication
import com.example.inventorytracker.data.repository.LanSyncController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object WeeklySyncSchedule {
    private const val UNIQUE_WORK_NAME = "inventory_weekly_lan_sync"

    fun enqueue(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        var firstRun = LocalDate.of(2026, 10, 5).atTime(LocalTime.MIDNIGHT).atZone(zone)
        if (firstRun.isBefore(now)) {
            val weeksToAdd = Duration.between(firstRun, now).toDays() / 7 + 1
            firstRun = firstRun.plusWeeks(weeksToAdd)
        }
        val initialDelay = Duration.between(now, firstRun).toMillis().coerceAtLeast(0L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WeeklyInventorySyncWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class WeeklyInventorySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? InventoryApplication ?: return Result.failure()
        if (app.lanSyncController.desktopAddress.isBlank() || app.lanSyncController.pairingToken.isBlank()) {
            Log.i(TAG, "Weekly sync skipped: no desktop is paired yet.")
            return Result.retry()
        }

        return when (val outcome = app.lanSyncController.syncNow()) {
            is LanSyncController.SyncOutcome.Success -> {
                Log.i(TAG, "Weekly sync complete: ${outcome.uploaded} uploaded, ${outcome.downloaded} downloaded.")
                Result.success()
            }
            is LanSyncController.SyncOutcome.Failure -> {
                Log.w(TAG, "Weekly sync will retry: ${outcome.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "WeeklyInventorySync"
    }
}
