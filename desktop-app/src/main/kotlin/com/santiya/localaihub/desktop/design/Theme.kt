package com.santiya.localaihub.desktop.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.santiya.localaihub.desktop.resources.Res
import com.santiya.localaihub.desktop.resources.manrope
import com.santiya.localaihub.desktop.resources.maple_mono
import com.santiya.localaihub.desktop.state.ThemePreset
import org.jetbrains.compose.resources.Font

private val fallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7A8FF),
    onPrimary = Color(0xFF20114C),
    secondary = Color(0xFFC9B6FF),
    tertiary = Color(0xFFF496B7),
    background = Color(0xFF0B0B12),
    surface = Color(0xFF11121B),
    surfaceVariant = Color(0xFF1B1A26),
    primaryContainer = Color(0xFF2E2361),
    secondaryContainer = Color(0xFF302545),
    tertiaryContainer = Color(0xFF4A2235),
    outline = Color(0xFF716986)
)

private val fallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF175CD6),
    onPrimary = Color.White,
    secondary = Color(0xFF7B67CF),
    tertiary = Color(0xFF977FD8),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF0EDFB),
    primaryContainer = Color(0xFFDDE8FF),
    secondaryContainer = Color(0xFFE9E0FF),
    tertiaryContainer = Color(0xFFEDE1FA),
    outline = Color(0xFFD6D2E6)
)

private val midnightVioletScheme = darkColorScheme(
    primary = Color(0xFFC4B0FF),
    onPrimary = Color(0xFF24104C),
    secondary = Color(0xFFD7C9FF),
    tertiary = Color(0xFFFFA0C5),
    background = Color(0xFF09070F),
    surface = Color(0xFF110E1B),
    surfaceVariant = Color(0xFF1B1729),
    primaryContainer = Color(0xFF352265),
    secondaryContainer = Color(0xFF312547),
    tertiaryContainer = Color(0xFF53253E),
    outline = Color(0xFF736C90)
)

private val obsidianMonoScheme = darkColorScheme(
    primary = Color(0xFFF6F4FF),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFD6D6DE),
    tertiary = Color(0xFFBEBECE),
    background = Color(0xFF050505),
    surface = Color(0xFF101010),
    surfaceVariant = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF2B2B2B),
    secondaryContainer = Color(0xFF242424),
    tertiaryContainer = Color(0xFF202020),
    outline = Color(0xFF7C7C7C)
)

private val marbleLilacScheme = lightColorScheme(
    primary = Color(0xFF1859D6),
    onPrimary = Color.White,
    secondary = Color(0xFF7D67D0),
    tertiary = Color(0xFF9A81DB),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFFFFCFF),
    surfaceVariant = Color(0xFFF1EEFB),
    primaryContainer = Color(0xFFDDE7FF),
    secondaryContainer = Color(0xFFE9E1FF),
    tertiaryContainer = Color(0xFFEEE3FB),
    outline = Color(0xFFD8D2E8)
)

@Composable
private fun manropeFontFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope)
)

@Composable
private fun mapleMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.maple_mono)
)

@Composable
private fun appTypography(): Typography {
    val manropeFontFamily = manropeFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = manropeFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = manropeFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = manropeFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = manropeFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = manropeFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = manropeFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = manropeFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = manropeFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = manropeFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = manropeFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = manropeFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = manropeFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = manropeFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = manropeFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = manropeFontFamily),
    )
}

val ManropeFontFamily: FontFamily
    @Composable get() = manropeFontFamily()

val MapleMonoFontFamily: FontFamily
    @Composable get() = mapleMonoFontFamily()

@Composable
fun SantiyaDesktopTheme(
    themePreset: ThemePreset,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when (themePreset) {
        ThemePreset.MIDNIGHT_VIOLET -> midnightVioletScheme
        ThemePreset.OBSIDIAN_MONO -> obsidianMonoScheme
        ThemePreset.MARBLE_LILAC -> marbleLilacScheme
        ThemePreset.SYSTEM -> if (darkTheme) fallbackDarkColorScheme else fallbackLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(),
        content = content
    )
}

val ColorScheme.canvasBorder: Color
    get() = outline.copy(alpha = 0.5f)

val ColorScheme.glassSurface: Color
    get() = surface.copy(alpha = 0.94f)

val ColorScheme.heroGradientStart: Color
    get() = primaryContainer.copy(alpha = 0.96f)

val ColorScheme.heroGradientEnd: Color
    get() = secondaryContainer.copy(alpha = 0.94f)
