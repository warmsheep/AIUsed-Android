package com.aiusage.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.R
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.ui.components.GradientButton
import com.aiusage.app.ui.components.ProviderBadge
import com.aiusage.app.ui.components.StatusDot
import com.aiusage.app.ui.theme.LocalAiuPalette
import com.aiusage.app.ui.theme.Mint

@Composable
fun EmptyState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAiuPalette.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuotaOrbit()
        Spacer(Modifier.height(10.dp))
        Text(
            text = "把 AI 额度，装进口袋",
            color = palette.textPrimary,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "连接智谱、MiniMax、OpenCode、DeepSeek 与 Kimi，\n5 小时、周、月额度与余额一处看清。",
            color = palette.textMuted,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(22.dp))
        GradientButton(
            text = "添加第一个供应商",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeatureDot("官方接口")
            Spacer(Modifier.width(12.dp))
            FeatureDot("本地加密")
            Spacer(Modifier.width(12.dp))
            FeatureDot("桌面小组件")
        }
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.widget_preview),
            contentDescription = "桌面小组件预览",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, palette.hairline, RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "桌面小组件 · 小米 HyperOS / 一加 ColorOS 通用",
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureDot(text: String) {
    val palette = LocalAiuPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(palette.accent.copy(alpha = 0.8f), 4.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = palette.textMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
        )
    }
}

@Composable
private fun QuotaOrbit() {
    val palette = LocalAiuPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width * 0.38f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.violet.copy(alpha = 0.15f), Color.Transparent),
                    center = center,
                    radius = size.width * 0.52f,
                ),
                radius = size.width * 0.52f,
                center = center,
            )
            drawCircle(
                color = palette.hairline,
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = palette.hairline.copy(alpha = 0.6f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Mint.copy(alpha = 0.0f), Mint.copy(alpha = 0.75f), Mint.copy(alpha = 0.0f))),
                startAngle = 200f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            val nodes = listOf(
                0.0 to 1.0,
                0.8 to 0.2,
                1.0 to 0.9,
                0.0 to 0.7,
            )
            nodes.forEach { (x, y) ->
                drawCircle(
                    color = palette.textMuted.copy(alpha = 0.2f),
                    radius = 2.dp.toPx(),
                    center = Offset(center.x + (x.toFloat() - 0.5f) * radius * 1.55f, center.y + (y.toFloat() - 0.5f) * radius * 1.55f),
                )
            }
        }
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(palette.cardElevated, palette.backgroundTop),
                    ),
                )
                .border(1.dp, palette.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = palette.accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        OrbitBadge(ProviderKind.ZHIPU, Modifier.offset(x = (-88).dp, y = (-82).dp))
        OrbitBadge(ProviderKind.MINIMAX, Modifier.offset(x = (80).dp, y = (-76).dp))
        OrbitBadge(ProviderKind.OPENCODE_GO, Modifier.offset(x = (92).dp, y = (58).dp))
        OrbitBadge(ProviderKind.DEEPSEEK, Modifier.offset(x = (-92).dp, y = (62).dp))
        OrbitBadge(ProviderKind.KIMI, Modifier.offset(x = (0).dp, y = (102).dp))
    }
}

@Composable
private fun OrbitBadge(kind: ProviderKind, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .clip(CircleShape)
            .background(LocalAiuPalette.current.cardElevated.copy(alpha = 0.9f))
            .padding(4.dp),
    ) {
        ProviderBadge(kind, size = 34.dp)
    }
}
