package com.aiusage.app.data.provider

import com.aiusage.app.data.model.BalanceSlot
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.util.array
import com.aiusage.app.util.asArrayOrNull
import com.aiusage.app.util.asObjectOrNull
import com.aiusage.app.util.booleanOrNull
import com.aiusage.app.util.doubleOrNull
import com.aiusage.app.util.longOrNull
import com.aiusage.app.util.obj
import com.aiusage.app.util.parseTimestamp
import com.aiusage.app.util.percent
import com.aiusage.app.util.stringOrNull
import com.aiusage.app.util.usedToRemaining
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------------------------------------------------------
// 智谱 / Z.ai GLM Coding Plan
// ---------------------------------------------------------------------------

internal object ZhipuParser {
    fun parse(root: JsonElement): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val code = rootObject.longOrNull("code")
        if (code == 401L || code == 403L) authError()
        if (code != null && code != 0L && rootObject["data"] == null) {
            noPlanError("智谱接口返回错误（$code）")
        }
        val data = rootObject.obj("data") ?: rootObject
        val limits = data.array("limits") ?: noPlanError("没有读取到 GLM Coding Plan 用量")

        var fiveHour: QuotaSlot? = null
        var weekly: QuotaSlot? = null
        for (element in limits) {
            val limit = element.asObjectOrNull() ?: continue
            val type = limit.stringOrNull("type") ?: continue
            if (type != "TOKENS_LIMIT" && type != "CREDIT_LIMIT") continue
            val unit = limit.longOrNull("unit")
            val number = limit.longOrNull("number")
            val bucket = when {
                unit == 3L && number == 5L -> FIVE_HOUR
                unit == 6L && number == 1L -> WEEK
                else -> null
            } ?: continue

            var used = limit.doubleOrNull("percentage")
            if (used == null) {
                val current = limit.doubleOrNull("currentValue", "current_value")
                val total = limit.doubleOrNull("usage", "total")
                if (current != null && total != null && total > 0) {
                    used = current / total * 100.0
                }
            }
            val remaining = usedToRemaining(used) ?: continue
            val slot = QuotaSlot.of(
                remainingPercent = remaining,
                resetsAt = parseTimestamp(limit["nextResetTime"] ?: limit["next_reset_time"]),
            )
            when (bucket) {
                FIVE_HOUR -> fiveHour = slot
                WEEK -> weekly = slot
            }
        }

        if (fiveHour == null && weekly == null) {
            noPlanError("套餐未开通或没有可读取的额度")
        }
        val level = data.stringOrNull("level", "planName", "plan_name")?.let(::prettyPlan)
        return com.aiusage.app.data.model.ProviderUsage(
            planName = if (level != null) "GLM Coding Plan · $level" else "GLM Coding Plan",
            fiveHour = fiveHour ?: QuotaSlot.unsupported(),
            weekly = weekly ?: QuotaSlot.unsupported(),
            monthly = QuotaSlot.unsupported(),
            balance = BalanceSlot.unsupported(),
        )
    }

    private const val FIVE_HOUR = 5
    private const val WEEK = 6

    private fun prettyPlan(raw: String): String = raw
        .removePrefix("LEVEL_")
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
}

// ---------------------------------------------------------------------------
// MiniMax M Plan / Token Plan
// ---------------------------------------------------------------------------

internal object MiniMaxParser {
    fun parse(root: JsonElement): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val base = rootObject.obj("base_resp")
        val statusCode = base?.longOrNull("status_code")
        if (statusCode != null && statusCode != 0L) {
            val message = base.stringOrNull("status_msg").orEmpty()
            if (statusCode == 1004L || statusCode == 2049L || statusCode == 1002L) {
                authError("MiniMax 拒绝了该密钥，请确认使用的是 M Plan 订阅 Key")
            }
            noPlanError(
                if (message.isBlank()) "MiniMax 接口返回错误（$statusCode）"
                else "MiniMax：${message.take(60)}",
            )
        }

        val data = rootObject.obj("data") ?: rootObject
        val rows = data.array("model_remains")
            ?: rootObject.array("model_remains")
            ?: noPlanError("没有读取到 MiniMax 套餐额度")
        if (rows.isEmpty()) noPlanError("没有读取到 MiniMax 套餐额度")

        val buckets = rows.mapNotNull { it.asObjectOrNull() }
        val bucket = buckets.firstOrNull { it.stringOrNull("model_name") == "general" }
            ?: buckets.firstOrNull { it.stringOrNull("model_name") !in setOf("video", "audio", "image") }
            ?: buckets.firstOrNull()
            ?: noPlanError("MiniMax 额度数据为空")

        val intervalPercent = bucket.percent("current_interval_remaining_percent")
        val intervalStatus = bucket.longOrNull("current_interval_status")
        val weeklyPercent = bucket.percent("current_weekly_remaining_percent")
        val weeklyStatus = bucket.longOrNull("current_weekly_status")

