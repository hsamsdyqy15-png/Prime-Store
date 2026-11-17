package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.crash.CrashReporter
import com.example.data.local.PrimeStoreDatabase
import com.example.data.repository.SettingsRepository
import com.example.data.translation.MlKitTranslatorHelper
import com.example.data.translation.TranslationOutcome
import com.example.ui.theme.AccentOption
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashSafetyAndUpgradeTest {

    @Test
    fun `crash reporter stores up to 5 sanitized reports and tracks unread state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReporter.clearCrashReports(context)
        assertFalse(CrashReporter.hasUnreadCrash(context))

        for (i in 1..7) {
            CrashReporter.writeCrashReport(
                context,
                Thread.currentThread(),
                RuntimeException("Crash #$i Authorization: Bearer secret_tok_$i ghp_1234567890abcdef")
            )
        }

        val reports = CrashReporter.getAllCrashReports(context)
        assertEquals(5, reports.size)
        val latest = CrashReporter.getLastCrashReport(context)
        assertNotNull(latest)
        assertTrue(latest!!.contains("Crash #7"))
        assertFalse(latest.contains("secret_tok_7"))
        assertFalse(latest.contains("ghp_1234567890abcdef"))
        assertTrue(CrashReporter.hasUnreadCrash(context))

        CrashReporter.markCrashAcknowledged(context)
        assertFalse(CrashReporter.hasUnreadCrash(context))
        assertNotNull(CrashReporter.getLastCrashReport(context))

        CrashReporter.clearCrashReports(context)
        assertNull(CrashReporter.getLastCrashReport(context))
    }

    @Test
    fun `settings repository survives mismatched preference types across APK upgrades`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val v2Prefs = context.getSharedPreferences("prime_store_settings", Context.MODE_PRIVATE)
        v2Prefs.edit().clear()
            .putInt("ps_theme_mode_v2", 99) // legacy wrong type (Int instead of String)
            .putString("ps_theme_accent_v2", "0xFF10B981") // String instead of Long
            .putInt("ps_language_tag_v2", 42) // Int instead of String
            .putString("ps_auto_translate_v2", "false") // String instead of Boolean
            .putInt("ps_notifications_enabled_v2", 0) // Int instead of Boolean
            .commit()

        val repo = SettingsRepository(context)
        assertEquals(ThemeMode.DARK, repo.themeMode.value)
        assertEquals(0xFF10B981L, repo.accentHex.value)
        assertEquals("en", repo.languageTag.value)
        assertFalse(repo.autoTranslateEnabled.value)
        assertFalse(repo.notificationsEnabled.value)

        // Verify fallback defaults cleanly when completely invalid strings are stored
        v2Prefs.edit()
            .putString("ps_theme_accent_v2", "not_a_number")
            .putString("ps_language_tag_v2", "")
            .commit()
        val repo2 = SettingsRepository(context)
        assertEquals(AccentOption.PURPLE.hex, repo2.accentHex.value)
        assertEquals("auto", repo2.languageTag.value)
    }

    @Test
    fun `migration 4 to 5 preserves cached_apps rows and sha256 columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_4_5_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v4Callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_apps` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `nameAr` TEXT NOT NULL,
                        `categoryKey` TEXT NOT NULL,
                        `rating` REAL NOT NULL,
                        `ratingCount` TEXT NOT NULL,
                        `downloadCount` TEXT NOT NULL,
                        `size` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `downloadCountLong` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `versionCode` INTEGER NOT NULL,
                        `isFeatured` INTEGER NOT NULL,
                        `isTrending` INTEGER NOT NULL,
                        `isNew` INTEGER NOT NULL,
                        `iconEmoji` TEXT NOT NULL,
                        `accentColor` INTEGER NOT NULL,
                        `descriptionAr` TEXT NOT NULL,
                        `featuresJson` TEXT NOT NULL,
                        `iconUrl` TEXT,
                        `fileUrl` TEXT,
                        `screenshotUrlsJson` TEXT NOT NULL,
                        `isOpenSource` INTEGER NOT NULL,
                        `sourceCodeUrl` TEXT,
                        `supportedAbis` TEXT NOT NULL,
                        `packageName` TEXT,
                        `version` TEXT NOT NULL,
                        `changelog` TEXT,
                        `lastUpdated` INTEGER NOT NULL,
                        `itemType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cached_apps` VALUES (
                        'app_v4', 'V4 App', 'تطبيق', 'tools', 4.6, '5', '50', '10 MB',
                        10000000, 50, 1000, 900, 2, 0, 0, 1, '📱', 0, 'desc', '[]',
                        NULL, 'https://example.com/v4.apk', '[]', 0, NULL, 'arm64-v8a',
                        'com.example.v4', '1.2.0', NULL, 1000, 'app'
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: androidx.sqlite.db.SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(v4Callback)
                .build()
        )
        val db = helper.writableDatabase
        PrimeStoreDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT id, sha256, splitApkUrlsJson, obbUrl FROM cached_apps WHERE id = 'app_v4'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("app_v4", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }

        db.close()
        helper.close()
        dbFile.delete()
    }

    @Test
    fun `database getInstance recovers from corrupt database file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbPath = context.getDatabasePath("prime_store_offline.db")
        dbPath.parentFile?.mkdirs()
        dbPath.writeBytes("corrupt_sqlite_header_bytes_123456".toByteArray())

        val instance = PrimeStoreDatabase.getInstance(context)
        val writable = instance.openHelper.writableDatabase
        assertTrue(writable.isOpen)
    }

    @Test
    fun `main activity extractDeepLinkAppId validates scheme host and id safely`() {
        val activity = MainActivity()

        val valid = Intent(Intent.ACTION_VIEW, Uri.parse("prime://app/snap_123-abc"))
        assertEquals("snap_123-abc", activity.extractDeepLinkAppId(valid))

        val wrongScheme = Intent(Intent.ACTION_VIEW, Uri.parse("https://app/snap_123"))
        assertNull(activity.extractDeepLinkAppId(wrongScheme))

        val wrongHost = Intent(Intent.ACTION_VIEW, Uri.parse("prime://other/snap_123"))
        assertNull(activity.extractDeepLinkAppId(wrongHost))

        val emptyPath = Intent(Intent.ACTION_VIEW, Uri.parse("prime://app/"))
        assertNull(activity.extractDeepLinkAppId(emptyPath))

        val invalidChars = Intent(Intent.ACTION_VIEW, Uri.parse("prime://app/foo%20bar!"))
        assertNull(activity.extractDeepLinkAppId(invalidChars))

        assertNull(activity.extractDeepLinkAppId(null))
    }

    @Test
    fun `translator script pre-detection avoids native ML Kit calls for known scripts`() {
        assertEquals("ar", MlKitTranslatorHelper.detectScriptLanguage("تطبيق تواصل اجتماعي رائع"))
        assertEquals("fa", MlKitTranslatorHelper.detectScriptLanguage("برنامه بسیار کاربردی و جدید گجت"))
        assertEquals("ur", MlKitTranslatorHelper.detectScriptLanguage("یہ ایک بہترین موبائل ایپ ہے"))
        assertEquals("ru", MlKitTranslatorHelper.detectScriptLanguage("Отличное приложение для Android"))
        assertEquals("hi", MlKitTranslatorHelper.detectScriptLanguage("यह एक शानदार एंड्रॉइड ऐप है"))
        assertEquals("tr", MlKitTranslatorHelper.detectScriptLanguage("Çok hızlı ve güvenli bir mağaza"))
        assertEquals("de", MlKitTranslatorHelper.detectScriptLanguage("Große Auswahl an neuen Apps"))
        assertEquals("es", MlKitTranslatorHelper.detectScriptLanguage("Una aplicación rápida en español"))
        assertEquals("pt", MlKitTranslatorHelper.detectScriptLanguage("Aplicação rápida para celular"))
        assertNull(MlKitTranslatorHelper.detectScriptLanguage("A fast and secure social app"))
    }

    @Test
    fun `translateField returns AlreadyInTargetLanguage on matching script or hint without crashing`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val blankOutcome = MlKitTranslatorHelper.translateField(
            context = context,
            appId = "test_app",
            field = "description",
            originalText = "   ",
            targetLanguageCode = "en"
        )
        assertTrue(blankOutcome is TranslationOutcome.AlreadyInTargetLanguage)

        val arabicToArabic = MlKitTranslatorHelper.translateField(
            context = context,
            appId = "test_app",
            field = "description",
            originalText = "تطبيق تواصل اجتماعي سريع ومجاني",
            targetLanguageCode = "ar"
        )
        assertTrue(arabicToArabic is TranslationOutcome.AlreadyInTargetLanguage)

        val englishWithHint = MlKitTranslatorHelper.translateField(
            context = context,
            appId = "test_app",
            field = "description",
            originalText = "A fast social app for sharing photos and short videos.",
            targetLanguageCode = "en",
            knownSourceHint = "en"
        )
        assertTrue(englishWithHint is TranslationOutcome.AlreadyInTargetLanguage)
    }
}
