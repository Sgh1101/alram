package com.alram.mail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alram.mail.R
import com.alram.mail.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5FE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = Color(0xFF0B2A72),
    secondary = Color(0xFF4B5A78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7ECF5),
    onSecondaryContainer = Color(0xFF1C2942),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1DC),
    onTertiaryContainer = Color(0xFF7A3E00),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A1A12),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF2F4F8),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFEEF1F6),
    surfaceContainerHigh = Color(0xFFE8EBF1),
    surfaceContainerHighest = Color(0xFFE1E5EC),
    outline = Color(0xFF8B93A3),
    outlineVariant = Color(0xFFE3E7EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86AAFF),
    onPrimary = Color(0xFF0A1F5C),
    primaryContainer = Color(0xFF1C3778),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFFAEBAD0),
    onSecondary = Color(0xFF1B2537),
    secondaryContainer = Color(0xFF232C3D),
    onSecondaryContainer = Color(0xFFD6DEEC),
    tertiary = Color(0xFFF5B46A),
    onTertiary = Color(0xFF3D2400),
    tertiaryContainer = Color(0xFF4A2E08),
    onTertiaryContainer = Color(0xFFFFE2BF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0B06),
    errorContainer = Color(0xFF5A1A16),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8ECF3),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFE8ECF3),
    surfaceVariant = Color(0xFF1E2430),
    onSurfaceVariant = Color(0xFF98A2B5),
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xFF11151D),
    surfaceContainer = Color(0xFF161B25),
    surfaceContainerHigh = Color(0xFF1C222E),
    surfaceContainerHighest = Color(0xFF232A37),
    outline = Color(0xFF6B7488),
    outlineVariant = Color(0xFF262D3A),
)

/** Material 색상표에 없는 이 앱 전용 색. */
@Immutable
data class AlramColors(
    /** 그룹 카드 배경 (라이트: 흰색, 다크: 배경보다 한 단계 밝은 남색) */
    val card: Color,
    /** 홈 상태 카드 그라데이션 */
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** 설정 행 앞 아이콘 타일 */
    val iconTile: Color,
    val iconTint: Color,
    /** 알림 배지(코랄) — 앱 아이콘과 같은 색 */
    val badge: Color,
    /** 세그먼트 탭 바탕/선택 */
    val segmentTrack: Color,
    val segmentThumb: Color,
    /** 켜진 스위치 트랙 (흰 손잡이와 대비가 충분한 블루) */
    val switchOn: Color,
)

private val LightExtra = AlramColors(
    card = Color.White,
    heroStart = Color(0xFF3469F4),
    heroEnd = Color(0xFF1E46D2),
    onHero = Color.White,
    onHeroMuted = Color.White.copy(alpha = 0.78f),
    iconTile = Color(0xFFE8EFFF),
    iconTint = Color(0xFF2E5FE8),
    badge = Color(0xFFFF6B57),
    segmentTrack = Color(0xFFE6E9F0),
    segmentThumb = Color.White,
    switchOn = Color(0xFF2E5FE8),
)

private val DarkExtra = AlramColors(
    card = Color(0xFF151A23),
    heroStart = Color(0xFF2652D6),
    heroEnd = Color(0xFF172F86),
    onHero = Color.White,
    onHeroMuted = Color.White.copy(alpha = 0.75f),
    iconTile = Color(0xFF1C2A4A),
    iconTint = Color(0xFF9DBBFF),
    badge = Color(0xFFFF7A68),
    segmentTrack = Color(0xFF11151D),
    segmentThumb = Color(0xFF262D3B),
    switchOn = Color(0xFF3D6DF2),
)

val LocalAlramColors = staticCompositionLocalOf { LightExtra }

object AlramTheme {
    val colors: AlramColors
        @Composable get() = LocalAlramColors.current
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// 한글이 단어 중간에서 끊기지 않도록 어절(구) 단위로 줄을 바꾼다. (Android 13+ 에서 적용)
private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
    lineBreak: LineBreak = LineBreak.Paragraph,
) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineBreak = lineBreak,
)

private val AppTypography = Typography(
    displaySmall = style(34, 42, FontWeight.Bold, -0.8, LineBreak.Heading),
    headlineLarge = style(30, 38, FontWeight.Bold, -0.6, LineBreak.Heading),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.5, LineBreak.Heading),
    headlineSmall = style(22, 30, FontWeight.Bold, -0.4, LineBreak.Heading),
    titleLarge = style(20, 28, FontWeight.Bold, -0.3, LineBreak.Heading),
    titleMedium = style(17, 24, FontWeight.SemiBold, -0.2),
    titleSmall = style(15, 21, FontWeight.SemiBold, -0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, -0.1),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold, -0.1),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AlramTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(mode)
    CompositionLocalProvider(LocalAlramColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
