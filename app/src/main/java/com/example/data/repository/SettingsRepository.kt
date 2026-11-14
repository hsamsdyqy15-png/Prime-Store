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
import java.util.Locale

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("prime_store_settings", Context.MODE_PRIVATE)
    private val legacyAuthPrefs: SharedPreferences =
        context.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentHex = MutableStateFlow(readAccentHex())
    val accentHex: StateFlow<Long> = _accentHex.asStateFlow()

    private val _languageTag = MutableStateFlow(readLanguageTag())
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    private val _autoTranslateEnabled = MutableStateFlow(readAutoTranslateEnabled())
    val autoTranslateEnabled: StateFlow<Boolean> = _autoTranslateEnabled.asStateFlow()

    private val _wifiOnlyTranslation = MutableStateFlow(readWifiOnlyTranslation())
    val wifiOnlyTranslation: StateFlow<Boolean> = _wifiOnlyTranslation.asStateFlow()

    companion object {
        private const val KEY_THEME_MODE = "ps_theme_mode_v2"
        private const val KEY_ACCENT_HEX = "ps_theme_accent_v2"
        private const val KEY_LANGUAGE_TAG = "ps_language_tag_v2"
        private const val KEY_AUTO_TRANSLATE = "ps_auto_translate_v2"
        private const val KEY_WIFI_ONLY_TRANSLATE = "ps_wifi_only_translate_v2"

        private val SUPPORTED_LANGS = setOf(
            "en", "ar", "tr", "fr", "es", "de", "ru", "fa", "ur", "hi", "id", "pt"
        )

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun normalizeLanguageTag(raw: String?): String {
            val clean = raw?.trim()?.lowercase()?.substringBefore("-")?.substringBefore("_").orEmpty()
            return when {
                clean.isBlank() -> "auto"
                clean == "auto" -> "auto"
                clean == "in" || clean == "id" -> "id"
                clean in SUPPORTED_LANGS -> clean
                else -> "en"
            }
        }

        fun toAndroidResourceTag(canonicalTag: String): String {
            return if (canonicalTag == "id") "id" else canonicalTag
        }

        fun resolveDeviceLanguage(): String {
            val sysLang = Locale.getDefault().language.lowercase()
            return when {
                sysLang == "in" || sysLang == "id" -> "id"
                sysLang in SUPPORTED_LANGS -> sysLang
                else -> "en"
            }
        }
    }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null)
            ?: legacyAuthPrefs.getString("ps_theme_mode", "DARK")
            ?: "DARK"
        return try {
            ThemeMode.valueOf(raw.uppercase())
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun readAccentHex(): Long {
        return if (prefs.contains(KEY_ACCENT_HEX)) {
            prefs.getLong(KEY_ACCENT_HEX, AccentOption.PURPLE.hex)
        } else {
            legacyAuthPrefs.getLong("ps_theme_accent", AccentOption.PURPLE.hex)
        }
    }

    private fun readLanguageTag(): String {
        val stored = prefs.getString(KEY_LANGUAGE_TAG, null)
            ?: legacyAuthPrefs.getString("ps_language", "auto")
            ?: "auto"
        return normalizeLanguageTag(stored)
    }

    private fun readAutoTranslateEnabled(): Boolean {
        return if (prefs.contains(KEY_AUTO_TRANSLATE)) {
            prefs.getBoolean(KEY_AUTO_TRANSLATE, true)
        } else {
            legacyAuthPrefs.getBoolean("ps_auto_translate", true)
        }
    }

    private fun readWifiOnlyTranslation(): Boolean {
        return if (prefs.contains(KEY_WIFI_ONLY_TRANSLATE)) {
            prefs.getBoolean(KEY_WIFI_ONLY_TRANSLATE, false)
        } else {
            legacyAuthPrefs.getBoolean("ps_wifi_only_translate", false)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentHex(hex: Long) {
        prefs.edit().putLong(KEY_ACCENT_HEX, hex).apply()
        _accentHex.value = hex
    }

    fun setAutoTranslateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
        _autoTranslateEnabled.value = enabled
    }

    fun setWifiOnlyTranslation(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_TRANSLATE, wifiOnly).apply()
        _wifiOnlyTranslation.value = wifiOnly
    }

    fun setLanguage(tag: String) {
        val normalized = normalizeLanguageTag(tag)
        prefs.edit().putString(KEY_LANGUAGE_TAG, normalized).apply()
        _languageTag.value = normalized

        val desired = if (normalized == "auto") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(toAndroidResourceTag(normalized))
        }

        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val desiredTags = desired.toLanguageTags()
        if (!currentTags.equals(desiredTags, ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    fun applySavedLocale() {
        val normalized = _languageTag.value
        val desired = if (normalized != "auto") {
            LocaleListCompat.forLanguageTags(toAndroidResourceTag(normalized))
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val desiredTags = desired.toLanguageTags()
        if (!currentTags.equals(desiredTags, ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    fun getEffectiveLanguageCode(): String {
        val saved = _languageTag.value
        if (saved != "auto") return saved
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val first = appLocales[0]?.language?.lowercase()
            val norm = normalizeLanguageTag(first)
            if (norm != "auto") return norm
        }
        return resolveDeviceLanguage()
    }
}
