package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    DARK,
    AMOLED,
    LIGHT
}

enum class AccentOption(val hex: Long, val displayNameAr: String, val displayNameEn: String) {
    PURPLE(0xFFA855F7, "بنفسجي", "Purple"),
    CYAN(0xFF22D3EE, "سماوي", "Cyan"),
    GREEN(0xFF10B981, "أخضر", "Emerald"),
    RED(0xFFF43F5E, "أحمر", "Rose"),
    ORANGE(0xFFF59E0B, "برتقالي", "Amber");

    companion object {
        fun fromHex(value: Long): AccentOption {
            return entries.find { it.hex == value } ?: PURPLE
        }

        fun fromString(name: String): AccentOption {
            return try {
                valueOf(name.uppercase())
            } catch (_: Exception) {
                PURPLE
            }
        }
    }
}

@Immutable
data class PrimeColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val accent: Color,
    val accentDark: Color,
    val success: Color = Color(0xFF10B981),
    val danger: Color = Color(0xFFEF4444),
    val warning: Color = Color(0xFFF59E0B),
    val isDark: Boolean
) {
    val buttonGradient: Brush
        get() = Brush.horizontalGradient(listOf(accentDark, accent))

    val accentGradient: Brush
        get() = buttonGradient

    val cardBorder: Color
        get() = border

    val accentGlow: Color
        get() = accent.copy(alpha = if (isDark) 0.35f else 0.20f)
}

val LocalPrimeColors = staticCompositionLocalOf<PrimeColors> {
    error("No PrimeColors provided")
}

object PrimeTheme {
    val colors: PrimeColors
        @Composable
        get() = LocalPrimeColors.current
}

fun darkenColor(color: Color, factor: Float = 0.25f): Color {
    val f = (1f - factor).coerceIn(0f, 1f)
    return Color(
        red = color.red * f,
        green = color.green * f,
        blue = color.blue * f,
        alpha = color.alpha
    )
}

fun buildPrimeColors(mode: ThemeMode, accentHex: Long, isSystemDark: Boolean): PrimeColors {
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    val accentColor = Color(accentHex)
    val accentDarkColor = darkenColor(accentColor, 0.25f)

    return when {
        !isDark -> {
            val borderCol = accentColor.copy(alpha = 0.35f)
            PrimeColors(
                background = Color(0xFFF3EEFC),
                surface = Color(0xFFFFFFFF),
                card = Color(0xFFFFFFFF),
                cardAlt = Color(0xFFE9DFFA),
                border = borderCol,
                textPrimary = Color(0xFF11071F),
                textSecondary = Color(0xFF5B4C76),
                textMuted = Color(0xFF7A6B94),
                textOnAccent = Color.White,
                accent = accentColor,
                accentDark = accentDarkColor,
                isDark = false
            )
        }
        mode == ThemeMode.AMOLED -> {
            val borderCol = accentColor.copy(alpha = 0.35f)
            PrimeColors(
                background = Color(0xFF000000),
                surface = Color(0xFF0A0A0A),
                card = Color(0xFF0A0A0A),
                cardAlt = Color(0xFF121212),
                border = borderCol,
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFB4A8C8),
                textMuted = Color(0xFF7E7392),
                textOnAccent = Color.White,
                accent = accentColor,
                accentDark = accentDarkColor,
                isDark = true
            )
        }
        else -> {
            val borderCol = accentColor.copy(alpha = 0.35f)
            PrimeColors(
                background = Color(0xFF0B0614),
                surface = Color(0xFF16101F),
                card = Color(0xFF16101F),
                cardAlt = Color(0xFF1D1530),
                border = borderCol,
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFB4A8C8),
                textMuted = Color(0xFF7E7392),
                textOnAccent = Color.White,
                accent = accentColor,
                accentDark = accentDarkColor,
                isDark = true
            )
        }
    }
}

@Composable
fun PrimeStoreTheme(
    mode: ThemeMode = ThemeMode.DARK,
    accentHex: Long = AccentOption.PURPLE.hex,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val primeColors = buildPrimeColors(mode, accentHex, isSystemDark)

    val colorScheme = if (primeColors.isDark) {
        darkColorScheme(
            primary = primeColors.accent,
            onPrimary = primeColors.textOnAccent,
            background = primeColors.background,
            onBackground = primeColors.textPrimary,
            surface = primeColors.card,
            onSurface = primeColors.textPrimary,
            surfaceVariant = primeColors.cardAlt,
            onSurfaceVariant = primeColors.textSecondary,
            outline = primeColors.border
        )
    } else {
        lightColorScheme(
            primary = primeColors.accent,
            onPrimary = primeColors.textOnAccent,
            background = primeColors.background,
            onBackground = primeColors.textPrimary,
            surface = primeColors.card,
            onSurface = primeColors.textPrimary,
            surfaceVariant = primeColors.cardAlt,
            onSurfaceVariant = primeColors.textSecondary,
            outline = primeColors.border
        )
    }

    CompositionLocalProvider(LocalPrimeColors provides primeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