        val fiveHour = when {
            intervalStatus == STATUS_EXHAUSTED -> QuotaSlot.of(0.0)
            intervalPercent != null && intervalStatus == STATUS_UNLIMITED ->
                QuotaSlot.of(100.0, note = "不限量")
            intervalPercent != null -> QuotaSlot.of(
                remainingPercent = intervalPercent,
                resetsAt = parseTimestamp(bucket["end_time"]),
            )
            else -> QuotaSlot.unsupported()
        }

        val weekly = when {
            weeklyStatus == STATUS_UNLIMITED -> QuotaSlot.unsupported(note = "该套餐没有周额度")
            weeklyStatus == STATUS_EXHAUSTED -> QuotaSlot.of(0.0)
            weeklyPercent != null -> QuotaSlot.of(
                remainingPercent = weeklyPercent,
                resetsAt = parseTimestamp(bucket["weekly_end_time"]),
            )
            else -> QuotaSlot.unsupported()
        }

        val points = data.doubleOrNull("points_balance")
            ?: rootObject.doubleOrNull("points_balance")
        val balance = if (points != null) {
            BalanceSlot.credits(points, note = "1,000 积分 = $1")
        } else {
            BalanceSlot.unsupported()
        }

        if (fiveHour.state == com.aiusage.app.data.model.SlotState.UNSUPPORTED &&
            weekly.state == com.aiusage.app.data.model.SlotState.UNSUPPORTED &&
            balance.state == com.aiusage.app.data.model.SlotState.UNSUPPORTED
        ) {
            noPlanError("MiniMax 套餐没有可读取的额度")
        }

        return com.aiusage.app.data.model.ProviderUsage(
            planName = data.stringOrNull("current_subscribe_title", "current_plan_title", "plan_name")
                ?: rootObject.stringOrNull("current_subscribe_title", "current_plan_title", "plan_name")
                ?: "M Plan",
            fiveHour = fiveHour,
            weekly = weekly,
            monthly = QuotaSlot.unsupported(),
            balance = balance,
        )
    }

    private const val STATUS_EXHAUSTED = 2L
    private const val STATUS_UNLIMITED = 3L
}

// ---------------------------------------------------------------------------
// OpenCode Go
// ---------------------------------------------------------------------------

internal object OpenCodeGoParser {
    fun parse(root: JsonElement): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val usage = rootObject.obj("usage")
            ?: rootObject.obj("data")?.obj("usage")
            ?: noPlanError("没有读取到 OpenCode Go 用量")

        val rolling = parseWindow(usage["rolling"] ?: usage["5h"] ?: usage["five_hour"])
        val weekly = parseWindow(usage["weekly"] ?: usage["week"] ?: usage["7d"])
        val monthly = parseWindow(usage["monthly"] ?: usage["month"])

        if (rolling == null && weekly == null && monthly == null) {
            noPlanError("没有读取到 OpenCode Go 订阅额度")
        }

        return com.aiusage.app.data.model.ProviderUsage(
            planName = rootObject.stringOrNull("plan", "planName", "plan_name") ?: "Go 订阅",
            fiveHour = rolling ?: QuotaSlot.unsupported(),
            weekly = weekly ?: QuotaSlot.unsupported(),
            monthly = monthly ?: QuotaSlot.unsupported(),
            balance = BalanceSlot.unsupported(),
        )
    }

    private fun parseWindow(element: JsonElement?): QuotaSlot? {
        val obj = element.asObjectOrNull() ?: return null
        val used = obj.percent("percent", "usedPercent", "used_percent") ?: return null
        val status = obj.stringOrNull("status")
        val resetsAt = parseTimestamp(obj["resetsAt"] ?: obj["resets_at"])
        val remaining = if (status == "rate-limited" || used >= 100.0) 0.0 else usedToRemaining(used) ?: 0.0
        return QuotaSlot.of(
            remainingPercent = remaining,
            resetsAt = resetsAt,
            note = if (status == "rate-limited") "已达上限" else null,
        )
    }
}

// ---------------------------------------------------------------------------
// DeepSeek
// ---------------------------------------------------------------------------

internal object DeepSeekParser {
    fun parse(root: JsonElement): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val infos = rootObject.array("balance_infos")
            ?: rootObject.obj("data")?.array("balance_infos")
            ?: noPlanError("DeepSeek 没有返回余额信息")
        val balances = infos.mapNotNull { it.asObjectOrNull() }
        if (balances.isEmpty()) noPlanError("DeepSeek 没有返回余额信息")

