package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimeDarkColorScheme = darkColorScheme(
    primary = NeonPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = NeonPurpleSecondary,
    onSecondary = Color.White,
    background = PrimeBackground,
    onBackground = PrimeTextPrimary,
    surface = PrimeCardBackground,
    onSurface = PrimeTextPrimary,
    surfaceVariant = PrimeCardSurface,
    onSurfaceVariant = PrimeTextSecondary,
    outline = NeonBorder
)

private val PrimeLightColorScheme = lightColorScheme(
    primary = NeonPurpleSecondary,
    onPrimary = Color.White,
    background = Color(0xFFF5F3FF),
    onBackground = Color(0xFF1E1035),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1035),
    surfaceVariant = Color(0xFFEDE9FE),
    onSurfaceVariant = Color(0xFF5B4D75),
    outline = Color(0xFFDDD6FE)
)

@Composable
fun PrimeStoreTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PrimeDarkColorScheme else PrimeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
