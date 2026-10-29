package com.aiusage.app.data.model

import kotlinx.serialization.Serializable

/** The five providers the app knows how to read quota from. */
@Serializable
enum class ProviderKind(
    val displayName: String,
    val shortName: String,
    val tagline: String,
    val keyHint: String,
) {
    ZHIPU(
        displayName = "智谱 GLM",
        shortName = "智谱",
        tagline = "GLM Coding Plan",
        keyHint = "在 BigModel / Z.ai 控制台创建 API Key",
    ),
    MINIMAX(
        displayName = "MiniMax",
        shortName = "MiniMax",
        tagline = "M Plan 订阅",
        keyHint = "使用 M Plan 订阅 Key（通常以 sk-cp- 开头）",
    ),
    OPENCODE_GO(
        displayName = "OpenCode Go",
        shortName = "OpenCode",
        tagline = "Go 订阅",
        keyHint = "在 opencode.ai/console 复制 API Key",
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortName = "DeepSeek",
        tagline = "开放平台",
        keyHint = "在 platform.deepseek.com 创建 API Key",
    ),
    KIMI(
        displayName = "Kimi",
        shortName = "Kimi",
        tagline = "Kimi Code / 开放平台",
        keyHint = "Kimi Code Key（sk-kimi-…）或 Moonshot 开放平台 Key",
    ),
}

@Serializable
enum class Region(val label: String) {
    AUTO("自动"),
    CN("国内"),
    GLOBAL("国际"),
}

@Serializable
data class ProviderConfig(
    val id: String,
    val kind: ProviderKind,
    val region: Region = Region.AUTO,
    val encryptedKey: String,
    val createdAt: Long,
)

@Serializable
enum class SlotState {
    /** The provider exposes this quota and the value is usable. */
    SUPPORTED,

    /** The provider or plan does not expose this quota. Rendered grey. */
    UNSUPPORTED,

    /** The quota exists but the latest request failed. Old data may remain. */
    ERROR,
}

@Serializable
data class QuotaSlot(
    val state: SlotState = SlotState.UNSUPPORTED,
    /** Remaining quota, 0..100. */
    val remainingPercent: Double? = null,
    val resetsAt: Long? = null,
    val note: String? = null,
) {
    val isSupported: Boolean get() = state != SlotState.UNSUPPORTED

    companion object {
        fun unsupported(note: String? = null) = QuotaSlot(SlotState.UNSUPPORTED, note = note)
        fun error(note: String? = null) = QuotaSlot(SlotState.ERROR, note = note)
        fun of(remainingPercent: Double, resetsAt: Long? = null, note: String? = null) = QuotaSlot(
            state = SlotState.SUPPORTED,
            remainingPercent = remainingPercent.coerceIn(0.0, 100.0),
            resetsAt = resetsAt,
            note = note,
        )
    }
}

@Serializable
data class BalanceSlot(
    val state: SlotState = SlotState.UNSUPPORTED,
    val amount: Double? = null,
    val currency: String = "",
    val unit: String = "",
    val note: String? = null,
) {
    val isSupported: Boolean get() = state != SlotState.UNSUPPORTED

    companion object {
        fun unsupported(note: String? = null) = BalanceSlot(SlotState.UNSUPPORTED, note = note)
        fun error(note: String? = null) = BalanceSlot(SlotState.ERROR, note = note)
        fun of(amount: Double, currency: String, note: String? = null) = BalanceSlot(
            state = SlotState.SUPPORTED,
            amount = amount,
            currency = currency,
            note = note,
        )

        fun credits(amount: Double, unit: String = "积分", note: String? = null) = BalanceSlot(
            state = SlotState.SUPPORTED,
            amount = amount,
            unit = unit,
            note = note,
        )
    }
}

@Serializable
enum class ErrorKind {
    AUTH,
    NETWORK,
    RATE_LIMIT,
    SERVER,
    PARSE,
    NO_PLAN,
    UNKNOWN,
}

