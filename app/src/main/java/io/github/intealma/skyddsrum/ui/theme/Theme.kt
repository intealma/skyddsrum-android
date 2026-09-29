package io.github.intealma.skyddsrum.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.intealma.skyddsrum.R

// Same palette as the Skyddsrum website: black, white, greys and one red accent.
val Black = Color(0xFF000000)
val Panel = Color(0xFF0B0C10)
val PanelRaised = Color(0xFF12141A)
val Field = Color(0xFF101218)
val Line = Color(0xFF2F333D)
val LineStrong = Color(0xFF575C66)
val White = Color(0xFFFFFFFF)
val WhiteDim = Color(0xFFD0D6DE)
val TextMuted = Color(0xFFB8BEC9)
val TextFaint = Color(0xFF6A707D)
val Red = Color(0xFFFF3B3B)

private fun nunito(weight: Int) = Font(
    R.font.nunito_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700))

private fun style(size: Int, weight: Int = 400, spacing: Float = 0f, lineHeight: Int = (size * 1.35f).toInt()) = TextStyle(
    fontFamily = Nunito,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    headlineMedium = style(22, 600),
    headlineSmall = style(19, 600),
    titleLarge = style(17, 600),
    titleMedium = style(15, 600),
    titleSmall = style(13, 600),
    bodyLarge = style(15),
    bodyMedium = style(13),
    bodySmall = style(12),
    labelLarge = style(13, 600, spacing = 0.4f),
    labelMedium = style(11, 600, spacing = 0.6f),
    labelSmall = style(10, 600, spacing = 0.8f),
)

private val AppColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = PanelRaised,
    onPrimaryContainer = White,
    secondary = Red,
    onSecondary = White,
    secondaryContainer = PanelRaised,
    onSecondaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Panel,
    surfaceContainer = PanelRaised,
    surfaceContainerHigh = PanelRaised,
    surfaceContainerHighest = PanelRaised,
    outline = LineStrong,
    outlineVariant = Line,
    error = Color(0xFFFF6B6B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun SkyddsrumTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
}
