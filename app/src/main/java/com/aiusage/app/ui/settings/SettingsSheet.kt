package com.aiusage.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.BuildConfig
import com.aiusage.app.data.model.AppSettings
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.ui.components.SectionEyebrow
import com.aiusage.app.ui.components.StatusDot
import com.aiusage.app.ui.theme.LocalAiuPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onRefreshChange: (Int) -> Unit,
) {
    val palette = LocalAiuPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.sheet,
        contentColor = palette.textPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.strongHairline),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("设置", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "让数据刷新和展示更贴合你的习惯",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.tile),
                ) {
                    Icon(Icons.Rounded.Close, "关闭", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionEyebrow("外观")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    OptionTile(
                        text = mode.label,
                        selected = settings.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionEyebrow("自动刷新")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    OptionTile(
                        text = if (minutes >= 60) "${minutes / 60} 小时" else "$minutes 分钟",
                        selected = settings.refreshMinutes == minutes,
                        onClick = { onRefreshChange(minutes) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "系统后台调度会尽量按此间隔刷新，省电模式下可能略有延迟。",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            )

            Spacer(Modifier.height(22.dp))
            SectionEyebrow("桌面小组件")
            Spacer(Modifier.height(9.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.tile.copy(alpha = 0.6f))
                    .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "小米 HyperOS / 一加 ColorOS",
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                )
                Spacer(Modifier.height(6.dp))
                WidgetStep("1", "长按桌面空白处，进入「添加小部件」")
                WidgetStep("2", "搜索「AI用量」，选择 4×2 小组件")
                WidgetStep("3", "拖到桌面即可，大小可自由缩放")
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "小组件使用系统标准 AppWidget，两个品牌桌面都原生支持。",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 14.sp),
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionEyebrow("安全与数据")
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.tile.copy(alpha = 0.55f))
                    .padding(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "API Key 使用 Android Keystore 的 AES-GCM 加密后保存在本机，不会上传到任何第三方服务器；请求只直接发往各供应商官方接口。",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "AI用量",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    )
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} · 为小米与一加桌面优化",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    )
                }
                StatusDot(palette.accent, 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "本地优先",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun OptionTile(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accentSoft else palette.tile.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) palette.accent.copy(alpha = 0.65f) else palette.hairline,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) palette.accent else palette.textSecondary,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetStep(number: String, text: String) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = Modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = palette.accent,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
        )
    }
}
