package com.aiusage.app.model

import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.UsageSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-refresh staleness rule used by ON_RESUME / repository init. Beyond the
 * classic "older than 5 minutes" case, a FAILED attempt must count as stale
 * after a short cooldown so that a background refresh which was network-blocked
 * (and stamped "数据过期" over old data) heals itself the next time the app is
 * in the foreground, where networking actually works.
 */
class AppStateStaleTest {

    private fun provider(id: String = "p1") = ProviderConfig(
        id = id,
        kind = ProviderKind.OPENCODE_GO,
        region = Region.AUTO,
        encryptedKey = "x",
        createdAt = 1,
    )

    private fun snapshot(
        fetchedAt: Long,
        attemptedAt: Long = fetchedAt,
        error: String? = null,
    ) = UsageSnapshot(
        providerId = "p1",
        fiveHour = QuotaSlot.of(50.0),
        fetchedAt = fetchedAt,
        attemptedAt = attemptedAt,
        error = error,
    )

    @Test
    fun emptyStateIsNeverStale() {
        assertFalse(AppState().isStale(now = 10_000L))
    }

    @Test
    fun missingSnapshotIsStale() {
        val state = AppState(providers = listOf(provider()))
        assertTrue(state.isStale(now = 10_000L))
    }

    @Test
    fun freshSuccessIsNotStale() {
        val state = AppState(
            providers = listOf(provider()),
            snapshots = mapOf("p1" to snapshot(fetchedAt = 100_000L)),
        )
        assertFalse(state.isStale(now = 100_000L + 4 * 60_000L))
    }

    @Test
    fun successOlderThanFiveMinutesIsStale() {
        val state = AppState(
            providers = listOf(provider()),
            snapshots = mapOf("p1" to snapshot(fetchedAt = 100_000L)),
        )
        assertTrue(state.isStale(now = 100_000L + 5 * 60_000L + 1))
    }

    @Test
    fun failedAttemptWithinCooldownIsNotStale() {
        // The user just watched a refresh fail; don't hammer the API on every
        // app resume within the cooldown.
        val state = AppState(
            providers = listOf(provider()),
            snapshots = mapOf("p1" to snapshot(fetchedAt = 100_000L, attemptedAt = 200_000L, error = "网络超时")),
        )
        assertFalse(state.isStale(now = 200_000L + 30_000L))
    }

    @Test
    fun failedAttemptPastCooldownIsStale() {
        // A blocked background refresh left "数据过期" over good data; the next
        // app open must retry so the UI heals itself in the foreground.
        val state = AppState(
            providers = listOf(provider()),
            snapshots = mapOf("p1" to snapshot(fetchedAt = 100_000L, attemptedAt = 200_000L, error = "网络超时")),
        )
        assertTrue(state.isStale(now = 200_000L + 61_000L))
    }

    @Test
    fun oneFailedProviderMakesWholeStateStale() {
        val state = AppState(
            providers = listOf(provider("ok"), provider("bad")),
            snapshots = mapOf(
                "ok" to snapshot(fetchedAt = 500_000L),
                "bad" to snapshot(fetchedAt = 500_000L, attemptedAt = 600_000L, error = "密钥无效"),
            ),
        )
        assertTrue(state.isStale(now = 600_000L + 120_000L))
    }

    @Test
    fun neverFetchedSnapshotIsStaleEvenWithoutError() {
        val state = AppState(
            providers = listOf(provider()),
            snapshots = mapOf("p1" to snapshot(fetchedAt = 0L, attemptedAt = 100L)),
        )
        assertTrue(state.isStale(now = 200L))
    }
}
