package com.aiusage.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.provider.ProviderClients
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderClientsIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun zhipuUsesQuotaPathAndBearer() {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"level":"pro","limits":[{"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":26},{"type":"TOKENS_LIMIT","unit":6,"number":1,"percentage":40}]}}""",
            ),
        )
        val usage = runBlocking {
            clients(ProviderKind.ZHIPU).fetch(config(ProviderKind.ZHIPU), "test-key")
        }
        assertEquals("GLM Coding Plan · Pro", usage.planName)
        assertEquals(74.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(60.0, usage.weekly.remainingPercent!!, 0.001)
        val request = server.takeRequest()
        assertEquals("/api/monitor/usage/quota/limit", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun miniMaxUsesTokenPlanPathAndReadsCredits() {
        server.enqueue(
            MockResponse().setBody(
                """{"base_resp":{"status_code":0},"data":{"current_subscribe_title":"M Plan Explore","points_balance":"14000","model_remains":[{"model_name":"general","current_interval_remaining_percent":"96","end_time":1790000000000,"current_weekly_remaining_percent":"99","weekly_end_time":1790500000000}]}}""",
            ),
        )
        val usage = runBlocking {
            clients(ProviderKind.MINIMAX).fetch(config(ProviderKind.MINIMAX), "test-key")
        }
        assertEquals("M Plan Explore", usage.planName)
        assertEquals(96.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(99.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(14000.0, usage.balance.amount!!, 0.001)
        assertEquals("/v1/token_plan/remains", server.takeRequest().path)
    }

    @Test
    fun openCodeUsesUsagePathAndSessionHeader() {
        server.enqueue(
            MockResponse().setBody(
                """{"usage":{"rolling":{"status":"ok","percent":40,"resetsAt":"2026-10-28T18:00:00Z"},"weekly":{"status":"ok","percent":10,"resetsAt":"2026-11-02T00:00:00Z"},"monthly":{"status":"ok","percent":70,"resetsAt":"2026-11-20T00:00:00Z"}}}""",
            ),
        )
        val usage = runBlocking {
            ProviderClients(
                installId = "test-session",
                endpointOverrides = mapOf(ProviderKind.OPENCODE_GO to baseUrl),
            ).fetch(config(ProviderKind.OPENCODE_GO), "test-key")
        }
        assertEquals(60.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(90.0, usage.weekly.remainingPercent!!, 0.001)
        assertEquals(30.0, usage.monthly.remainingPercent!!, 0.001)
        val request = server.takeRequest()
        assertEquals("/v1/usage", request.path)
        assertEquals("test-session", request.getHeader("x-opencode-session"))
    }

    @Test
    fun deepSeekUsesBalancePath() {
        server.enqueue(
            MockResponse().setBody(
                """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"88.25"}]}""",
            ),
        )
        val usage = runBlocking {
            clients(ProviderKind.DEEPSEEK).fetch(config(ProviderKind.DEEPSEEK), "test-key")
        }
        assertEquals(88.25, usage.balance.amount!!, 0.001)
        assertEquals("/user/balance", server.takeRequest().path)
    }

    @Test
    fun kimiCodeUsesUsagePath() {
        server.enqueue(
            MockResponse().setBody(
                """{"user":{"membership":{"level":"LEVEL_INTERMEDIATE"}},"usage":{"limit":"100","used":"5"},"limits":[{"window":{"duration":300,"timeUnit":"TIME_UNIT_MINUTE"},"detail":{"limit":"100","remaining":"100"}}]}""",
            ),
        )
        val usage = runBlocking {
            clients(ProviderKind.KIMI).fetch(config(ProviderKind.KIMI), "sk-kimi-test")
        }
        assertEquals("Kimi Code · Intermediate", usage.planName)
        assertEquals(100.0, usage.fiveHour.remainingPercent!!, 0.001)
        assertEquals(95.0, usage.weekly.remainingPercent!!, 0.001)
        val request = server.takeRequest()
        assertEquals("/coding/v1/usages", request.path)
        assertEquals(null, request.getHeader("X-Msh-Platform"))
    }

    @Test
    fun moonshotKeyUsesBalancePath() {
        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"available_balance":12.34}}"""),
        )
        val usage = runBlocking {
            clients(ProviderKind.KIMI).fetch(config(ProviderKind.KIMI), "sk-moonshot-test")
        }
        assertEquals(12.34, usage.balance.amount!!, 0.001)
        assertEquals("USD", usage.balance.currency)
        assertEquals("/v1/users/me/balance", server.takeRequest().path)
    }

    private fun clients(kind: ProviderKind): ProviderClients = ProviderClients(
        installId = "test-session",
        endpointOverrides = mapOf(kind to baseUrl),
    )

    private fun config(kind: ProviderKind) = ProviderConfig(
        id = "test-${kind.name}",
        kind = kind,
        region = Region.AUTO,
        encryptedKey = "",
        createdAt = 0,
    )
}
