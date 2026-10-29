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
}