@Serializable
data class UsageSnapshot(
    val providerId: String,
    val planName: String? = null,
    val fiveHour: QuotaSlot = QuotaSlot.unsupported(),
    val weekly: QuotaSlot = QuotaSlot.unsupported(),
    val monthly: QuotaSlot = QuotaSlot.unsupported(),
    val balance: BalanceSlot = BalanceSlot.unsupported(),
    /** Epoch millis of the last successful payload. 0 when never succeeded. */
    val fetchedAt: Long = 0L,
    /** Epoch millis of the latest attempt, successful or not. */
    val attemptedAt: Long = 0L,
    val error: String? = null,
    val errorKind: ErrorKind? = null,
    val note: String? = null,
) {
    val hasData: Boolean
        get() = fetchedAt > 0L && (fiveHour.state == SlotState.SUPPORTED ||
            weekly.state == SlotState.SUPPORTED ||
            monthly.state == SlotState.SUPPORTED ||
            balance.state == SlotState.SUPPORTED)

    val isStaleError: Boolean get() = error != null && hasData
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    DARK("深色"),
    LIGHT("浅色"),
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val refreshMinutes: Int = 30,
)

@Serializable
data class StoredData(
    val version: Int = 1,
    val providers: List<ProviderConfig> = emptyList(),
    val snapshots: Map<String, UsageSnapshot> = emptyMap(),
    val settings: AppSettings = AppSettings(),
)

data class AppState(
    val providers: List<ProviderConfig> = emptyList(),
    val snapshots: Map<String, UsageSnapshot> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val refreshing: Set<String> = emptySet(),
    val loaded: Boolean = false,
    val refreshingAll: Boolean = false,
) {
    fun snapshot(providerId: String): UsageSnapshot? = snapshots[providerId]

    val healthyProviderCount: Int
        get() = providers.count { provider ->
            val snapshot = snapshots[provider.id]
            snapshot?.hasData == true && snapshot.error == null
        }

    val attentionProviderCount: Int
        get() = providers.count { provider ->
            val snapshot = snapshots[provider.id] ?: return@count true
            snapshot.error != null || listOf(snapshot.fiveHour, snapshot.weekly, snapshot.monthly)
                .any { it.state == SlotState.SUPPORTED && (it.remainingPercent ?: 100.0) <= 20.0 }
        }

    /**
     * True when a fetch should be triggered automatically (app open / resume).
     * Fresh successful data is not stale; data older than [staleAfterMs] is;
     * and so is ANY provider whose last attempt failed more than
     * [errorRetryAfterMs] ago — so a background refresh that got blocked (and
     * stamped old data as "过期") heals itself the next time the app comes to
     * the foreground, where networking always works.
     */
    fun isStale(
        now: Long = System.currentTimeMillis(),
        staleAfterMs: Long = STALE_AFTER_MS,
        errorRetryAfterMs: Long = ERROR_RETRY_AFTER_MS,
    ): Boolean {
        if (providers.isEmpty()) return false
        return providers.any { provider ->
            val snapshot = snapshots[provider.id]
            when {
                snapshot == null -> true
                snapshot.error != null -> now - snapshot.attemptedAt > errorRetryAfterMs
                snapshot.fetchedAt == 0L -> true
                else -> now - snapshot.fetchedAt > staleAfterMs
            }
        }
    }

    companion object {
        const val STALE_AFTER_MS: Long = 5 * 60_000L
        const val ERROR_RETRY_AFTER_MS: Long = 60_000L
    }
}

/** What a provider client returns after a successful call. */
data class ProviderUsage(
    val planName: String? = null,
    val fiveHour: QuotaSlot = QuotaSlot.unsupported(),
    val weekly: QuotaSlot = QuotaSlot.unsupported(),
    val monthly: QuotaSlot = QuotaSlot.unsupported(),
    val balance: BalanceSlot = BalanceSlot.unsupported(),
    val note: String? = null,
)
