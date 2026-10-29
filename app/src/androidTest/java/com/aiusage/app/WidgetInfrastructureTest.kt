package com.aiusage.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.UsageSnapshot
import com.aiusage.app.data.store.WidgetCacheStore
import com.aiusage.app.widget.UsageWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetInfrastructureTest {
    @Test
    fun widgetCacheRoundTripsWithoutSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ProviderConfig(
            id = "widget-test",
            kind = ProviderKind.OPENCODE_GO,
            region = Region.AUTO,
            encryptedKey = "SECRET_NOT_FOR_WIDGET",
            createdAt = 1,
        )
        val snapshot = UsageSnapshot(
            providerId = provider.id,
            planName = "OpenCode Go",
            fetchedAt = 123456789L,
            attemptedAt = 123456789L,
            fiveHour = com.aiusage.app.data.model.QuotaSlot.of(70.0),
        )
        WidgetCacheStore.write(context, AppState(providers = listOf(provider), snapshots = mapOf(provider.id to snapshot)))
        val cache = WidgetCacheStore.read(context)
        assertEquals(1, cache.providers.size)
        assertEquals("OpenCode", cache.providers[0].name)
        assertEquals(70.0, cache.providers[0].fiveHour!!, 0.001)
        WidgetCacheStore.clear(context)
    }

    @Test
    fun widgetReceiverIsExportedAndRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, UsageWidgetReceiver::class.java)
        val info = context.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
        assertTrue(info.exported)
        assertTrue(info.metaData.containsKey("android.appwidget.provider"))
    }

    @Test
    fun widgetRefreshServiceIsDeclaredAsNonExportedDataSyncFgs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, com.aiusage.app.widget.WidgetRefreshService::class.java)
        val info = context.packageManager.getServiceInfo(component, 0)
        assertFalse(info.exported)
        assertTrue(
            "foregroundServiceType should declare dataSync, was ${info.foregroundServiceType}",
            info.foregroundServiceType and android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0,
        )
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
        assertNotNull(permissions)
        assertTrue(android.Manifest.permission.FOREGROUND_SERVICE in permissions!!)
        assertTrue(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC in permissions)
    }

    @Test
    fun widgetRefreshServiceRunsAndStopsItself() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Start it the same way the widget's PendingIntent does. The app is
        // foregrounded by the instrumentation, so the FGS start is allowed.
        val intent = android.content.Intent(
            context,
            com.aiusage.app.widget.WidgetRefreshService::class.java,
        )
        androidx.core.content.ContextCompat.startForegroundService(context, intent)

        // startInForeground() creates the channel synchronously in
        // onStartCommand — its presence proves the service really started.
        val deadline = System.currentTimeMillis() + 10_000L
        var channelSeen = false
        while (System.currentTimeMillis() < deadline && !channelSeen) {
            android.os.SystemClock.sleep(250)
            channelSeen = isRefreshChannelPresent(context)
        }
        assertTrue("WidgetRefreshService should create its notification channel", channelSeen)

        // And it must stop itself shortly after the refresh pass finishes.
        val stopDeadline = System.currentTimeMillis() + 20_000L
        var stopped = false
        while (System.currentTimeMillis() < stopDeadline && !stopped) {
            android.os.SystemClock.sleep(500)
            stopped = !isRefreshServiceRunning(context)
        }
        assertTrue("WidgetRefreshService should stop itself after the refresh", stopped)
    }

    private fun isRefreshChannelPresent(context: android.content.Context): Boolean {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        return manager.notificationChannels.any {
            it.id == com.aiusage.app.widget.WidgetRefreshService.CHANNEL_ID
        }
    }

    private fun isRefreshServiceRunning(context: android.content.Context): Boolean {
        @Suppress("DEPRECATION")
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == com.aiusage.app.widget.WidgetRefreshService::class.java.name
        }
    }
}
