package com.aiusage.app.provider

import com.aiusage.app.data.model.ErrorKind
import com.aiusage.app.data.provider.ApiHttp
import com.aiusage.app.data.provider.ProviderException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiHttpTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `get sends bearer authorization and parses json`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"value":42}"""),
        )
        val element = ApiHttp().get(server.url("/usage").toString(), apiKey = "sk-test")
        assertEquals("42", element.toString().substringAfter("\"value\":").substringBefore("}"))
        val request = server.takeRequest()
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("AIUsage/"))
    }

    @Test
    fun `blank auth scheme sends the key as the raw header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        ApiHttp().get(server.url("/usage").toString(), apiKey = "raw-key", authScheme = "")
        assertEquals("raw-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 becomes auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"bad key"}"""))
        try {
            ApiHttp().get(server.url("/usage").toString(), apiKey = "bad")
            throw AssertionError("expected auth error")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.AUTH, error.kind)
            assertTrue(error.userMessage.contains("密钥"))
        }
    }

    @Test
    fun `429 becomes rate limit error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            ApiHttp().get(server.url("/usage").toString(), apiKey = "key")
            throw AssertionError("expected rate limit error")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.RATE_LIMIT, error.kind)
        }
    }

    @Test
    fun `500 becomes server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        try {
            ApiHttp().get(server.url("/usage").toString(), apiKey = "key")
            throw AssertionError("expected server error")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.SERVER, error.kind)
        }
    }

    @Test
    fun `invalid json becomes parse error`() = runTest {
        server.enqueue(MockResponse().setBody("not json {"))
        try {
            ApiHttp().get(server.url("/usage").toString(), apiKey = "key")
            throw AssertionError("expected parse error")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.PARSE, error.kind)
        }
    }

    @Test
    fun `empty body becomes parse error`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        try {
            ApiHttp().get(server.url("/usage").toString(), apiKey = "key")
            throw AssertionError("expected parse error")
        } catch (error: ProviderException) {
            assertEquals(ErrorKind.PARSE, error.kind)
        }
    }
}
