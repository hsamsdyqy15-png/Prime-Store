package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.ui.theme.AccentOption
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("prime_store_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentHex = MutableStateFlow(readAccentHex())
    val accentHex: StateFlow<Long> = _accentHex.asStateFlow()

    private val _languageTag = MutableStateFlow(readLanguageTag())
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    companion object {
        private const val KEY_THEME_MODE = "ps_theme_mode_v2"
        private const val KEY_ACCENT_HEX = "ps_theme_accent_v2"
        private const val KEY_LANGUAGE_TAG = "ps_language_tag_v2"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK"
        return try {
            ThemeMode.valueOf(raw.uppercase())
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun readAccentHex(): Long {
        return prefs.getLong(KEY_ACCENT_HEX, AccentOption.PURPLE.hex)
    }

    private fun readLanguageTag(): String {
        return prefs.getString(KEY_LANGUAGE_TAG, "auto") ?: "auto"
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentHex(hex: Long) {
        prefs.edit().putLong(KEY_ACCENT_HEX, hex).apply()
        _accentHex.value = hex
    }

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE_TAG, tag).apply()
        _languageTag.value = tag

        if (tag == "auto" || tag.isBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun applySavedLocale() {
        val tag = _languageTag.value
        if (tag.isNotBlank() && tag != "auto") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }
}