        val selected = balances.firstOrNull { it.stringOrNull("currency") == "CNY" }
            ?: balances.firstOrNull { it.stringOrNull("currency") == "USD" }
            ?: balances.first()
        val amount = selected.doubleOrNull("total_balance", "totalBalance")
            ?: noPlanError("DeepSeek 余额字段无法解析")
        val currency = selected.stringOrNull("currency") ?: "CNY"
        val available = rootObject.booleanOrNull("is_available")
        return com.aiusage.app.data.model.ProviderUsage(
            planName = "按量付费",
            fiveHour = QuotaSlot.unsupported(),
            weekly = QuotaSlot.unsupported(),
            monthly = QuotaSlot.unsupported(),
            balance = BalanceSlot.of(
                amount = amount,
                currency = currency,
                note = if (available == false) "余额不足，API 可能已停用" else null,
            ),
            note = if (available == false) "余额不足" else null,
        )
    }
}

// ---------------------------------------------------------------------------
// Kimi
// ---------------------------------------------------------------------------

internal object KimiParser {
    fun isCodeKey(key: String): Boolean = key.trim().lowercase().startsWith("sk-kimi-")

    fun parseCodeUsage(root: JsonElement): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val data = rootObject.obj("data")
        val limits = rootObject.array("limits") ?: data?.array("limits").orEmpty()

        var fiveHour: QuotaSlot? = null
        var weekly: QuotaSlot? = null
        var monthly: QuotaSlot? = null

        for (element in limits) {
            val limit = element.asObjectOrNull() ?: continue
            val window = limit.obj("window") ?: continue
            val minutes = windowMinutes(window) ?: continue
            val detail = limit.obj("detail") ?: continue
            val slot = parseDetail(detail) ?: continue
            when {
                minutes in 1..360 -> if (fiveHour == null) fiveHour = slot
                minutes in 6 * 24 * 60..8 * 24 * 60 -> if (weekly == null) weekly = slot
                minutes >= 27 * 24 * 60 -> if (monthly == null) monthly = slot
            }
        }

        if (weekly == null) {
            (rootObject.obj("usage") ?: data?.obj("usage"))?.let { usage ->
                weekly = parseDetail(usage)
            }
        }

        if (fiveHour == null && weekly == null && monthly == null) {
            noPlanError("没有读取到 Kimi Code 额度")
        }

        val level = (rootObject.obj("user") ?: data?.obj("user"))
            ?.obj("membership")
            ?.stringOrNull("level")
            ?.let(::prettyPlan)
        val plan = if (level != null) "Kimi Code · $level" else "Kimi Code"

        return com.aiusage.app.data.model.ProviderUsage(
            planName = plan,
            fiveHour = fiveHour ?: QuotaSlot.unsupported(),
            weekly = weekly ?: QuotaSlot.unsupported(),
            monthly = monthly ?: QuotaSlot.unsupported(),
            balance = BalanceSlot.unsupported(),
        )
    }

    fun parseMoonshotBalance(root: JsonElement, currency: String): com.aiusage.app.data.model.ProviderUsage {
        val rootObject = root.asObjectOrNull() ?: parseError()
        val data = rootObject.obj("data") ?: rootObject
        val available = data.doubleOrNull("available_balance", "availableBalance")
            ?: noPlanError("没有读取到 Kimi 开放平台余额")
        return com.aiusage.app.data.model.ProviderUsage(
            planName = "Kimi 开放平台",
            fiveHour = QuotaSlot.unsupported(),
            weekly = QuotaSlot.unsupported(),
            monthly = QuotaSlot.unsupported(),
            balance = BalanceSlot.of(available, currency),
        )
    }

    private fun parseDetail(detail: JsonObject): QuotaSlot? {
        val limit = detail.doubleOrNull("limit", "total") ?: return null
        if (limit <= 0.0) return null
        val used = detail.doubleOrNull("used")
        val remaining = detail.doubleOrNull("remaining")
        val remainingPercent = when {
            used != null -> usedToRemaining((used / limit * 100.0).coerceIn(0.0, 100.0))
            remaining != null -> (remaining / limit * 100.0).coerceIn(0.0, 100.0)
            else -> null
        } ?: return null
        return QuotaSlot.of(
            remainingPercent = remainingPercent,
            resetsAt = parseTimestamp(
                detail["resetTime"] ?: detail["resetAt"] ?: detail["reset_time"] ?: detail["reset_at"],
            ),
        )
    }

    private fun windowMinutes(window: JsonObject): Long? {
        val duration = window.doubleOrNull("duration") ?: return null
        if (duration <= 0.0) return null
        val unit = window.stringOrNull("timeUnit", "time_unit")
            ?.lowercase()
            ?.removePrefix("time_unit_")
            ?.removeSuffix("s")
            ?: return null
        return when (unit) {
            "minute", "min" -> duration.toLong()
            "hour", "hr" -> (duration * 60).toLong()
            "day" -> (duration * 24 * 60).toLong()
            "week" -> (duration * 7 * 24 * 60).toLong()
            "month" -> (duration * 30 * 24 * 60).toLong()
            else -> null
        }
    }

    private fun prettyPlan(raw: String): String = raw
        .removePrefix("LEVEL_")
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
}

private fun com.aiusage.app.data.model.ProviderUsage.unsupportedText(): String = "不支持"
