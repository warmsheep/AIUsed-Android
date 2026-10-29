package com.aiusage.app.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiusage.app.data.crypto.SecretCipher
import com.aiusage.app.data.model.AppSettings
import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.BalanceSlot
import com.aiusage.app.data.model.ErrorKind
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.ProviderUsage
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.data.model.StoredData
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.data.model.UsageSnapshot
import com.aiusage.app.data.provider.ProviderClients
import com.aiusage.app.data.provider.ProviderException
import com.aiusage.app.data.work.RefreshScheduler
import com.aiusage.app.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "aiusage_data")

class AppRepository internal constructor(
    private val context: Context,
    private val clients: ProviderClients = ProviderClients(installId = InstallId.get(context)),
    private val cipher: SecretCipher = SecretCipher(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val stateMutex = Mutex()
    private val refreshGate = Mutex()

    private val initJob: Job = scope.launch { load() }

    init {
        scope.launch {
            initJob.join()
            scheduleRefresh()
            if (isStale()) {
                refreshAll()
            }
            WidgetCacheStore.write(context, _state.value)
            runCatching { refreshWidgets(context) }
        }
    }

    suspend fun awaitLoaded() {
        initJob.join()
    }

    suspend fun refreshAll() {
        initJob.join()
        if (!refreshGate.tryLock()) return
        try {
            val providers = _state.value.providers
            if (providers.isEmpty()) return
            _state.update {
                it.copy(
                    refreshing = providers.map { provider -> provider.id }.toSet(),
                    refreshingAll = true,
                )
            }
            val results = coroutineScope {
                providers.map { config ->
                    async(Dispatchers.IO) { config.id to fetchSnapshot(config) }
                }.awaitAll()
            }.toMap()
            stateMutex.withLock {
                val current = _state.value
                val activeIds = current.providers.mapTo(HashSet()) { it.id }
                val merged = current.snapshots.toMutableMap()
                results.forEach { (id, snapshot) ->
                    val existing = merged[id]
                    if (id in activeIds && (existing == null || snapshot.attemptedAt >= existing.fetchedAt)) {
                        merged[id] = snapshot
                    }
                }
                val next = current.copy(
                    snapshots = merged,
                    refreshing = emptySet(),
                    refreshingAll = false,
                )
                _state.value = next
                persist(next)
            }
            notifyWidget()
        } finally {
            _state.update { it.copy(refreshing = emptySet(), refreshingAll = false) }
            refreshGate.unlock()
        }
    }

    suspend fun refreshIfStale() {
        initJob.join()
        if (isStale()) refreshAll()
    }

    /** Watchdog used by the widget worker: skip when data is very fresh. */
    suspend fun refreshIfOlderThan(thresholdMs: Long) {
        initJob.join()
        val newest = _state.value.snapshots.values.maxOfOrNull { it.attemptedAt } ?: 0L
        if (System.currentTimeMillis() - newest < thresholdMs) return
        refreshAll()
    }

    suspend fun refreshProvider(providerId: String) {
        initJob.join()
        val config = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        _state.update { it.copy(refreshing = it.refreshing + providerId) }
        try {
            val snapshot = fetchSnapshot(config)
            stateMutex.withLock {
                val current = _state.value
                if (current.providers.none { it.id == providerId }) {
                    _state.value = current.copy(refreshing = current.refreshing - providerId)
                    return@withLock
                }
                val existing = current.snapshots[providerId]
                val snapshots = if (existing == null || snapshot.attemptedAt >= existing.fetchedAt) {
                    current.snapshots + (providerId to snapshot)
                } else {
                    current.snapshots
                }
                val next = current.copy(
                    snapshots = snapshots,
                    refreshing = current.refreshing - providerId,
                )
                _state.value = next
                persist(next)
            }
            notifyWidget()
        } finally {
            _state.update { it.copy(refreshing = it.refreshing - providerId) }
        }
    }

    suspend fun addProvider(kind: ProviderKind, rawKey: String, region: Region): Result<Unit> {
        initJob.join()
        val key = normalizeKey(rawKey)
        if (key.length < 8) {
            return Result.failure(ProviderException(ErrorKind.AUTH, "请输入有效的 API Key"))
        }
        if (_state.value.providers.any { it.kind == kind }) {
            return Result.failure(ProviderException(ErrorKind.UNKNOWN, "该供应商已添加，可直接在卡片中更新密钥"))
        }

        val now = System.currentTimeMillis()
        val draft = ProviderConfig(
            id = UUID.randomUUID().toString(),
            kind = kind,
            region = region,
            encryptedKey = "",
            createdAt = now,
        )
        val usage = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { clients.fetch(draft, key) }
                ?: return Result.failure(ProviderException(ErrorKind.NETWORK, "连接超时，请检查网络后重试"))
        } catch (error: ProviderException) {
            return Result.failure(error)
        } catch (error: Exception) {
            return Result.failure(ProviderException(ErrorKind.UNKNOWN, "连接失败，请稍后重试", error))
        }

        val encrypted = runCatching { cipher.encrypt(key) }.getOrElse { error ->
            return Result.failure(ProviderException(ErrorKind.UNKNOWN, "密钥保存失败，请重试", error))
        }
        val config = draft.copy(encryptedKey = encrypted)
        val snapshot = usage.toSnapshot(config.id, now)

        stateMutex.withLock {
            if (_state.value.providers.any { it.kind == kind }) {
                return Result.failure(ProviderException(ErrorKind.UNKNOWN, "该供应商已添加"))
            }
            val current = _state.value
            val next = current.copy(
                providers = current.providers + config,
                snapshots = current.snapshots + (config.id to snapshot),
            )
            _state.value = next
            persist(next)
        }
        notifyWidget()
        return Result.success(Unit)
    }

    suspend fun updateProvider(
        providerId: String,
        rawKey: String?,
        region: Region,
    ): Result<Unit> {
        initJob.join()
        val existing = _state.value.providers.firstOrNull { it.id == providerId }
            ?: return Result.failure(ProviderException(ErrorKind.UNKNOWN, "供应商不存在"))
        val key = rawKey?.let(::normalizeKey)?.takeIf { it.isNotBlank() }
            ?: cipher.decrypt(existing.encryptedKey)
            ?: return Result.failure(ProviderException(ErrorKind.AUTH, "密钥无法读取，请重新输入"))
        val candidate = existing.copy(region = region)
        val now = System.currentTimeMillis()
        val usage = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { clients.fetch(candidate, key) }
                ?: return Result.failure(ProviderException(ErrorKind.NETWORK, "连接超时，请检查网络后重试"))
        } catch (error: ProviderException) {
            return Result.failure(error)
        } catch (error: Exception) {
            return Result.failure(ProviderException(ErrorKind.UNKNOWN, "连接失败，请稍后重试", error))
        }
        val encrypted = if (rawKey != null && key.isNotBlank()) {
            runCatching { cipher.encrypt(key) }.getOrElse { error ->
                return Result.failure(ProviderException(ErrorKind.UNKNOWN, "密钥保存失败，请重试", error))
            }
        } else {
            existing.encryptedKey
        }
        val updated = candidate.copy(encryptedKey = encrypted)
        val snapshot = usage.toSnapshot(providerId, now)
        stateMutex.withLock {
            val current = _state.value
            if (current.providers.none { it.id == providerId }) {
                return Result.failure(ProviderException(ErrorKind.UNKNOWN, "供应商已被移除"))
            }
            val next = current.copy(
                providers = current.providers.map { if (it.id == providerId) updated else it },
                snapshots = current.snapshots + (providerId to snapshot),
            )
            _state.value = next
            persist(next)
        }
        notifyWidget()
        return Result.success(Unit)
    }

    /** Debug-only helper used by the screenshot/demo receiver. */
    internal suspend fun replaceForDemo(
        providers: List<ProviderConfig>,
        snapshots: Map<String, UsageSnapshot>,
    ) {
        initJob.join()
        stateMutex.withLock {
            val current = _state.value
            val next = current.copy(providers = providers, snapshots = snapshots)
            _state.value = next
            persist(next)
        }
        notifyWidget()
    }

    suspend fun removeProvider(providerId: String) {
        initJob.join()
        stateMutex.withLock {
            val current = _state.value
            val next = current.copy(
                providers = current.providers.filterNot { it.id == providerId },
                snapshots = current.snapshots - providerId,
                refreshing = current.refreshing - providerId,
            )
            _state.value = next
            persist(next)
        }
        notifyWidget()
    }

    suspend fun updateTheme(mode: ThemeMode) {
        updateSettings(_state.value.settings.copy(themeMode = mode))
    }

    suspend fun updateRefreshMinutes(minutes: Int) {
        updateSettings(_state.value.settings.copy(refreshMinutes = minutes.coerceIn(15, 240)))
    }

    private suspend fun updateSettings(settings: AppSettings) {
        initJob.join()
        stateMutex.withLock {
            val current = _state.value
            val next = current.copy(settings = settings)
            _state.value = next
            persist(next)
        }
        scheduleRefresh()
        notifyWidget()
    }

    private fun scheduleRefresh() {
        RefreshScheduler.schedule(context, _state.value.settings.refreshMinutes)
    }

    private fun isStale(): Boolean {
        val providers = _state.value.providers
        if (providers.isEmpty()) return false
        val now = System.currentTimeMillis()
        return providers.any { provider ->
            val snapshot = _state.value.snapshots[provider.id]
            snapshot == null || snapshot.fetchedAt == 0L || now - snapshot.fetchedAt > STALE_AFTER_MS
        }
    }

    private suspend fun fetchSnapshot(config: ProviderConfig): UsageSnapshot {
        val now = System.currentTimeMillis()
        val key = cipher.decrypt(config.encryptedKey)
        if (key.isNullOrBlank()) {
            return failureSnapshot(config.id, "密钥无法读取，请重新添加", ErrorKind.AUTH, now)
        }
        val usage = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { clients.fetch(config, key) }
                ?: return failureSnapshot(config.id, "连接超时，请检查网络", ErrorKind.NETWORK, now)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // A cancelled refresh (e.g. the hosting scope went away) is NOT a
            // provider failure — never persist it as an expired snapshot.
            throw cancelled
        } catch (error: ProviderException) {
            return failureSnapshot(config.id, error.userMessage, error.kind, now)
        } catch (error: Exception) {
            return failureSnapshot(config.id, "更新失败，请稍后重试", ErrorKind.UNKNOWN, now)
        }
        return usage.toSnapshot(config.id, now)
    }

    private fun failureSnapshot(
        providerId: String,
        message: String,
        kind: ErrorKind,
        now: Long,
    ): UsageSnapshot {
        val old = _state.value.snapshots[providerId]
        if (old != null && old.hasData) {
            return old.copy(
                attemptedAt = now,
                error = message,
                errorKind = kind,
            )
        }
        return UsageSnapshot(
            providerId = providerId,
            fiveHour = QuotaSlot.error("更新失败"),
            weekly = QuotaSlot.error("更新失败"),
            monthly = QuotaSlot.error("更新失败"),
            balance = BalanceSlot.error("更新失败"),
            attemptedAt = now,
            error = message,
            errorKind = kind,
        )
    }

    private fun ProviderUsage.toSnapshot(providerId: String, now: Long): UsageSnapshot = UsageSnapshot(
        providerId = providerId,
        planName = planName,
        fiveHour = fiveHour,
        weekly = weekly,
        monthly = monthly,
        balance = balance,
        fetchedAt = now,
        attemptedAt = now,
        note = note,
    )

    private suspend fun load() {
        val stored = try {
            val raw = context.dataStore.data.first()[DATA_KEY]
            if (raw.isNullOrBlank()) StoredData() else json.decodeFromString<StoredData>(raw)
        } catch (error: Exception) {
            StoredData()
        }
        _state.value = AppState(
            providers = stored.providers,
            snapshots = stored.snapshots.filterKeys { id -> stored.providers.any { it.id == id } },
            settings = stored.settings,
            loaded = true,
        )
    }

    private suspend fun persist(state: AppState) {
        val stored = StoredData(
            providers = state.providers,
            snapshots = state.snapshots,
            settings = state.settings,
        )
        runCatching {
            context.dataStore.edit { preferences ->
                preferences[DATA_KEY] = json.encodeToString(stored)
            }
        }
    }

    private fun notifyWidget() {
        WidgetCacheStore.write(context, _state.value)
        scope.launch(Main) {
            val result = runCatching { refreshWidgets(context) }
            if (com.aiusage.app.BuildConfig.DEBUG) {
                Log.d("AIUsageWidget", "updateAll=$result")
            }
        }
    }

    private fun normalizeKey(raw: String): String = raw
        .trim()
        .removePrefix("Bearer ")
        .removePrefix("bearer ")
        .trim()

    companion object {
        private val DATA_KEY = stringPreferencesKey("state_v1")
        private const val FETCH_TIMEOUT_MS = 30_000L
        private const val STALE_AFTER_MS = 5 * 60_000L
    }
}

/** Stable, anonymous install id used only for OpenCode's session header. */
object InstallId {
    private const val PREFS = "aiusage_meta"
    private const val KEY = "install_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
