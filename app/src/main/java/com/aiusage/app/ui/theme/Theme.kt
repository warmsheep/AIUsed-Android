package com.aiusage.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.aiusage.app.data.model.ThemeMode

val Ink = Color(0xFF07090F)
val Ink2 = Color(0xFF0B1020)
val CardDark = Color(0xFF121827)
val CardDarkElevated = Color(0xFF171E30)
val TileDark = Color(0xFF0E1422)
val HairlineDark = Color(0x14FFFFFF)
val TextHi = Color(0xFFF4F7FF)
val TextMid = Color(0xFFA5AFC4)
val TextLow = Color(0xFF6C7790)
val Mint = Color(0xFF41E6B0)
val MintDeep = Color(0xFF19C893)
val Violet = Color(0xFF8B7CFF)
val Blue = Color(0xFF5D8CFF)
val Amber = Color(0xFFFFB454)
val Coral = Color(0xFFFF615F)
val Rose = Color(0xFFFF4D6D)

val Paper = Color(0xFFF5F6FA)
val Paper2 = Color(0xFFECEEF6)
val CardLight = Color(0xFFFFFFFF)
val TileLight = Color(0xFFF4F6FB)
val HairlineLight = Color(0x14000000)
val TextHiLight = Color(0xFF141925)
val TextMidLight = Color(0xFF5A6478)
val TextLowLight = Color(0xFF7C8699)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF032018),
    primaryContainer = Color(0xFF123C31),
    onPrimaryContainer = Color(0xFFB9FFE7),
    secondary = Violet,
    onSecondary = Color(0xFF160E3A),
    tertiary = Blue,
    background = Ink,
    onBackground = TextHi,
    surface = CardDark,
    onSurface = TextHi,
    surfaceVariant = CardDarkElevated,
    onSurfaceVariant = TextMid,
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x18FFFFFF),
    error = Coral,
    onError = Color(0xFF2D0507),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0C9F78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F8EA),
    onPrimaryContainer = Color(0xFF003C2C),
    secondary = Color(0xFF5B4BD6),
    onSecondary = Color.White,
    tertiary = Color(0xFF3568DB),
    background = Paper,
    onBackground = TextHiLight,
    surface = CardLight,
    onSurface = TextHiLight,
    surfaceVariant = TileLight,
    onSurfaceVariant = TextMidLight,
    outline = Color(0x22000000),
    outlineVariant = Color(0x12000000),
    error = Color(0xFFD92D4A),
    onError = Color.White,
)

@Immutable
data class AiuPalette(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val cardElevated: Color,
    val tile: Color,
    val sheet: Color,
    val hairline: Color,
    val strongHairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val accent: Color,
    val accentSoft: Color,
    val violet: Color,
    val blue: Color,
    val amber: Color,
    val coral: Color,
)

private val DarkPalette = AiuPalette(
    isDark = true,
    backgroundTop = Ink,
    backgroundBottom = Ink2,
    card = Color(0xE6121827),
    cardElevated = Color(0xFF1A2133),
    tile = Color(0xB30E1422),
    sheet = Color(0xFF111726),
    hairline = Color(0x14FFFFFF),
    strongHairline = Color(0x26FFFFFF),
    textPrimary = TextHi,
    textSecondary = TextMid,
    textMuted = TextLow,
    track = Color(0xFF232B3D),
    accent = Mint,
    accentSoft = Color(0x1F41E6B0),
    violet = Violet,
    blue = Blue,
    amber = Amber,
    coral = Coral,
)

private val LightPalette = AiuPalette(
    isDark = false,
    backgroundTop = Color(0xFFF8F9FD),
    backgroundBottom = Color(0xFFEDF0F8),
    card = Color(0xF5FFFFFF),
    cardElevated = Color(0xFFFFFFFF),
    tile = Color(0xB3F3F5FA),
    sheet = Color(0xFFFFFFFF),
    hairline = Color(0x12000000),
    strongHairline = Color(0x1F000000),
    textPrimary = TextHiLight,
    textSecondary = TextMidLight,
    textMuted = Color(0xFF6F788B),
    track = Color(0xFFE3E7F0),
    accent = Color(0xFF0C9F78),
    accentSoft = Color(0x140C9F78),
    violet = Color(0xFF5B4BD6),
    blue = Color(0xFF3568DB),
    amber = Color(0xFFB86900),
    coral = Color(0xFFD92D4A),
)

val LocalAiuPalette = staticCompositionLocalOf { DarkPalette }

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        letterSpacing = (-1.2).sp,
        lineHeight = 42.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.8).sp,
        lineHeight = 38.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
    ),
)

@Composable
fun AiUsageTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalAiuPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
