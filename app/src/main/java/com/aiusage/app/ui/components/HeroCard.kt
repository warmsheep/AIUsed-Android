package com.aiusage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.ui.theme.LocalAiuPalette
import com.aiusage.app.util.formatPercent
import com.aiusage.app.util.formatUpdatedAt

@Composable
fun HeroCard(
    state: AppState,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val palette = LocalAiuPalette.current
    val windows = state.providers.flatMap { provider ->
        val snapshot = state.snapshots[provider.id] ?: return@flatMap emptyList()
        listOf(snapshot.fiveHour, snapshot.weekly, snapshot.monthly)
            .filter { it.state == SlotState.SUPPORTED && it.remainingPercent != null }
    }
    val overall = windows.takeIf { it.isNotEmpty() }
        ?.mapNotNull { it.remainingPercent }
        ?.average()
    val updatedAt = state.snapshots.values.maxOfOrNull { it.fetchedAt } ?: 0L
    val attention = state.attentionProviderCount
    val healthy = state.healthyProviderCount
    val statusText = when {
        state.providers.isEmpty() -> "等待配置"
        attention == 0 -> "全部正常"
        else -> "$attention 项需关注"
    }
    val statusColor = when {
        state.providers.isEmpty() -> palette.textMuted
        attention == 0 -> palette.accent
        else -> palette.amber
    }

    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
    ) {
        Box {
            BrandGlow(palette.violet, Modifier.matchParentSize())
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionEyebrow("OVERVIEW")
                    Spacer(Modifier.weight(1f))
                    StatusPill(statusText, statusColor)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "综合余量",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (overall == null) "—" else formatPercent(overall),
                                color = if (overall == null) palette.textMuted else palette.textPrimary,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 44.sp,
                                    lineHeight = 48.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                            if (overall != null) {
                                Text(
                                    text = " 剩余",
                                    color = palette.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 9.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (windows.isEmpty()) {
                                if (state.providers.isEmpty()) "添加供应商后开始统计"
                                else "当前供应商仅提供余额"
                            } else {
                                "基于 ${windows.size} 个可用额度"
                            },
                            color = palette.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OverallRing(percent = overall, size = 116.dp)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(palette.tile.copy(alpha = 0.7f))
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroStat(
                        value = state.providers.size.toString(),
                        label = "供应商",
                        modifier = Modifier.weight(1f),
                    )
                    HeroDivider()
                    HeroStat(
                        value = healthy.toString(),
                        label = "健康",
                        valueColor = palette.accent,
                        modifier = Modifier.weight(1f),
                    )
                    HeroDivider()
                    HeroStat(
                        value = attention.toString(),
                        label = "关注",
                        valueColor = if (attention > 0) palette.amber else palette.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    HeroDivider()
                    Column(
                        modifier = Modifier.weight(1.35f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (updatedAt > 0) {
                                formatUpdatedAt(updatedAt, now).removeSuffix("更新")
                            } else {
                                "—"
                            },
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = "最近更新",
                            color = palette.textMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val palette = LocalAiuPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = valueColor ?: palette.textPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = label,
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .height(24.dp)
            .width(1.dp)
            .background(LocalAiuPalette.current.hairline),
    )
}

@Composable
fun MiniProviderStack(state: AppState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        state.providers.take(4).forEach { provider ->
            val brand = brandFor(provider.kind)
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(brand.start, brand.end)))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = brand.monogram.take(1),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
