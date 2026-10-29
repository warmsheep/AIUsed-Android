package com.aiusage.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.ui.theme.LocalAiuPalette
import com.aiusage.app.ui.theme.Mint

data class BrandColors(
    val start: Color,
    val end: Color,
    val foreground: Color = Color.White,
    val monogram: String,
)

fun brandFor(kind: ProviderKind): BrandColors = when (kind) {
    ProviderKind.ZHIPU -> BrandColors(Color(0xFF3C6BFF), Color(0xFF7A4DFF), monogram = "智")
    ProviderKind.MINIMAX -> BrandColors(Color(0xFFFF7A4D), Color(0xFFFF4D6D), monogram = "M")
    ProviderKind.OPENCODE_GO -> BrandColors(Color(0xFF353D4E), Color(0xFF0C101A), monogram = "OC")
    ProviderKind.DEEPSEEK -> BrandColors(Color(0xFF4169FF), Color(0xFF79A6FF), monogram = "D")
    ProviderKind.KIMI -> BrandColors(Color(0xFF7A5CFF), Color(0xFFB65CFF), monogram = "K")
}

// Green starts at 56% remaining, so 60%+ is safely green. The green band is
// 44% of the scale, matching the "remaining 60% is the safe zone" rule.
private val QuotaStops = listOf(
    0.0f to Color(0xFFFF4D5E),
    0.18f to Color(0xFFFF6B4A),
    0.36f to Color(0xFFFFA23A),
    0.50f to Color(0xFFFFD34E),
    0.60f to Color(0xFF41E6B0),
    1.0f to Color(0xFF19C893),
)

private val LightQuotaStops = listOf(
    0.0f to Color(0xFFC6283E),
    0.18f to Color(0xFFC4471E),
    0.36f to Color(0xFFA85A00),
    0.50f to Color(0xFF8A6A00),
    0.60f to Color(0xFF0C9F78),
    1.0f to Color(0xFF067A5C),
)

private fun colorFromStops(stops: List<Pair<Float, Color>>, percent: Double): Color {
    val p = percent.coerceIn(0.0, 100.0).toFloat() / 100f
    var from = stops.first()
    var to = stops.last()
    for (index in 0 until stops.lastIndex) {
        if (p >= stops[index].first && p <= stops[index + 1].first) {
            from = stops[index]
            to = stops[index + 1]
            break
        }
    }
    val span = (to.first - from.first).takeIf { it > 0f } ?: 1f
    return lerp(from.second, to.second, (p - from.first) / span)
}

fun remainingColor(percent: Double?): Color {
    if (percent == null) return Color(0xFF6C7790)
    return colorFromStops(QuotaStops, percent)
}

/** Full red→green gradient; green starts at 60% remaining (40% of the scale). */
/** Same bright red→green ramp in both themes; light mode keeps dark text for contrast. */
fun quotaGradient(): Brush = Brush.horizontalGradient(QuotaStops.map { it.second })

/** Exact color stops used by the widget bitmap so both bars match. */
fun quotaColorStops(): List<Pair<Float, Color>> = QuotaStops

/** Darker red→green ramp that keeps contrast on light cards. */
fun remainingTextColor(percent: Double?, isDark: Boolean): Color {
    if (percent == null) return Color(0xFF6C7790)
    return if (isDark) remainingColor(percent) else colorFromStops(LightQuotaStops, percent)
}

fun balanceGradient(): Brush = Brush.horizontalGradient(
    listOf(Color(0xFF5D8CFF), Color(0xFF5FC7E8), Mint),
)

fun brandGradient(brand: BrandColors): Brush =
    Brush.linearGradient(listOf(brand.start, brand.end))

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val palette = LocalAiuPalette.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(palette.backgroundTop, palette.backgroundBottom),
            ),
        )
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.violet.copy(alpha = if (palette.isDark) 0.22f else 0.13f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.86f, h * 0.08f),
                radius = w * 0.72f,
            ),
            radius = w * 0.72f,
            center = Offset(w * 0.86f, h * 0.08f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.accent.copy(alpha = if (palette.isDark) 0.12f else 0.10f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.05f, h * 0.38f),
                radius = w * 0.58f,
            ),
            radius = w * 0.58f,
            center = Offset(w * 0.05f, h * 0.38f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.blue.copy(alpha = if (palette.isDark) 0.08f else 0.08f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.98f),
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.5f, h * 0.98f),
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalAiuPalette.current
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.32f else 0.08f),
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.35f else 0.10f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.card,
                        if (palette.isDark) palette.card.copy(alpha = 0.82f) else palette.card,
                    ),
                ),
            )
            .border(1.dp, palette.hairline, shape)
            .then(clickModifier),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun ProviderBadge(
    kind: ProviderKind,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val brand = brandFor(kind)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(brandGradient(brand))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = brand.monogram,
            color = brand.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = if (brand.monogram.length > 1) (size.value * 0.28f).sp else (size.value * 0.42f).sp,
            letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    size: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val palette = LocalAiuPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (palette.isDark) 0.13f else 0.10f))
            .border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
        } else {
            StatusDot(color, 6.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = LocalAiuPalette.current.textMuted,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val palette = LocalAiuPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(Mint, Color(0xFF8BF2D2)))
                } else {
                    Brush.horizontalGradient(listOf(palette.track, palette.track))
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color(0xFF05271D) else palette.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) Color(0xFF05271D) else palette.textMuted,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}
