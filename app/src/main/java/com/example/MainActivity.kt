package com.example

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.repository.SettingsRepository
import com.example.ui.PrimeStoreApp
import com.example.ui.theme.PrimeStoreTheme
import com.example.ui.theme.ThemeMode

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository.getInstance(this)
        settingsRepository.applySavedLocale()

        val initialThemeMode = settingsRepository.themeMode.value
        val initialIsDark = when (initialThemeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        applySystemBars(initialIsDark)

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState()
            val accentHex by settingsRepository.accentHex.collectAsState()
            val isSysDark = isSystemInDarkTheme()

            val effectiveIsDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.SYSTEM -> isSysDark
            }

            androidx.compose.runtime.SideEffect {
                applySystemBars(effectiveIsDark)
            }

            PrimeStoreTheme(
                mode = themeMode,
                accentHex = accentHex
            ) {
                PrimeStoreApp()
            }
        }
    }

    private fun applySystemBars(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)

        window?.let { w ->
            val controller = WindowInsetsControllerCompat(w, w.decorView)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}
