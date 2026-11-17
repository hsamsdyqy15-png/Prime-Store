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

    private val _notificationsEnabled = MutableStateFlow(readNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _deleteApkAfterInstall = MutableStateFlow(readDeleteApkAfterInstall())
    val deleteApkAfterInstall: StateFlow<Boolean> = _deleteApkAfterInstall.asStateFlow()

    private val _shizukuRootInstallEnabled = MutableStateFlow(readShizukuRootInstallEnabled())
    val shizukuRootInstallEnabled: StateFlow<Boolean> = _shizukuRootInstallEnabled.asStateFlow()

    companion object {
        private const val KEY_THEME_MODE = "ps_theme_mode_v2"
        private const val KEY_ACCENT_HEX = "ps_theme_accent_v2"
        private const val KEY_LANGUAGE_TAG = "ps_language_tag_v2"
        private const val KEY_AUTO_TRANSLATE = "ps_auto_translate_v2"
        private const val KEY_WIFI_ONLY_TRANSLATE = "ps_wifi_only_translate_v2"
        private const val KEY_NOTIFICATIONS_ENABLED = "ps_notifications_enabled_v2"
        private const val KEY_DELETE_APK_AFTER_INSTALL = "ps_delete_apk_after_install_v2"
        private const val KEY_SHIZUKU_ROOT_MODE = "ps_shizuku_root_install_v1"

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

    private fun readAnyPref(key: String): Any? {
        return try {
            prefs.all[key]
        } catch (_: Throwable) {
            null
        } ?: try {
            legacyAuthPrefs.all[key]
        } catch (_: Throwable) {
            null
        }
    }

    private fun readSafeString(primaryKey: String, legacyKey: String? = null, default: String): String {
        val raw = try {
            prefs.all[primaryKey]
        } catch (_: Throwable) {
            null
        } ?: legacyKey?.let { lk ->
            try {
                legacyAuthPrefs.all[lk]
            } catch (_: Throwable) {
                null
            }
        }
        return when (raw) {
            is String -> raw
            is Number, is Boolean -> raw.toString()
            else -> default
        }
    }

    private fun readSafeLong(primaryKey: String, legacyKey: String? = null, default: Long): Long {
        val raw = try {
            prefs.all[primaryKey]
        } catch (_: Throwable) {
            null
        } ?: legacyKey?.let { lk ->
            try {
                legacyAuthPrefs.all[lk]
            } catch (_: Throwable) {
                null
            }
        }
        return when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
                ?: raw.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16)
                ?: default
            else -> default
        }
    }

    private fun readSafeBoolean(primaryKey: String, legacyKey: String? = null, default: Boolean): Boolean {
        val raw = try {
            prefs.all[primaryKey]
        } catch (_: Throwable) {
            null
        } ?: legacyKey?.let { lk ->
            try {
                legacyAuthPrefs.all[lk]
            } catch (_: Throwable) {
                null
            }
        }
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
            else -> default
        }
    }

    private fun readThemeMode(): ThemeMode {
        val raw = readSafeString(KEY_THEME_MODE, "ps_theme_mode", "DARK")
        return try {
            ThemeMode.valueOf(raw.trim().uppercase())
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun readAccentHex(): Long {
        return readSafeLong(KEY_ACCENT_HEX, "ps_theme_accent", AccentOption.PURPLE.hex)
    }

    private fun readLanguageTag(): String {
        val stored = readSafeString(KEY_LANGUAGE_TAG, "ps_language", "auto")
        return normalizeLanguageTag(stored)
    }

    private fun readAutoTranslateEnabled(): Boolean {
        return readSafeBoolean(KEY_AUTO_TRANSLATE, "ps_auto_translate", true)
    }

    private fun readWifiOnlyTranslation(): Boolean {
        return readSafeBoolean(KEY_WIFI_ONLY_TRANSLATE, "ps_wifi_only_translate", false)
    }

    private fun readNotificationsEnabled(): Boolean =
        readSafeBoolean(KEY_NOTIFICATIONS_ENABLED, "ps_notifications_enabled", true)

    private fun readDeleteApkAfterInstall(): Boolean =
        readSafeBoolean(KEY_DELETE_APK_AFTER_INSTALL, null, true)

    private fun readShizukuRootInstallEnabled(): Boolean =
        readSafeBoolean(KEY_SHIZUKU_ROOT_MODE, null, false)

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

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DELETE_APK_AFTER_INSTALL, enabled).apply()
        _deleteApkAfterInstall.value = enabled
    }

    fun setShizukuRootInstallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHIZUKU_ROOT_MODE, enabled).apply()
        _shizukuRootInstallEnabled.value = enabled
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
            val first = appLocales.get(0)?.language?.lowercase()
            val norm = normalizeLanguageTag(first)
            if (norm != "auto") return norm
        }
        return resolveDeviceLanguage()
    }

    fun getCacheSizeBytes(context: Context): Long {
        var total = 0L
        total += dirSize(context.cacheDir)
        total += dirSize(context.externalCacheDir)
        return total
    }

    fun getApksSizeBytes(context: Context): Long {
        val dir = context.getExternalFilesDir("apks") ?: java.io.File(context.filesDir, "apks")
        return dirSize(dir)
    }

    fun clearTemporaryCache(context: Context): Long {
        val before = getCacheSizeBytes(context)
        deleteChildren(context.cacheDir)
        deleteChildren(context.externalCacheDir)
        val after = getCacheSizeBytes(context)
        return (before - after).coerceAtLeast(0L)
    }

    fun deleteAllDownloadedApks(context: Context): Long {
        val dir = context.getExternalFilesDir("apks") ?: java.io.File(context.filesDir, "apks")
        val before = dirSize(dir)
        deleteChildren(dir)
        return before
    }

    fun formatStorageSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "0 KB"
        }
    }

    private fun dirSize(file: java.io.File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            sum += dirSize(child)
        }
        return sum
    }

    private fun deleteChildren(dir: java.io.File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
    }
}
