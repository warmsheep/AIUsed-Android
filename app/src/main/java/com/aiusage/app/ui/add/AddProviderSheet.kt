package com.aiusage.app.ui.add

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.Region
import com.aiusage.app.ui.components.GradientButton
import com.aiusage.app.ui.components.ProviderBadge
import com.aiusage.app.ui.components.SectionEyebrow
import com.aiusage.app.ui.components.StatusDot
import com.aiusage.app.ui.theme.LocalAiuPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderSheet(
    editing: ProviderConfig? = null,
    existingKinds: Set<ProviderKind>,
    onSubmit: suspend (ProviderKind, String, Region) -> Result<Unit>,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val palette = LocalAiuPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var kind by remember { mutableStateOf(editing?.kind) }
    var region by remember { mutableStateOf(editing?.region ?: Region.AUTO) }
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val supportsRegion = kind == ProviderKind.ZHIPU || kind == ProviderKind.MINIMAX || kind == ProviderKind.KIMI
    val canSubmit = !loading && kind != null && (editing != null || key.trim().length >= 8)

    if (confirmRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("移除供应商？") },
            text = { Text("将删除本机保存的密钥和额度缓存，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) { Text("移除", color = palette.coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("取消") }
            },
            containerColor = palette.cardElevated,
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!loading) onDismiss() },
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
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (editing == null) "添加供应商" else "更新密钥",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (editing == null) "连接你的 AI 套餐，开始追踪每一份额度" else "修改密钥或区域后会立即重新验证",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(
                    onClick = { if (!loading) onDismiss() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.tile),
                ) {
                    Icon(Icons.Rounded.Close, "关闭", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionEyebrow("选择供应商")
            Spacer(Modifier.height(9.dp))
            if (editing == null) {
                ProviderGrid(
                    selected = kind,
                    disabled = existingKinds,
                    onSelect = {
                        kind = it
                        error = null
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.tile)
                        .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadge(editing.kind, size = 40.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = editing.kind.displayName,
                            color = palette.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = editing.kind.tagline,
                            color = palette.textMuted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        )
                    }
                    StatusDot(palette.accent, 7.dp)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionEyebrow("API KEY")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontSize = 14.sp,
                ),
                label = {
                    Text(
                        text = if (editing == null) "粘贴密钥" else "新密钥（留空则保持不变）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                placeholder = {
                    Text(
                        text = if (kind == ProviderKind.MINIMAX) "sk-cp-…" else if (kind == ProviderKind.KIMI) "sk-kimi-… 或 sk-…" else "sk-…",
                        color = palette.textMuted.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                key = pasteFromClipboard(context).orEmpty()
                                error = null
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text("粘贴", color = palette.accent, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp))
                        }
                        TextButton(
                            onClick = { reveal = !reveal },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(
                                text = if (reveal) "隐藏" else "显示",
                                color = palette.textMuted,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                            )
                        }
                    }
                },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent.copy(alpha = 0.75f),
                    unfocusedBorderColor = palette.hairline,
                    focusedContainerColor = palette.tile.copy(alpha = 0.6f),
                    unfocusedContainerColor = palette.tile.copy(alpha = 0.45f),
                    cursorColor = palette.accent,
                    focusedLabelColor = palette.accent,
                    unfocusedLabelColor = palette.textMuted,
                ),
            )

            if (supportsRegion) {
                Spacer(Modifier.height(16.dp))
                SectionEyebrow("接口区域")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Region.entries.forEach { option ->
                        SegmentChip(
                            text = option.label,
                            selected = region == option,
                            onClick = {
                                region = option
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "自动会依次尝试国内与国际接口，通常无需手动选择",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                )
            }

            Spacer(Modifier.height(14.dp))
            ProviderHint(kind)
            if (editing == null && kind != null && kind in existingKinds) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "该供应商已添加，可在对应卡片的菜单中选择「更新密钥」",
                    color = palette.amber,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.coral.copy(alpha = 0.09f))
                        .border(1.dp, palette.coral.copy(alpha = 0.2f), RoundedCornerShape(13.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(palette.coral, 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error!!,
                        color = palette.coral,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = when {
                    loading -> "正在验证…"
                    editing == null -> "验证并保存"
                    else -> "保存并刷新"
                },
                onClick = {
                    val selected = kind ?: return@GradientButton
                    scope.launch {
                        loading = true
                        error = null
                        val result = try {
                            onSubmit(selected, key, region)
                        } catch (failure: Exception) {
                            Result.failure(failure)
                        }
                        loading = false
                        result.exceptionOrNull()?.let {
                            error = it.message ?: "验证失败，请检查密钥"
                        }
                        if (result.isSuccess) onDismiss()
                    }
                },
                enabled = canSubmit,
                icon = null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = palette.accent,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = palette.textMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "密钥经 Android Keystore 加密，只保存在本机",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                    modifier = Modifier.weight(1f),
                )
                if (editing != null && onRemove != null) {
                    Text(
                        text = "移除",
                        color = palette.coral,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { confirmRemove = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderGrid(
    selected: ProviderKind?,
    disabled: Set<ProviderKind>,
    onSelect: (ProviderKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderKind.entries.chunked(2).forEach { rowKinds ->
            if (rowKinds.size == 1) {
                ProviderOption(
                    kind = rowKinds.first(),
                    selected = selected == rowKinds.first(),
                    disabled = rowKinds.first() in disabled,
                    onClick = { onSelect(rowKinds.first()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKinds.forEach { kind ->
                        ProviderOption(
                            kind = kind,
                            selected = selected == kind,
                            disabled = kind in disabled,
                            onClick = { onSelect(kind) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOption(
    kind: ProviderKind,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (selected) palette.accentSoft else palette.tile.copy(alpha = 0.55f),
            )
            .border(
                1.dp,
                if (selected) palette.accent.copy(alpha = 0.7f) else palette.hairline,
                RoundedCornerShape(17.dp),
            )
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderBadge(kind, size = 32.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = kind.displayName,
                color = if (disabled) palette.textMuted else palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (disabled) "已添加" else kind.tagline,
                color = if (disabled) palette.textMuted.copy(alpha = 0.6f) else palette.textMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            StatusDot(palette.accent, 6.dp)
        }
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    Box(
        modifier = modifier
            .height(38.dp)
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
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ProviderHint(kind: ProviderKind?) {
    val palette = LocalAiuPalette.current
    if (kind == null) return
    val hint = when (kind) {
        ProviderKind.ZHIPU -> "读取 GLM Coding Plan 的 5 小时与周额度；个人套餐通常没有月额度与余额。"
        ProviderKind.MINIMAX -> "使用 M Plan 订阅 Key（sk-cp-…），读取 5 小时、周额度与积分余额。"
        ProviderKind.OPENCODE_GO -> "读取 Go 订阅的 5 小时、周、月额度；额外余额需在 Console 查看。"
        ProviderKind.DEEPSEEK -> "DeepSeek 开放平台只提供账户余额，额度窗口会保持置灰。"
        ProviderKind.KIMI -> "sk-kimi- 开头为 Kimi Code Key，读取额度；其他 Key 读取开放平台余额。"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.tile.copy(alpha = 0.55f))
            .border(1.dp, palette.hairline, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(palette.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = hint,
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
        )
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.ifBlank { null }
}
