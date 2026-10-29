package com.aiusage.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.AppState
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.ui.components.AuroraBackground
import com.aiusage.app.ui.components.DashedAddCard
import com.aiusage.app.ui.components.GlassCard
import com.aiusage.app.ui.components.HeroCard
import com.aiusage.app.ui.components.ProviderCard
import com.aiusage.app.ui.components.SectionEyebrow
import com.aiusage.app.ui.components.StatusDot
import com.aiusage.app.ui.theme.LocalAiuPalette
import kotlinx.coroutines.delay

private enum class ProviderFilter { ALL, ATTENTION }

@Composable
fun HomeScreen(
    state: AppState,
    snackbarHostState: SnackbarHostState,
    onRefreshAll: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ProviderConfig) -> Unit,
    onRemove: (ProviderConfig) -> Unit,
    onRefreshProvider: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalAiuPalette.current
    var filter by remember { mutableStateOf(ProviderFilter.ALL) }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val attentionProviders = remember(state.providers, state.snapshots) {
        state.providers.filter { provider ->
            val snapshot = state.snapshots[provider.id]
            snapshot == null || snapshot.error != null || snapshot.let { s ->
                listOf(s.fiveHour, s.weekly, s.monthly).any {
                    it.state == SlotState.SUPPORTED && (it.remainingPercent ?: 100.0) <= 20.0
                }
            }
        }
    }
    val shownProviders = when (filter) {
        ProviderFilter.ALL -> state.providers
        ProviderFilter.ATTENTION -> attentionProviders
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("header") {
                HomeHeader(
                    refreshing = state.refreshingAll,
                    onRefresh = onRefreshAll,
                    onSettings = onSettings,
                )
            }

            if (state.providers.isEmpty()) {
                item("empty") {
                    EmptyState(onAdd = onAdd)
                }
            } else {
                item("hero") {
                    HeroCard(state = state, now = now)
                }
                item("providers-header") {
                    ProvidersHeader(
                        total = state.providers.size,
                        attention = attentionProviders.size,
                        filter = filter,
                        onFilterChange = { filter = it },
                    )
                }
                if (shownProviders.isEmpty()) {
                    item("no-attention") {
                        GlassCard(
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(palette.accentSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text(
                                        text = "所有额度都在健康区间",
                                        color = palette.textPrimary,
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    )
                                    Text(
                                        text = "没有供应商需要关注，继续保持。",
                                        color = palette.textMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                items(shownProviders, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        snapshot = state.snapshots[provider.id],
                        isRefreshing = provider.id in state.refreshing,
                        now = now,
                        onRefresh = { onRefreshProvider(provider.id) },
                        onEdit = { onEdit(provider) },
                        onRemove = { onRemove(provider) },
                    )
                }
                item("add") {
                    DashedAddCard(onClick = onAdd)
                }
            }

            item("footer") {
                FooterNote()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = palette.cardElevated,
                contentColor = palette.textPrimary,
                shape = RoundedCornerShape(16.dp),
                actionColor = palette.accent,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(palette.accent, 5.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "QUOTA DASHBOARD",
                    color = palette.accent,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.6.sp,
                        fontSize = 9.sp,
                    ),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "AI用量",
                color = palette.textPrimary,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 33.sp,
                    letterSpacing = (-1.2).sp,
                ),
            )
            Text(
                text = "所有 AI 额度，一处掌握",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HeaderIconButton(
            onClick = onRefresh,
            enabled = !refreshing,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
            } else {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新全部",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(onClick = onSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = palette.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = LocalAiuPalette.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.card)
            .border(1.dp, palette.hairline, CircleShape)
            .then(
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(ColorHighlight, androidx.compose.ui.graphics.Color.Transparent),
                    ),
                ),
            ),
    ) {
        content()
    }
}

@Composable
private fun ProvidersHeader(
    total: Int,
    attention: Int,
    filter: ProviderFilter,
    onFilterChange: (ProviderFilter) -> Unit,
) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionEyebrow("PROVIDERS")
            Spacer(Modifier.height(2.dp))
            Text(
                text = "我的供应商",
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.card.copy(alpha = 0.75f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilterPill(
                text = "全部 $total",
                selected = filter == ProviderFilter.ALL,
                onClick = { onFilterChange(ProviderFilter.ALL) },
            )
            FilterPill(
                text = "关注 $attention",
                selected = filter == ProviderFilter.ATTENTION,
                onClick = { onFilterChange(ProviderFilter.ATTENTION) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalAiuPalette.current
    Text(
        text = text,
        color = if (selected) palette.textPrimary else palette.textMuted,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.tile else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun FooterNote() {
    val palette = LocalAiuPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(palette.textMuted.copy(alpha = 0.5f), 4.dp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = "数据来自供应商官方接口 · 密钥仅保存在本机",
                color = palette.textMuted.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点击右上角设置可调整刷新频率",
            color = palette.textMuted.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
        )
    }
}

private val ColorHighlight = androidx.compose.ui.graphics.Color(0x0AFFFFFF)
