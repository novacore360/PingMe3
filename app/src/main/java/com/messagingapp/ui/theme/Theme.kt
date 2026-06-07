package com.messagingapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Glass morphism color palette
object GlassColors {
    val background = Color(0xFF0A0E1A)
    val backgroundGradientStart = Color(0xFF0A0E1A)
    val backgroundGradientEnd = Color(0xFF0D1B2A)

    val glassWhite = Color(0x1AFFFFFF)
    val glassBorder = Color(0x33FFFFFF)
    val glassHighlight = Color(0x0DFFFFFF)

    val primary = Color(0xFF6C9EFF)
    val primaryLight = Color(0xFF8BB4FF)
    val primaryGlass = Color(0x336C9EFF)

    val accent = Color(0xFFB388FF)
    val accentGlass = Color(0x33B388FF)

    val success = Color(0xFF64FFDA)
    val error = Color(0xFFFF6B8A)
    val warning = Color(0xFFFFD740)

    val textPrimary = Color(0xFFEEF2FF)
    val textSecondary = Color(0xFFAAB4C8)
    val textTertiary = Color(0xFF667085)

    val bubbleSent = Color(0x996C9EFF)
    val bubbleReceived = Color(0x1AFFFFFF)

    val divider = Color(0x1AFFFFFF)
    val shimmer = Color(0x26FFFFFF)
}

val AppColorScheme = darkColorScheme(
    primary = GlassColors.primary,
    secondary = GlassColors.accent,
    background = GlassColors.background,
    surface = GlassColors.glassWhite,
    onPrimary = Color.White,
    onBackground = GlassColors.textPrimary,
    onSurface = GlassColors.textPrimary,
    error = GlassColors.error
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = GlassColors.textPrimary,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = GlassColors.textPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = GlassColors.textPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = GlassColors.textPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = GlassColors.textPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = GlassColors.textSecondary
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = GlassColors.textTertiary
    )
)

val AppBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF060A14),
        Color(0xFF0A0E1A),
        Color(0xFF0D1525),
        Color(0xFF0A1020)
    )
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}

fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color(0x1EFFFFFF),
                Color(0x0AFFFFFF)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0x40FFFFFF),
                Color(0x10FFFFFF)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

fun Modifier.glassSurface(cornerRadius: Dp = 20.dp): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color(0x26FFFFFF),
                Color(0x0DFFFFFF)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0x50FFFFFF),
                Color(0x18FFFFFF)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    padding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .glassCard(cornerRadius)
            .padding(padding)
    ) {
        content()
    }
}
