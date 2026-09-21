package com.seongtae.vlogmaker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Gray1 = Color(0xFF111111)
val Gray2 = Color(0xFF222222)
val Gray3 = Color(0xFF444444)
val Gray5 = Color(0xFF888888)
val Gray7 = Color(0xFFBBBBBB)

private val BlipColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Gray7,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Gray1,
    onSurfaceVariant = Gray7,
    outline = Gray3,
    outlineVariant = Gray2,
    error = Color(0xFFFF5555),
    onError = Black,
    surfaceContainer = Gray1,
    surfaceContainerHigh = Gray2,
    surfaceContainerHighest = Gray2,
    surfaceContainerLow = Gray1,
    surfaceContainerLowest = Black,
)

private val BlipShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun BlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlipColors, shapes = BlipShapes, content = content)
}
