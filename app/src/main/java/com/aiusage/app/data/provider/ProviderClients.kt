package com.aiusage.app.data.provider

import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.ProviderUsage
import com.aiusage.app.data.model.Region

internal class ProviderClients(
    private val http: ApiHttp = ApiHttp(),
    private val installId: String = "aiusage-android",
    private val endpointOverrides: Map<ProviderKind, String> = emptyMap(),
) {
    suspend fun fetch(config: ProviderConfig, apiKey: String): ProviderUsage {
        val key = apiKey.trim()
        return when (config.kind) {
            ProviderKind.ZHIPU -> fetchZhipu(key, config.region)
            ProviderKind.MINIMAX -> fetchMiniMax(key, config.region)
            ProviderKind.OPENCODE_GO -> fetchOpenCodeGo(key)
            ProviderKind.DEEPSEEK -> fetchDeepSeek(key)
            ProviderKind.KIMI -> fetchKimi(key, config.region)
        }
    }

    private suspend fun fetchZhipu(key: String, region: Region): ProviderUsage {
        val override = endpointOverrides[ProviderKind.ZHIPU]?.trimEnd('/')
        val candidates = if (override != null) {
            listOf(override)
        } else {
            when (region) {
                Region.AUTO -> listOf(CN_ZHIPU, GLOBAL_ZHIPU)
                Region.CN -> listOf(CN_ZHIPU)
                Region.GLOBAL -> listOf(GLOBAL_ZHIPU)
            }
        }
        return fetchWithFallback(candidates) { base ->
            val scheme = if (base == GLOBAL_ZHIPU) "" else "Bearer"
            http.get(
                url = "$base/api/monitor/usage/quota/limit",
                apiKey = key,
                authScheme = scheme,
            ).let(ZhipuParser::parse)
        }
    }

    private suspend fun fetchMiniMax(key: String, region: Region): ProviderUsage {
        val override = endpointOverrides[ProviderKind.MINIMAX]?.trimEnd('/')
        val candidates = if (override != null) {
            listOf(override)
        } else {
            when (region) {
                Region.AUTO -> listOf(GLOBAL_MINIMAX, CN_MINIMAX)
                Region.CN -> listOf(CN_MINIMAX)
                Region.GLOBAL -> listOf(GLOBAL_MINIMAX)
            }
        }
        return fetchWithFallback(candidates) { base ->
            http.get(
                url = "$base/v1/token_plan/remains",
                apiKey = key,
            ).let(MiniMaxParser::parse)
        }
    }

    private suspend fun fetchOpenCodeGo(key: String): ProviderUsage {
        val base = endpointOverrides[ProviderKind.OPENCODE_GO]?.trimEnd('/') ?: OPENCODE_GO
        return http.get(
            url = "$base/v1/usage",
            apiKey = key,
            extraHeaders = mapOf("x-opencode-session" to installId),
        ).let(OpenCodeGoParser::parse)
    }

    private suspend fun fetchDeepSeek(key: String): ProviderUsage {
        val base = endpointOverrides[ProviderKind.DEEPSEEK]?.trimEnd('/') ?: DEEPSEEK
        return http.get(
            url = "$base/user/balance",
            apiKey = key,
        ).let(DeepSeekParser::parse)
    }

    private suspend fun fetchKimi(key: String, region: Region): ProviderUsage {
        return if (KimiParser.isCodeKey(key)) {
            val override = endpointOverrides[ProviderKind.KIMI]?.trimEnd('/')
            val candidates = if (override != null) {
                listOf(override)
            } else {
                when (region) {
                    Region.AUTO -> listOf(CN_KIMI, GLOBAL_KIMI)
                    Region.CN -> listOf(CN_KIMI)
                    Region.GLOBAL -> listOf(GLOBAL_KIMI)
                }
            }
            try {
                fetchWithFallback(candidates) { base ->
                    http.get(
                        url = "$base/coding/v1/usages",
                        apiKey = key,
                    ).let(KimiParser::parseCodeUsage)
                }
            } catch (error: ProviderException) {
                if (error.kind == com.aiusage.app.data.model.ErrorKind.AUTH) {
                    throw ProviderException(
                        com.aiusage.app.data.model.ErrorKind.AUTH,
                        "Kimi Code 拒绝了该密钥，请确认 Key 来自 Kimi Code 控制台（sk-kimi-…）",
                    )
                }
                throw error
            }
        } else {
            val override = endpointOverrides[ProviderKind.KIMI]?.trimEnd('/')
            val candidates = if (override != null) {
                listOf(override to "USD")
            } else {
                when (region) {
                    Region.AUTO -> listOf(CN_MOONSHOT to "CNY", GLOBAL_MOONSHOT to "USD")
                    Region.CN -> listOf(CN_MOONSHOT to "CNY")
                    Region.GLOBAL -> listOf(GLOBAL_MOONSHOT to "USD")
                }
            }
            try {
                fetchRegionalWithFallback(candidates) { (base, currency) ->
                    http.get(
                        url = "$base/v1/users/me/balance",
                        apiKey = key,
                    ).let { KimiParser.parseMoonshotBalance(it, currency) }
                }
            } catch (error: ProviderException) {
                if (error.kind == com.aiusage.app.data.model.ErrorKind.AUTH) {
                    throw ProviderException(
                        com.aiusage.app.data.model.ErrorKind.AUTH,
                        "密钥无效，请确认 Key 来自 Moonshot 开放平台，或使用 Kimi Code 的 sk-kimi- Key",
                    )
                }
                throw error
            }
        }
    }

    /**
     * Tries region candidates in order. Auth and network failures may be
     * region-specific, so those fall through to the next endpoint. Parsing and
     * server failures are not retried against a different host.
     */
    private suspend fun <T> fetchWithFallback(
        candidates: List<String>,
        block: suspend (String) -> T,
    ): T {
        var lastError: ProviderException? = null
        for (candidate in candidates) {
            try {
                return block(candidate)
            } catch (error: ProviderException) {
                lastError = error
                if (error.kind != com.aiusage.app.data.model.ErrorKind.AUTH &&
                    error.kind != com.aiusage.app.data.model.ErrorKind.NETWORK
                ) {
                    throw error
                }
            }
        }
        throw lastError ?: ProviderException(
            com.aiusage.app.data.model.ErrorKind.UNKNOWN,
            "没有可用的接口地址",
        )
    }

    private suspend fun <T> fetchRegionalWithFallback(
        candidates: List<Pair<String, String>>,
        block: suspend (Pair<String, String>) -> T,
    ): T {
        var lastError: ProviderException? = null
        for (candidate in candidates) {
            try {
                return block(candidate)
            } catch (error: ProviderException) {
                lastError = error
                if (error.kind != com.aiusage.app.data.model.ErrorKind.AUTH &&
                    error.kind != com.aiusage.app.data.model.ErrorKind.NETWORK
                ) {
                    throw error
                }
            }
        }
        throw lastError ?: ProviderException(
            com.aiusage.app.data.model.ErrorKind.UNKNOWN,
            "没有可用的接口地址",
        )
    }

    companion object {
        private const val CN_ZHIPU = "https://open.bigmodel.cn"
        private const val GLOBAL_ZHIPU = "https://api.z.ai"
        private const val CN_MINIMAX = "https://api.minimaxi.com"
        private const val GLOBAL_MINIMAX = "https://api.minimax.io"
        private const val OPENCODE_GO = "https://opencode.ai/zen/go"
        private const val DEEPSEEK = "https://api.deepseek.com"
        private const val CN_KIMI = "https://api.kimi.com"
        private const val GLOBAL_KIMI = "https://api.kimi.ai"
        private const val CN_MOONSHOT = "https://api.moonshot.cn"
        private const val GLOBAL_MOONSHOT = "https://api.moonshot.ai"
    }
}
