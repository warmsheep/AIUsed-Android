package com.aiusage.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattersTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `reset countdown formats minutes hours and days`() {
        assertEquals("即将重置", formatResetCountdown(now + 30_000L, now))
        assertEquals("已重置", formatResetCountdown(now - 2 * 60_000L, now))
        assertEquals("13分后重置", formatResetCountdown(now + 13 * 60_000L, now))
        assertEquals("2小时13分后重置", formatResetCountdown(now + (2 * 60 + 13) * 60_000L, now))
        assertEquals("2小时后重置", formatResetCountdown(now + 2 * 60 * 60_000L, now))
        assertEquals("7天后重置", formatResetCountdown(now + 7 * 24 * 60 * 60_000L, now))
    }

    @Test
    fun `reset countdown handles missing timestamps`() {
        assertEquals(null, formatResetCountdown(null, now))
        assertEquals(null, formatResetCountdown(0L, now))
    }

    @Test
    fun `updated at handles fresh and old timestamps`() {
        assertEquals("刚刚更新", formatUpdatedAt(now - 10_000L, now))
        assertEquals("3 分钟前", formatUpdatedAt(now - 3 * 60_000L, now))
        assertEquals("尚未更新", formatUpdatedAt(0L, now))
    }

    @Test
    fun `percent formatter keeps one decimal only when useful`() {
        assertEquals("66%", formatPercent(66.0))
        assertEquals("66.6%", formatPercent(66.6))
        assertEquals("—", formatPercent(null))
    }

    @Test
    fun `money formatter includes the currency symbol`() {
        assertEquals("¥88.25", formatMoney(88.25, "CNY"))
        assertEquals("$12.50", formatMoney(12.5, "USD"))
        assertEquals("14,000 积分", formatCredits(14000.0, "积分"))
    }
}
