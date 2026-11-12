package com.freestore.market.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF555555),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF111111),
    onBackground = Color(0xFF111111),
    outline = Color(0xFFE2E2E2)
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBBBBBB),
    background = Color(0xFF101010),
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    onBackground = Color.White,
    outline = Color(0xFF333333)
)

@Composable
fun FreeStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
