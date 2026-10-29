package com.aiusage.app.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aiusage.app.AiUsageApp
import com.aiusage.app.R
import com.aiusage.app.data.store.WidgetCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short foreground service that performs the fetch behind the widget's
 * refresh button — the button's PendingIntent starts it directly
 * (Glance `actionStartService`, i.e. `PendingIntent.getForegroundService`).
 *
 * Why a foreground service at all: the refresh runs while the app has no
 * other active component, and a backgrounded process is exactly what Data
 * Saver / battery saver / OEM "后台联网" policies silently black-hole: every
 * socket times out and each provider gets stamped "数据过期" even though the
 * same fetch succeeds the moment the app is on screen (verified on an API 36
 * emulator with `cmd netpolicy set restrict-background true`). The
 * foreground-service process state is exempt from all of those blocks.
 * Widget taps are an explicit user interaction, which Android 12+ exempts
 * from the background foreground-service start restrictions — verified from
 * a live process, a killed process and even a fully cold start
 * ("Start proc … for service").
 *
 * The service stops itself as soon as the refresh completes; a full fetch is
 * bounded by the repository's 30 s timeout, far below the dataSync caps.
 */
class WidgetRefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The startForegroundService() contract requires this promptly; do it
        // before any suspend work so the system never kills us for being a
        // "background" FGS.
        startInForeground()
        if (com.aiusage.app.BuildConfig.DEBUG) {
            android.util.Log.d("AIUsageWidget", "refresh service started")
        }
        if (refreshJob?.isActive == true) return START_NOT_STICKY
        val app = applicationContext as? AiUsageApp ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        refreshJob = scope.launch {
            try {
                // Instant feedback: header shows "刷新中…" while we fetch.
                WidgetCacheStore.markRefreshing(this@WidgetRefreshService)
                runCatching { refreshWidgets(applicationContext) }
                // Same shared repository the app UI uses: one gate, one
                // DataStore, one widget-cache write path. Awaiting the gate
                // means a racing plain-background fetch (e.g. a straggling
                // WorkManager pass) can't leave the user's tap with its
                // failed result — we always run our own pass afterwards.
                runCatching { app.container.repository.refreshAllAwaitingGate() }
                runCatching { refreshWidgets(applicationContext) }
                if (com.aiusage.app.BuildConfig.DEBUG) {
                    android.util.Log.d("AIUsageWidget", "refresh service done")
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ safety net for dataSync; a normal refresh finishes in
        // seconds, but never linger past the system's cap.
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildRefreshNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "aiusage_widget_refresh"
        private const val NOTIFICATION_ID = 2001
    }
}

/** Low-key notification shown while a widget-triggered refresh runs. */
internal fun buildRefreshNotification(context: Context): Notification {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            WidgetRefreshService.CHANNEL_ID,
            "小组件刷新",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "桌面小组件手动刷新时短暂运行" },
    )
    return NotificationCompat.Builder(context, WidgetRefreshService.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_widget_refresh)
        .setContentTitle("AI 用量")
        .setContentText("正在刷新额度…")
        .setOngoing(true)
        .setSilent(true)
        .build()
}

/**
 * ForegroundInfo for expedited RefreshWorker runs (API < 31 promote expedited
 * work to a foreground service and need this from the worker).
 */
internal fun refreshForegroundInfo(context: Context): androidx.work.ForegroundInfo {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
        0
    }
    return androidx.work.ForegroundInfo(2001, buildRefreshNotification(context), type)
}
