package com.alram.mail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alram.mail.data.ThemeMode

private val Teal = Color(0xFF0F766E)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBE7),
    onPrimaryContainer = Color(0xFF042F2B),
    secondary = Color(0xFF55605E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6ECEA),
    onSecondaryContainer = Color(0xFF1B2422),
    tertiary = Color(0xFFA15C07),
    tertiaryContainer = Color(0xFFFBE8CC),
    onTertiaryContainer = Color(0xFF3B2102),
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFAFAF9),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEEEEEB),
    onSurfaceVariant = Color(0xFF5F5E5B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F3),
    surfaceContainer = Color(0xFFF0F0EE),
    surfaceContainerHigh = Color(0xFFEAEAE7),
    surfaceContainerHighest = Color(0xFFE4E4E1),
    outline = Color(0xFF8D8C88),
    outlineVariant = Color(0xFFDAD9D5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EC4B6),
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF0B4A44),
    onPrimaryContainer = Color(0xFFBDEBE4),
    secondary = Color(0xFFB4C1BE),
    onSecondary = Color(0xFF1F2B29),
    secondaryContainer = Color(0xFF2B3735),
    onSecondaryContainer = Color(0xFFD7E4E1),
    tertiary = Color(0xFFF2BA6B),
    tertiaryContainer = Color(0xFF4A2F06),
    onTertiaryContainer = Color(0xFFFBE8CC),
    background = Color(0xFF121413),
    onBackground = Color(0xFFE6E6E3),
    surface = Color(0xFF121413),
    onSurface = Color(0xFFE6E6E3),
    surfaceVariant = Color(0xFF2A2C2B),
    onSurfaceVariant = Color(0xFFA9A9A5),
    surfaceContainerLowest = Color(0xFF0D0F0E),
    surfaceContainerLow = Color(0xFF181A19),
    surfaceContainer = Color(0xFF1C1E1D),
    surfaceContainerHigh = Color(0xFF262827),
    surfaceContainerHighest = Color(0xFF303231),
    outline = Color(0xFF7B7B77),
    outlineVariant = Color(0xFF3A3C3A),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val Sans = FontFamily.Default

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun AlramTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
