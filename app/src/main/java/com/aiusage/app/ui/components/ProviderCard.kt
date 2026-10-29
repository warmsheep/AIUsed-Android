package com.aiusage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.data.model.UsageSnapshot
import com.aiusage.app.ui.theme.LocalAiuPalette
import com.aiusage.app.util.formatUpdatedAt

@Composable
fun ProviderCard(
    provider: ProviderConfig,
    snapshot: UsageSnapshot?,
    isRefreshing: Boolean,
    now: Long = System.currentTimeMillis(),
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    val hasData = snapshot?.hasData == true
    val hasError = snapshot?.error != null
    val low = snapshot != null && listOf(snapshot.fiveHour, snapshot.weekly, snapshot.monthly)
        .any { it.state == SlotState.SUPPORTED && (it.remainingPercent ?: 100.0) <= 20.0 }
    val status = when {
        isRefreshing -> "更新中" to palette.amber
        hasError && hasData -> "数据过期" to palette.amber
        hasError -> "异常" to palette.coral
        !hasData -> "待更新" to palette.textMuted
        low -> "额度偏低" to palette.amber
        else -> "正常" to palette.accent
    }
    val planText = snapshot?.planName
        ?.takeIf { it.isNotBlank() && !it.equals(provider.kind.displayName, ignoreCase = true) }
        ?: provider.kind.tagline
    val regionText = when (provider.region) {
        Region.AUTO -> null
        Region.CN -> "国内"
        Region.GLOBAL -> "国际"
    }

    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            if (isRefreshing) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(palette.amber.copy(alpha = 0.7f)),
                )
            }
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadge(provider.kind, size = 42.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.kind.displayName,
                                color = palette.textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                            )
                            if (regionText != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = regionText,
                                    color = palette.textMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(palette.tile)
                                        .border(1.dp, palette.hairline, RoundedCornerShape(50))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = planText,
                            color = palette.textMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(status.first, status.second)
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.tile.copy(alpha = 0.55f)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多操作",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = palette.cardElevated,
                        ) {
                            DropdownMenuItem(
                                text = { Text("立即刷新", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    onRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("更新密钥", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("移除供应商", style = MaterialTheme.typography.bodyMedium.copy(color = palette.coral)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, null, tint = palette.coral, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }

                if (hasError) {
                    Spacer(Modifier.height(11.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.coral.copy(alpha = if (palette.isDark) 0.09f else 0.06f))
                            .border(1.dp, palette.coral.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onRefresh)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = palette.coral,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = snapshot?.error ?: "更新失败",
                            color = palette.coral.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "重试",
                            color = palette.coral,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "5小时余量",
                        slot = snapshot?.fiveHour ?: com.aiusage.app.data.model.QuotaSlot.unsupported(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "本周余量",
                        slot = snapshot?.weekly ?: com.aiusage.app.data.model.QuotaSlot.unsupported(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "本月余量",
                        slot = snapshot?.monthly ?: com.aiusage.app.data.model.QuotaSlot.unsupported(),
                        modifier = Modifier.weight(1f),
                    )
                    BalanceTile(
                        balance = snapshot?.balance ?: com.aiusage.app.data.model.BalanceSlot.unsupported(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderRefreshStamp(snapshot?.fetchedAt ?: 0L, now = now)
                    Spacer(Modifier.weight(1f))
                    if (snapshot?.note != null) {
                        Text(
                            text = snapshot.note,
                            color = palette.textMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderRefreshStamp(fetchedAt: Long, now: Long = System.currentTimeMillis()) {
    Text(
        text = formatUpdatedAt(fetchedAt, now),
        color = LocalAiuPalette.current.textMuted,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        maxLines = 1,
    )
}

@Composable
fun DashedAddCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.card.copy(alpha = 0.55f))
            .border(1.dp, palette.strongHairline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = palette.accent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "添加供应商",
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            )
            Text(
                text = "智谱 · MiniMax · OpenCode · DeepSeek · Kimi",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "→",
            color = palette.textMuted,
            fontSize = 18.sp,
        )
    }
}
