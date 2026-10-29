package com.aiusage.app.data.provider

import com.aiusage.app.BuildConfig
import com.aiusage.app.data.model.ErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal class ApiHttp(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun get(
        url: String,
        apiKey: String? = null,
        authScheme: String = "Bearer",
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "AIUsage/${BuildConfig.VERSION_NAME} (Android)")
        if (apiKey != null) {
            val header = if (authScheme.isBlank()) apiKey else "$authScheme $apiKey"
            builder.header("Authorization", header)
        }
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: SocketTimeoutException) {
            throw ProviderException(ErrorKind.NETWORK, "网络超时，请稍后重试", error)
        } catch (error: UnknownHostException) {
            throw ProviderException(ErrorKind.NETWORK, "无法连接网络，请检查网络设置", error)
        } catch (error: IOException) {
            throw ProviderException(ErrorKind.NETWORK, "网络请求失败，请稍后重试", error)
        }

        response.use { res ->
            val body = try {
                res.body?.string().orEmpty()
            } catch (error: IOException) {
                throw ProviderException(ErrorKind.NETWORK, "读取响应失败，请稍后重试", error)
            }
            if (!res.isSuccessful) {
                throw httpError(res.code, body)
            }
            if (body.isBlank()) {
                throw ProviderException(ErrorKind.PARSE, "接口返回为空")
            }
            try {
                json.parseToJsonElement(body)
            } catch (error: Exception) {
                throw ProviderException(ErrorKind.PARSE, "接口返回的数据格式无法解析", error)
            }
        }
    }

    private fun httpError(status: Int, body: String): ProviderException {
        val upstream = extractUpstreamMessage(body)
        return when {
            status == 401 || status == 403 -> ProviderException(
                ErrorKind.AUTH,
                "密钥无效，或没有访问该套餐的权限",
            )
            status == 429 -> ProviderException(
                ErrorKind.RATE_LIMIT,
                "请求过于频繁，请稍后再试",
            )
            status in 500..599 -> ProviderException(
                ErrorKind.SERVER,
                "供应商服务暂时不可用（$status）",
            )
            else -> ProviderException(
                ErrorKind.UNKNOWN,
                upstream?.takeIf { it.isNotBlank() }?.let { "请求失败（$status）：${it.take(80)}" }
                    ?: "请求失败（$status）",
            )
        }
    }

    private fun extractUpstreamMessage(body: String): String? {
        if (body.isBlank() || body.length > 20_000) return null
        return runCatching {
            val root = json.parseToJsonElement(body)
            val candidates = listOf(
                root as? JsonObject,
                (root as? JsonObject)?.get("error") as? JsonObject,
                (root as? JsonObject)?.get("base_resp") as? JsonObject,
            )
            candidates.asSequence().filterNotNull().mapNotNull { obj ->
                listOf("message", "msg", "status_msg", "detail")
                    .firstNotNullOfOrNull { key ->
                        (obj[key] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }
                    }
            }.firstOrNull()
        }.getOrNull()
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
