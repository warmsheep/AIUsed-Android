package com.aiusage.app.data.store

import android.content.Context
import androidx.core.content.edit
import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.BalanceSlot
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.util.formatCredits
import com.aiusage.app.util.formatMoney
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WidgetProvider(
    val id: String,
    val kind: ProviderKind,
    val name: String,
    val planName: String? = null,
    val fiveHour: Double? = null,
    val weekly: Double? = null,
    val monthly: Double? = null,
    val balance: String? = null,
    /** Numeric money balance for the header total ("总额"); credits stay null. */
    val balanceValue: Double? = null,
    val balanceCurrency: String? = null,
    val hasError: Boolean = false,
)

@Serializable
data class WidgetCache(
    val updatedAt: Long = 0L,
    val providers: List<WidgetProvider> = emptyList(),
    /** Mirrors the app setting so the widget can match the app and system theme. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** When a refresh started (0 = idle); the widget shows "刷新中…" briefly. */
    val refreshingAt: Long = 0L,
)

/**
 * A tiny, non-sensitive cache for the launcher process. API keys never enter
 * this file; it only contains the numbers already rendered in the app.
 */
object WidgetCacheStore {
    private const val PREFS = "aiusage_widget"
    private const val KEY_CACHE = "cache_v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun write(context: Context, state: AppState) {
        // Include EVERY provider the app knows about (even one that has not
        // been fetched yet) so the widget shows the same provider set as the
        // app; missing numbers simply render as "—".
        val providers = state.providers.map { provider ->
            val snapshot = state.snapshots[provider.id]
            val balanceSlot = snapshot?.balance?.takeIf { it.state == SlotState.SUPPORTED }
            WidgetProvider(
                id = provider.id,
                kind = provider.kind,
                name = provider.kind.shortName,
                planName = snapshot?.planName,
                fiveHour = snapshot?.fiveHour?.takeIf { it.state == SlotState.SUPPORTED }?.remainingPercent,
                weekly = snapshot?.weekly?.takeIf { it.state == SlotState.SUPPORTED }?.remainingPercent,
                monthly = snapshot?.monthly?.takeIf { it.state == SlotState.SUPPORTED }?.remainingPercent,
                balance = balanceSlot?.let(::balanceLabel),
                balanceValue = balanceSlot?.amount?.takeIf { balanceSlot.unit.isBlank() },
                balanceCurrency = balanceSlot?.currency?.takeIf { balanceSlot.unit.isBlank() && it.isNotBlank() },
                hasError = snapshot?.error != null,
            )
        }
        val refreshing = state.refreshingAll || state.refreshing.isNotEmpty()
        val cache = WidgetCache(
            updatedAt = state.snapshots.values.maxOfOrNull { it.fetchedAt } ?: 0L,
            providers = providers,
            themeMode = state.settings.themeMode,
            refreshingAt = if (refreshing) System.currentTimeMillis() else 0L,
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_CACHE, json.encodeToString(cache)) }
    }

    /** Flag a widget-triggered refresh so the header shows "刷新中…" at once. */
    fun markRefreshing(context: Context) {
        val current = read(context)
        val updated = current.copy(refreshingAt = System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_CACHE, json.encodeToString(updated)) }
    }

    fun read(context: Context): WidgetCache {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, null)
            ?: return WidgetCache()
        return runCatching { json.decodeFromString<WidgetCache>(raw) }.getOrDefault(WidgetCache())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun balanceLabel(balance: BalanceSlot): String? {
        val amount = balance.amount ?: return null
        return if (balance.unit.isNotBlank()) {
            formatCredits(amount, balance.unit)
        } else {
            formatMoney(amount, balance.currency)
        }
    }
}
