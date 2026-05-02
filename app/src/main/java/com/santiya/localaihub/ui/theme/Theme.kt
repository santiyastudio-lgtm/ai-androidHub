package com.santiya.localaihub.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.santiya.localaihub.hub.ThemePreset

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7A8FF),
    onPrimary = Color(0xFF20114C),
    secondary = Color(0xFFC9B6FF),
    tertiary = Color(0xFFF496B7),
    background = Color(0xFF0B0B12),
    surface = Color(0xFF11121B),
    surfaceVariant = Color(0xFF1B1A26),
    primaryContainer = Color(0xFF2E2361),
    secondaryContainer = Color(0xFF302545),
    tertiaryContainer = Color(0xFF4A2235)
)

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D48D8),
    onPrimary = Color.White,
    secondary = Color(0xFF8B6FD7),
    tertiary = Color(0xFFB85F87),
    background = Color(0xFFF8F4FC),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFEEE7F6),
    primaryContainer = Color(0xFFE8DEFF),
    secondaryContainer = Color(0xFFEEE6FF),
    tertiaryContainer = Color(0xFFFFD7E6)
)

private val MidnightVioletScheme = darkColorScheme(
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

private val ObsidianMonoScheme = darkColorScheme(
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

private val MarbleLilacScheme = lightColorScheme(
    primary = Color(0xFF6A4BE8),
    onPrimary = Color.White,
    secondary = Color(0xFF8565D8),
    tertiary = Color(0xFFAC6A97),
    background = Color(0xFFF6F2F8),
    surface = Color(0xFFFFFCFF),
    surfaceVariant = Color(0xFFF0EAF4),
    primaryContainer = Color(0xFFE9DEFF),
    secondaryContainer = Color(0xFFF0E8FF),
    tertiaryContainer = Color(0xFFFADAE7),
    outline = Color(0xFF82788F)
)

private val ManropeTypography: Typography by lazy {
    val base = Typography()
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = ManropeFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = ManropeFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = ManropeFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = ManropeFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = ManropeFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = ManropeFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = ManropeFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = ManropeFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = ManropeFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = ManropeFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = ManropeFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = ManropeFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = ManropeFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = ManropeFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = ManropeFontFamily),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SantiyaLocalAiHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePreset: ThemePreset = ThemePreset.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = resolveColorScheme(
        context = context,
        darkTheme = darkTheme,
        themePreset = themePreset
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ManropeTypography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private fun resolveColorScheme(
    context: Context,
    darkTheme: Boolean,
    themePreset: ThemePreset
): ColorScheme {
    return when (themePreset) {
        ThemePreset.MIDNIGHT_VIOLET -> MidnightVioletScheme
        ThemePreset.OBSIDIAN_MONO -> ObsidianMonoScheme
        ThemePreset.MARBLE_LILAC -> MarbleLilacScheme
        ThemePreset.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } catch (_: Exception) {
                    if (darkTheme) FallbackDarkColorScheme else FallbackLightColorScheme
                }
            } else {
                if (darkTheme) FallbackDarkColorScheme else FallbackLightColorScheme
            }
        }
    }
}
