package io.github.intealma.skyddsrum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF123C44)
val TealLight = Color(0xFF2E6B75)
val Amber = Color(0xFFF4B942)
val AmberDark = Color(0xFF7A5200)

private val LightColors = lightColorScheme(
    primary = TealLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8EC),
    onPrimaryContainer = Teal,
    secondary = AmberDark,
    onSecondary = Color.White,
    secondaryContainer = Amber,
    onSecondaryContainer = Color(0xFF2A1C00),
    background = Color(0xFFF7FAFA),
    surface = Color(0xFFF7FAFA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0DA),
    onPrimary = Teal,
    primaryContainer = TealLight,
    onPrimaryContainer = Color(0xFFE0F4F7),
    secondary = Amber,
    onSecondary = Color(0xFF2A1C00),
    secondaryContainer = Amber,
    onSecondaryContainer = Color(0xFF2A1C00),
)

@Composable
fun SkyddsrumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
