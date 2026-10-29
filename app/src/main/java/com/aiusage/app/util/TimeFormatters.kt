package com.aiusage.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private fun timeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private fun dateTimeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.getDefault())

fun formatUpdatedAt(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return "尚未更新"
    val delta = max(0L, now - epochMillis)
    return when {
        delta < 45_000L -> "刚刚更新"
        delta < 60 * 60_000L -> "${delta / 60_000L} 分钟前"
        isSameDay(epochMillis, now) -> "今天 ${timeFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))}"
        else -> dateTimeFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }
}

fun formatResetCountdown(epochMillis: Long?, now: Long = System.currentTimeMillis()): String? {
    if (epochMillis == null || epochMillis <= 0L) return null
    val delta = epochMillis - now
    if (delta <= -60_000L) return "已重置"
    if (delta <= 60_000L) return "即将重置"
    val minutes = delta / 60_000L
    return when {
        minutes < 60 -> "${minutes}分后重置"
        minutes < 24 * 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "${h}小时后重置" else "${h}小时${m}分后重置"
        }
        else -> {
            val days = minutes / (24 * 60)
            val hours = (minutes % (24 * 60)) / 60
            if (hours == 0L) "${days}天后重置" else "${days}天${hours}小时后重置"
        }
    }
}

fun formatResetAbsolute(epochMillis: Long?): String? {
    if (epochMillis == null || epochMillis <= 0L) return null
    return dateTimeFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}
