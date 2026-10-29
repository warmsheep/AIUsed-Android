package com.aiusage.app.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aiusage.app.AiUsageApp
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val PERIODIC_WORK = "aiusage-periodic-refresh"
    private const val IMMEDIATE_WORK = "aiusage-immediate-refresh"

    fun schedule(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.coerceIn(15, 240).toLong()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun refreshNow(context: Context) {
        // Durable one-shot fallback. The widget's refresh button now starts
        // WidgetRefreshService directly (network-safe in the background), so
        // this is kept for manual/debug use and as a safety net.
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? AiUsageApp ?: return Result.success()
        return runCatching {
            // Watchdog: the widget action already refreshes directly; only
            // fetch here when the data really is stale (e.g. process died).
            app.container.repository.refreshIfOlderThan(120_000L)
            runCatching { com.aiusage.app.widget.refreshWidgets(applicationContext) }
            Result.success()
        }.getOrElse { Result.success() }
    }

    /**
     * Required when this worker runs expedited on API < 31: WorkManager
     * promotes expedited work to a foreground service there and fails the
     * request at dispatch if the worker cannot supply one.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        com.aiusage.app.widget.refreshForegroundInfo(applicationContext)
}
