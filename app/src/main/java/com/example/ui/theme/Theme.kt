package com.example.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun PrimeStoreTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    PrimeStoreTheme(
        mode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        accentHex = AccentOption.PURPLE.hex,
        content = content
    )
}
