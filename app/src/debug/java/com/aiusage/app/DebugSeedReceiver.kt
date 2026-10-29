package com.aiusage.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiusage.app.data.model.BalanceSlot
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.data.model.UsageSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.aiusage.app.widget.refreshWidgets

/**
 * Debug-only screenshot helper:
 * adb shell am broadcast -a com.aiusage.app.debug.SEED_DEMO \
 *   -n com.aiusage.app.debug/com.aiusage.app.DebugSeedReceiver
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AiUsageApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_UPDATE_WIDGET) {
                    val result = runCatching { refreshWidgets(app) }
                    Log.d("AIUsageDebug", "widget updateAll result=$result")
                    return@launch
                }
                if (intent.action == ACTION_RESET_METRICS) {
                    resetWidgetMetrics(app)
                    val result = runCatching { refreshWidgets(app) }
                    Log.d("AIUsageDebug", "widget metrics reset, updateAll result=$result")
                    return@launch
                }
                if (intent.action == ACTION_LIGHT || intent.action == ACTION_DARK || intent.action == ACTION_SYSTEM) {
                    val mode = when (intent.action) {
                        ACTION_LIGHT -> ThemeMode.LIGHT
                        ACTION_DARK -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                    app.container.repository.updateTheme(mode)
                    return@launch
                }
                if (intent.action == ACTION_CLEAR) {
                    app.container.repository.replaceForDemo(emptyList(), emptyMap())
                } else if (intent.action == ACTION_SEED_ONE || intent.action == ACTION_SEED_TWO) {
                    val now = System.currentTimeMillis()
                    val providers = listOf(
                        demo("zhipu", ProviderKind.ZHIPU, Region.CN),
                        demo("kimi", ProviderKind.KIMI, Region.AUTO),
                    )
                    val snapshots = mapOf(
                        "zhipu" to UsageSnapshot(
                            providerId = "zhipu",
                            planName = "GLM Coding Plan · Pro",
                            fiveHour = QuotaSlot.of(74.0, now + 2 * 3_600_000L),
                            weekly = QuotaSlot.of(61.0, now + 3 * 86_400_000L),
                            monthly = QuotaSlot.of(88.0, now + 18 * 86_400_000L),
                            balance = BalanceSlot.of(88.25, "CNY"),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                        "kimi" to UsageSnapshot(
                            providerId = "kimi",
                            planName = "Kimi Code · Intermediate",
                            fiveHour = QuotaSlot.of(100.0, now + 3 * 3_600_000L),
                            weekly = QuotaSlot.of(65.0, now + 4 * 86_400_000L),
                            monthly = QuotaSlot.of(18.0, now + 12 * 86_400_000L),
                            balance = BalanceSlot.credits(14_000.0, note = "1,000 积分 = $1"),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                    )
                    app.container.repository.replaceForDemo(
                        if (intent.action == ACTION_SEED_ONE) providers.take(1) else providers,
                        if (intent.action == ACTION_SEED_ONE) {
                            snapshots.filterKeys { it == "zhipu" }
                        } else {
                            snapshots
                        },
                    )
                } else {
                    val now = System.currentTimeMillis()
                    val providers = listOf(
                        demo("zhipu", ProviderKind.ZHIPU, Region.CN),
                        demo("minimax", ProviderKind.MINIMAX, Region.AUTO),
                        demo("opencode", ProviderKind.OPENCODE_GO, Region.AUTO),
                        demo("deepseek", ProviderKind.DEEPSEEK, Region.AUTO),
                        demo("kimi", ProviderKind.KIMI, Region.AUTO),
                    )
                    val snapshots = mapOf(
                        "zhipu" to UsageSnapshot(
                            providerId = "zhipu",
                            planName = "GLM Coding Plan · Pro",
                            fiveHour = QuotaSlot.of(74.0, now + 2 * 3_600_000L),
                            weekly = QuotaSlot.of(61.0, now + 3 * 86_400_000L),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                        "minimax" to UsageSnapshot(
                            providerId = "minimax",
                            planName = "M Plan Explore",
                            fiveHour = QuotaSlot.of(96.0, now + 4 * 3_600_000L),
                            weekly = QuotaSlot.of(99.0, now + 5 * 86_400_000L),
                            balance = BalanceSlot.credits(14_000.0, note = "1,000 积分 = $1"),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                        "opencode" to UsageSnapshot(
                            providerId = "opencode",
                            planName = "Go 订阅",
                            fiveHour = QuotaSlot.of(42.0, now + 90 * 60_000L),
                            weekly = QuotaSlot.of(68.0, now + 2 * 86_400_000L),
                            monthly = QuotaSlot.of(88.0, now + 18 * 86_400_000L),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                        "deepseek" to UsageSnapshot(
                            providerId = "deepseek",
                            planName = "按量付费",
                            balance = BalanceSlot.of(88.25, "CNY"),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                        "kimi" to UsageSnapshot(
                            providerId = "kimi",
                            planName = "Kimi Code · Intermediate",
                            fiveHour = QuotaSlot.of(100.0, now + 3 * 3_600_000L),
                            weekly = QuotaSlot.of(65.0, now + 4 * 86_400_000L),
                            monthly = QuotaSlot.of(18.0, now + 12 * 86_400_000L),
                            fetchedAt = now,
                            attemptedAt = now,
                        ),
                    )
                    app.container.repository.replaceForDemo(providers, snapshots)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun resetWidgetMetrics(context: Context) {
        context.getSharedPreferences("aiusage_widget_metrics", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun demo(id: String, kind: ProviderKind, region: Region) = ProviderConfig(
        id = id,
        kind = kind,
        region = region,
        encryptedKey = "",
        createdAt = System.currentTimeMillis(),
    )

    companion object {
        private const val ACTION_CLEAR = "com.aiusage.app.debug.CLEAR_DEMO"
        private const val ACTION_UPDATE_WIDGET = "com.aiusage.app.debug.UPDATE_WIDGET"
        private const val ACTION_LIGHT = "com.aiusage.app.debug.SET_LIGHT"
        private const val ACTION_DARK = "com.aiusage.app.debug.SET_DARK"
        private const val ACTION_SYSTEM = "com.aiusage.app.debug.SET_SYSTEM"
        private const val ACTION_SEED_ONE = "com.aiusage.app.debug.SEED_ONE"
        private const val ACTION_SEED_TWO = "com.aiusage.app.debug.SEED_TWO"
        private const val ACTION_RESET_METRICS = "com.aiusage.app.debug.RESET_METRICS"
    }
}
