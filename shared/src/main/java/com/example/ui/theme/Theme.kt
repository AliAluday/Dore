package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = MintPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = MintDeep,
    secondary = IndigoAccentLight,
    onSecondary = Color.White,
    secondaryContainer = IndigoSoft,
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = StatusUrgent,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = Color(0xFF00291D),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = MintSoft,
    secondary = IndigoAccentDark,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = IndigoSoft,
    tertiary = AmberGold,
    onTertiary = Color(0xFF3B2100),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519)
)

val DoriShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun DoriTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DoriTypography,
        shapes = DoriShapes,
        content = content
    )
}
