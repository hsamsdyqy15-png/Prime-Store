package com.example.ui.theme

import androidx.annotation.StringRes
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
import com.example.R

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_mode_system),
    DARK(R.string.theme_mode_dark),
    AMOLED(R.string.theme_mode_amoled),
    LIGHT(R.string.theme_mode_light)
}

enum class AccentOption(val hex: Long, @StringRes val labelRes: Int) {
    PURPLE(0xFFA855F7, R.string.accent_purple),
    CYAN(0xFF22D3EE, R.string.accent_cyan),
    GREEN(0xFF10B981, R.string.accent_green),
    RED(0xFFF43F5E, R.string.accent_red),
    ORANGE(0xFFF59E0B, R.string.accent_orange);

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
        get() = accent.copy(alpha = if (isDark) 0.35f else 0.18f)
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

private fun computeOnAccentColor(accent: Color): Color {
    val luminance = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
    return if (luminance > 0.45f) Color(0xFF11071F) else Color.White
}

fun buildPrimeColors(mode: ThemeMode, accentHex: Long, isSystemDark: Boolean): PrimeColors {
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    val accentColor = Color(accentHex)
    val accentDarkColor = darkenColor(accentColor, 0.24f)
    val onAccentColor = computeOnAccentColor(accentColor)

    return when {
        !isDark -> {
            PrimeColors(
                background = Color(0xFFF6F4FC),
                surface = Color(0xFFEDE8FA),
                card = Color(0xFFFFFFFF),
                cardAlt = Color(0xFFE4DDF7),
                border = accentColor.copy(alpha = 0.30f),
                textPrimary = Color(0xFF11071F),
                textSecondary = Color(0xFF4C3D66),
                textMuted = Color(0xFF6E5F8A),
                textOnAccent = onAccentColor,
                accent = accentColor,
                accentDark = accentDarkColor,
                success = Color(0xFF059669),
                danger = Color(0xFFDC2626),
                warning = Color(0xFFD97706),
                isDark = false
            )
        }
        mode == ThemeMode.AMOLED -> {
            PrimeColors(
                background = Color(0xFF000000),
                surface = Color(0xFF050505),
                card = Color(0xFF0A0A0A),
                cardAlt = Color(0xFF121212),
                border = accentColor.copy(alpha = 0.40f),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFC4BAD8),
                textMuted = Color(0xFF887E9C),
                textOnAccent = onAccentColor,
                accent = accentColor,
                accentDark = accentDarkColor,
                isDark = true
            )
        }
        else -> {
            PrimeColors(
                background = Color(0xFF0B0614),
                surface = Color(0xFF130C21),
                card = Color(0xFF16101F),
                cardAlt = Color(0xFF1F1630),
                border = accentColor.copy(alpha = 0.35f),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFB4A8C8),
                textMuted = Color(0xFF817696),
                textOnAccent = onAccentColor,
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
