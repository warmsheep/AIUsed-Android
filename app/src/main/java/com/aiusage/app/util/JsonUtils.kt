package com.aiusage.app.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonObject.obj(vararg names: String): JsonObject? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonObject) return value
    }
    return null
}

internal fun JsonObject.array(vararg names: String): JsonArray? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonArray) return value
    }
    return null
}

internal fun JsonObject.stringOrNull(vararg names: String): String? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonPrimitive && value !is JsonNull && value.isString) {
            return value.content.trim().ifEmpty { null }
        }
        if (value is JsonPrimitive && value !is JsonNull) {
            return value.content.trim().ifEmpty { null }
        }
    }
    return null
}

internal fun JsonObject.doubleOrNull(vararg names: String): Double? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonPrimitive && value !is JsonNull) {
            val parsed = value.content.toDoubleOrNull()
            if (parsed != null && parsed.isFinite()) return parsed
        }
    }
    return null
}

internal fun JsonObject.longOrNull(vararg names: String): Long? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonPrimitive && value !is JsonNull) {
            val text = value.content.trim()
            val parsed = text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
            if (parsed != null) return parsed
        }
    }
    return null
}

internal fun JsonObject.booleanOrNull(vararg names: String): Boolean? {
    for (name in names) {
        val value = this[name] ?: continue
        if (value is JsonPrimitive && value !is JsonNull) {
            when (value.content.trim().lowercase()) {
                "true", "1" -> return true
                "false", "0" -> return false
            }
        }
    }
    return null
}

/**
 * Providers report timestamps as ISO-8601, epoch seconds, or epoch millis.
 * Normalise everything to epoch millis, returning null for unusable values.
 */
internal fun parseTimestamp(value: JsonElement?): Long? {
    if (value == null || value is JsonNull) return null
    if (value is JsonPrimitive) {
        val text = value.content.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return normaliseEpoch(it) }
        text.toDoubleOrNull()?.let { return normaliseEpoch(it.toLong()) }
        runCatching { return Instant.parse(text).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return java.time.LocalDateTime.parse(text).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    }
    return null
}

internal fun normaliseEpoch(value: Long): Long? {
    if (value <= 0L) return null
    return when {
        value < 100_000_000_000L -> value * 1000L // seconds
        else -> value
    }
}

/** Converts a "used" percentage into the "remaining" percentage shown in the UI. */
internal fun usedToRemaining(usedPercent: Double?): Double? =
    usedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }

internal fun clampPercent(value: Double?): Double? = value?.coerceIn(0.0, 100.0)

internal fun JsonObject.percent(vararg names: String): Double? = clampPercent(doubleOrNull(*names))

internal fun formatPercent(value: Double?): String {
    if (value == null) return "—"
    val rounded = value.roundToInt()
    return if (kotlin.math.abs(value - rounded) < 0.05) {
        "$rounded%"
    } else {
        String.format(java.util.Locale.US, "%.1f%%", value)
    }
}

internal fun formatMoney(amount: Double, currency: String): String {
    val symbol = when (currency.uppercase()) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "JPY" -> "¥"
        else -> ""
    }
    val formatted = java.text.DecimalFormat(
        "#,##0.00",
        java.text.DecimalFormatSymbols(java.util.Locale.US),
    ).format(amount)
    return if (symbol.isNotEmpty()) "$symbol$formatted" else "$formatted ${currency.uppercase()}".trim()
}

internal fun formatCredits(amount: Double, unit: String): String {
    val formatted = java.text.DecimalFormat(
        "#,##0.##",
        java.text.DecimalFormatSymbols(java.util.Locale.US),
    ).format(amount)
    return "$formatted $unit".trim()
}
