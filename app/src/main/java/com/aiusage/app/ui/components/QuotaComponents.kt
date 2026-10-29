package com.aiusage.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.BalanceSlot
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.data.model.SlotState
import com.aiusage.app.ui.theme.LocalAiuPalette
import com.aiusage.app.util.formatCredits
import com.aiusage.app.util.formatMoney
import com.aiusage.app.util.formatPercent
import com.aiusage.app.util.formatResetCountdown

@Composable
fun GradientBar(
    percent: Double?,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
    enabled: Boolean = true,
) {
    val palette = LocalAiuPalette.current
    val target = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(
        targetValue = if (enabled) target else 0f,
        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        label = "quota-bar",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = palette.track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        if (animated > 0f) {
            val fillWidth = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(
                brush = quotaGradient(),
                size = Size(fillWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    slot: QuotaSlot,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    val supported = slot.state != SlotState.UNSUPPORTED
    val hasValue = supported && slot.remainingPercent != null && slot.state == SlotState.SUPPORTED
    val stale = slot.state == SlotState.ERROR
    val color = if (hasValue) {
        remainingTextColor(slot.remainingPercent, palette.isDark)
    } else {
        palette.textMuted
    }
    val value = when {
        slot.state == SlotState.SUPPORTED && slot.note == "不限量" -> "不限"
        hasValue -> formatPercent(slot.remainingPercent)
        slot.state == SlotState.ERROR -> "—"
        else -> "—"
    }
    val sub = when {
        slot.state == SlotState.SUPPORTED && slot.note == "不限量" -> "当前不限量"
        slot.state == SlotState.ERROR -> "更新失败"
        !supported -> slot.note ?: "未提供此额度"
        else -> slot.note ?: formatResetCountdown(slot.resetsAt) ?: "有效中"
    }
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (palette.isDark) palette.tile else palette.tile.copy(alpha = 0.7f))
            .border(1.dp, palette.hairline, RoundedCornerShape(17.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
            .alpha(if (stale) 0.62f else 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.weight(1f),
            )
            if (hasValue && (slot.remainingPercent ?: 100.0) <= 20.0) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(palette.coral),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            color = if (hasValue) color else palette.textMuted.copy(alpha = if (palette.isDark) 0.72f else 0.95f),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        GradientBar(
            percent = slot.remainingPercent,
            enabled = hasValue,
            height = 5.dp,
            modifier = Modifier.alpha(if (supported) 1f else 0.4f),
        )
    }
}

@Composable
fun BalanceTile(
    balance: BalanceSlot,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    val supported = balance.state != SlotState.UNSUPPORTED
    val hasValue = balance.state == SlotState.SUPPORTED && balance.amount != null
    val label = when {
        !supported -> "—"
        hasValue && balance.unit.isNotBlank() -> formatCredits(balance.amount!!, balance.unit)
        hasValue -> formatMoney(balance.amount!!, balance.currency)
        else -> "—"
    }
    val sub = when {
        balance.state == SlotState.ERROR -> "更新失败"
        !supported -> balance.note ?: "未提供余额"
        balance.note != null -> balance.note
        else -> "可用余额"
    }
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (palette.isDark) palette.tile else palette.tile.copy(alpha = 0.7f))
            .border(1.dp, palette.hairline, RoundedCornerShape(17.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
            .alpha(if (balance.state == SlotState.ERROR) 0.62f else 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "余额",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.weight(1f),
            )
            if (hasValue) {
                StatusDot(palette.accent, 5.dp)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (hasValue) palette.textPrimary else palette.textMuted.copy(alpha = if (palette.isDark) 0.72f else 0.95f),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = when {
                    label.length >= 11 -> 16.sp
                    label.length >= 9 -> 18.sp
                    else -> 23.sp
                },
                lineHeight = 27.sp,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub ?: "可用余额",
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .alpha(if (supported) 0.9f else 0.3f),
        ) {
            val radius = size.height / 2f
            if (hasValue) {
                drawRoundRect(
                    brush = balanceGradient(),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
            } else {
                drawRoundRect(
                    color = palette.track,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
            }
        }
    }
}

@Composable
fun OverallRing(
    percent: Double?,
    modifier: Modifier = Modifier,
    size: Dp = 126.dp,
    caption: String = "综合余量",
) {
    val palette = LocalAiuPalette.current
    val target = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "overall-ring",
    )
    val ringColor by animateColorAsState(
        targetValue = remainingTextColor(percent, palette.isDark),
        animationSpec = tween(600),
        label = "ring-color",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(size)) {
            val stroke = this.size.minDimension * 0.085f
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            rotate(135f, center) {
                drawArc(
                    color = palette.track.copy(alpha = 0.8f),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (animated > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = 0f,
                        sweepAngle = 270f * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (percent == null) "—" else formatPercent(percent),
                color = if (percent == null) palette.textMuted else palette.textPrimary,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (size > 110.dp) 29.sp else 25.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = caption,
                color = palette.textMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            )
        }
    }
}

@Composable
fun AggregateChip(label: String, percent: Double?, modifier: Modifier = Modifier) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.tile)
            .border(1.dp, palette.hairline, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StatusDot(remainingColor(percent), 6.dp)
        Text(
            text = label,
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        )
        Text(
            text = formatPercent(percent),
            color = palette.textPrimary,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

@Composable
fun BrandGlow(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width, 0f),
        )
    }
}
