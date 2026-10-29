package com.aiusage.app.provider

import com.aiusage.app.data.model.ErrorKind
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.data.provider.DeepSeekParser
import com.aiusage.app.data.provider.KimiParser
import com.aiusage.app.data.provider.MiniMaxParser
import com.aiusage.app.data.provider.OpenCodeGoParser
import com.aiusage.app.data.provider.ProviderException
import com.aiusage.app.data.provider.ZhipuParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderParsersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `zhipu parses five hour and weekly used percentages`() {
        val root = json.parseToJsonElement(
            """
            {
              "code": 0,
              "data": {
                "level": "pro",
                "limits": [
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 3,
                    "number": 5,
                    "percentage": 26,
                    "nextResetTime": "2026-10-28T18:00:00Z"
                  },
                  {
                    "type": "TOKENS_LIMIT",
                    "unit": 6,
                    "number": 1,
                    "usage": 100,
                    "currentValue": 40,
                    "nextResetTime": 1790000000000
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val usage = ZhipuParser.parse(root)
        assertEquals("GLM Coding Plan · Pro", usage.planName)
        assertEquals(SlotState.SUPPORTED, usage.fiveHour.state)
        assertEquals(74.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(SlotState.SUPPORTED, usage.weekly.state)
        assertEquals(60.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(SlotState.UNSUPPORTED, usage.monthly.state)
        assertFalse(usage.balance.isSupported)
    }

    @Test
    fun `zhipu maps an auth code even with HTTP 200`() {
        val root = json.parseToJsonElement("""{"code":401,"message":"bad key","data":null}""")
        try {
            ZhipuParser.parse(root)
            fail("expected auth failure")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.AUTH, error.kind)
        }
    }

    @Test
    fun `zhipu reports no plan when limits are empty`() {
        val root = json.parseToJsonElement("""{"code":0,"data":{"limits":[]}}""")
        try {
            ZhipuParser.parse(root)
            fail("expected no plan")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.NO_PLAN, error.kind)
        }
    }

    @Test
    fun `minimax parses percentages credits and plan name`() {
        val root = json.parseToJsonElement(
            """
            {
              "base_resp": { "status_code": 0 },
              "data": {
                "current_subscribe_title": "M Plan Explore",
                "points_balance": "14000",
                "model_remains": [
                  {
                    "model_name": "general",
                    "current_interval_remaining_percent": "96",
                    "start_time": 1780279200000,
                    "end_time": 1780297200000,
                    "current_weekly_remaining_percent": "99",
                    "weekly_start_time": 1780243200000,
                    "weekly_end_time": 1780848000000
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val usage = MiniMaxParser.parse(root)
        assertEquals("M Plan Explore", usage.planName)
        assertEquals(96.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(99.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(14000.0, usage.balance.amount!!, 0.001)
        assertEquals("积分", usage.balance.unit)
        assertNotNull(usage.fiveHour.resetsAt)
    }

    @Test
    fun `minimax treats unlimited weekly as unsupported and exhausted interval as zero`() {
        val root = json.parseToJsonElement(
            """
            {
              "base_resp": { "status_code": "0" },
              "model_remains": [
                {
                  "model_name": "general",
                  "current_interval_remaining_percent": 80,
                  "current_interval_status": 2,
                  "current_weekly_remaining_percent": 100,
                  "current_weekly_status": 3
                }
              ]
            }
            """.trimIndent(),
        )
        val usage = MiniMaxParser.parse(root)
        assertEquals(0.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(SlotState.UNSUPPORTED, usage.weekly.state)
    }

    @Test
    fun `minimax reports an auth message when the key is rejected in the body`() {
        val root = json.parseToJsonElement(
            """{"base_resp":{"status_code":1004,"status_msg":"invalid api key"},"model_remains":[]}""",
        )
        try {
            MiniMaxParser.parse(root)
            fail("expected auth failure")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.AUTH, error.kind)
            assertTrue(error.userMessage.contains("订阅 Key"))
        }
    }

    @Test
    fun `opencode converts used percent into remaining`() {
        val root = json.parseToJsonElement(
            """
            {
              "usage": {
                "rolling": { "status": "ok", "percent": 40, "resetsAt": "2026-10-28T18:00:00Z" },
                "weekly": { "status": "ok", "percent": 12.5, "resetsAt": "2026-11-02T00:00:00Z" },
                "monthly": { "status": "rate-limited", "percent": 100, "resetsAt": "2026-11-20T00:00:00Z" }
              }
            }
            """.trimIndent(),
        )
        val usage = OpenCodeGoParser.parse(root)
        assertEquals(60.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(87.5, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(0.0, usage.monthly.remainingPercent!!, 0.001)
        assertEquals("已达上限", usage.monthly.note)
        assertNotNull(usage.fiveHour.resetsAt)
    }

    @Test
    fun `opencode fails cleanly when usage is missing`() {
        try {
            OpenCodeGoParser.parse(json.parseToJsonElement("""{"ok":true}"""))
            fail("expected no plan")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.NO_PLAN, error.kind)
        }
    }

    @Test
    fun `deepseek chooses CNY and keeps balance only`() {
        val root = json.parseToJsonElement(
            """
            {
              "is_available": true,
              "balance_infos": [
                { "currency": "USD", "total_balance": "1.50" },
                { "currency": "CNY", "total_balance": "88.25" }
              ]
            }
            """.trimIndent(),
        )
        val usage = DeepSeekParser.parse(root)
        assertEquals(88.25, usage.balance.amount!!, 0.001)
        assertEquals("CNY", usage.balance.currency)
        assertEquals(SlotState.UNSUPPORTED, usage.fiveHour.state)
        assertEquals(SlotState.UNSUPPORTED, usage.weekly.state)
        assertEquals(SlotState.UNSUPPORTED, usage.monthly.state)
    }

    @Test
    fun `deepseek notes a disabled account`() {
        val root = json.parseToJsonElement(
            """{"is_available":false,"balance_infos":[{"currency":"USD","total_balance":0}]}""",
        )
        val usage = DeepSeekParser.parse(root)
        assertEquals(0.0, usage.balance.amount!!, 0.001)
        assertNotNull(usage.balance.note)
    }

    @Test
    fun `kimi code parses weekly five hour and optional monthly windows`() {
        val root = json.parseToJsonElement(
            """
            {
              "user": { "membership": { "level": "LEVEL_INTERMEDIATE" } },
              "usage": {
                "limit": "100",
                "used": "5",
                "remaining": "95",
                "resetTime": "2026-11-04T13:27:17.211Z"
              },
              "limits": [
                {
                  "window": { "duration": 300, "timeUnit": "TIME_UNIT_MINUTE" },
                  "detail": { "limit": "100", "remaining": "100", "resetTime": "2026-10-28T23:27:17.211Z" }
                },
                {
                  "window": { "duration": 30, "timeUnit": "TIME_UNIT_DAY" },
                  "detail": { "limit": 1000, "used": 400 }
                }
              ]
            }
            """.trimIndent(),
        )
        val usage = KimiParser.parseCodeUsage(root)
        assertEquals("Kimi Code · Intermediate", usage.planName)
        assertEquals(100.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(95.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(60.0, usage.monthly.remainingPercent!!, 0.001)
        assertNotNull(usage.fiveHour.resetsAt)
    }

    @Test
    fun `kimi accepts string numbers and epoch reset stamps`() {
        val root = json.parseToJsonElement(
            """
            {
              "usage": { "limit": "100", "used": "40", "reset_at": "1784900000" },
              "limits": [
                {
                  "window": { "duration": "5", "timeUnit": "hour" },
                  "detail": { "limit": 100, "remaining": 74, "resetTime": 1784900000 }
                }
              ]
            }
            """.trimIndent(),
        )
        val usage = KimiParser.parseCodeUsage(root)
        assertEquals(60.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(74.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertNotNull(usage.fiveHour.resetsAt)
    }

    @Test
    fun `kimi moonshot balance uses the currency of the selected region`() {
        val root = json.parseToJsonElement(
            """{"code":0,"data":{"available_balance":12.34,"voucher_balance":2,"cash_balance":10.34}}""",
        )
        val usage = KimiParser.parseMoonshotBalance(root, "USD")
        assertEquals(12.34, usage.balance.amount!!, 0.001)
        assertEquals("USD", usage.balance.currency)
        assertEquals(SlotState.UNSUPPORTED, usage.monthly.state)
    }

    @Test
    fun `kimi identifies code keys only`() {
        assertTrue(KimiParser.isCodeKey("sk-kimi-abc"))
        assertTrue(KimiParser.isCodeKey("  SK-KIMI-ABC "))
        assertFalse(KimiParser.isCodeKey("sk-abc"))
    }
}
