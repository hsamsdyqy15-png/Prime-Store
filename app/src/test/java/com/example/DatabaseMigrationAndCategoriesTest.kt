package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PrimeStoreDatabase
import com.example.data.model.AppActionState
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.StoreCategories
import com.example.data.model.resolveAppActionState
import com.example.data.repository.CategoriesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationAndCategoriesTest {

    private fun sampleApp(
        id: String,
        categoryKey: String,
        itemType: String,
        category: AppCategory = AppCategory.TOOLS,
        versionCode: Long = 1L
    ) = AppItem(
        id = id,
        name = id,
        nameAr = id,
        category = category,
        tags = emptyList(),
        rating = 4.5f,
        ratingCount = "10",
        downloadCount = "100",
        size = "10 MB",
        sizeBytes = 10_000_000L,
        iconEmoji = "📱",
        accentColor = 0xFFA855F7,
        descriptionAr = "Desc",
        descriptionEn = "Desc",
        featuresAr = emptyList(),
        screenshotsColors = emptyList(),
        categoryKey = categoryKey,
        itemType = itemType,
        versionCode = versionCode
    )

    @Test
    fun `migration 3 to 4 preserves cached_apps rows and seeds categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_test_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val v3Callback = object : SupportSQLiteOpenHelper.Callback(3) {
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
                        `lastUpdated` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cached_apps` VALUES (
                        'app_1', 'My Tool', 'أداتي', 'tools', 4.8, '10', '100', '12 MB',
                        12000000, 100, 1000, 900, 1, 0, 0, 1, '🔧', 0, 'desc', '[]',
                        NULL, 'https://example.com/a.apk', '[]', 0, NULL, 'arm64-v8a',
                        'com.example.tool', '1.0.0', NULL, 1000
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cached_apps` VALUES (
                        'game_1', 'Battle Arena', 'ساحة المعركة', 'game_action', 4.9, '50', '500', '120 MB',
                        120000000, 500, 2000, 1900, 2, 1, 1, 1, '⚔️', 0, 'desc', '[]',
                        NULL, 'https://example.com/g.apks', '[]', 0, NULL, 'arm64-v8a',
                        'com.example.arena', '2.0.0', NULL, 2000
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
                .callback(v3Callback)
                .build()
        )
        val db = helper.writableDatabase
        PrimeStoreDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT id, itemType FROM cached_apps ORDER BY id ASC").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("app_1", cursor.getString(0))
            assertEquals("app", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("game_1", cursor.getString(0))
            assertEquals("game", cursor.getString(1))
        }

        db.query("SELECT COUNT(*) FROM categories WHERE type = 'game'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(40, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM categories WHERE type = 'app'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(54, c.getInt(0))
        }

        db.close()
        helper.close()
        dbFile.delete()
    }

    @Test
    fun `strict category filtering never mixes games and apps`() {
        val footballGame = sampleApp("g_sport", "game_sports", StoreCategories.ITEM_TYPE_GAME, AppCategory.SPORTS)
        val fitnessSportsApp = sampleApp("a_sport", "sports", StoreCategories.ITEM_TYPE_APP, AppCategory.SPORTS)

        assertTrue(StoreCategories.matchesCategoryFilter(footballGame, "game_sports", StoreCategories.ITEM_TYPE_GAME))
        assertFalse(StoreCategories.matchesCategoryFilter(fitnessSportsApp, "game_sports", StoreCategories.ITEM_TYPE_GAME))
        assertFalse(StoreCategories.matchesCategoryFilter(footballGame, "sports", StoreCategories.ITEM_TYPE_APP))
        assertTrue(StoreCategories.matchesCategoryFilter(fitnessSportsApp, "sports", StoreCategories.ITEM_TYPE_APP))
        assertFalse(StoreCategories.matchesCategoryFilter(footballGame, "all", StoreCategories.ITEM_TYPE_APP))
        assertTrue(StoreCategories.matchesCategoryFilter(fitnessSportsApp, "all", StoreCategories.ITEM_TYPE_APP))
    }

    @Test
    fun `categories repository supports add rename hide and move`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CategoriesRepository.getInstance(context)
        repo.ensureSeeded()

        val created = repo.addCustomCategory("Racing VR", "سباق واقع افتراضي", "🥽", StoreCategories.ITEM_TYPE_GAME)
        var visibleGames = repo.visibleGameCategories.first()
        assertTrue(visibleGames.any { it.key == created.key })

        repo.updateCategory(created.key, "VR Racing Pro", "سباق برو", "🏎️")
        visibleGames = repo.visibleGameCategories.first()
        val updated = visibleGames.first { it.key == created.key }
        assertEquals("VR Racing Pro", updated.nameEn)
        assertEquals("🏎️", updated.emoji)

        repo.setCategoryHidden(created.key, true)
        visibleGames = repo.visibleGameCategories.first()
        assertFalse(visibleGames.any { it.key == created.key })

        repo.setCategoryHidden(created.key, false)
        val allGamesBeforeMove = repo.allGameCategories.first()
        val firstKey = allGamesBeforeMove.first().key
        val secondKey = allGamesBeforeMove[1].key
        repo.moveCategory(secondKey, StoreCategories.ITEM_TYPE_GAME, moveUp = true)
        val allGamesAfterMove = repo.allGameCategories.first()
        assertEquals(secondKey, allGamesAfterMove[0].key)
        assertEquals(firstKey, allGamesAfterMove[1].key)
    }

    @Test
    fun `resolveAppActionState tracks download downloading installReady installing open and update`() {
        val app = sampleApp("app_x", "tools", StoreCategories.ITEM_TYPE_APP, versionCode = 5L)

        assertEquals(
            AppActionState.Download,
            resolveAppActionState(app, null, isApkReady = false, isInstalling = false, hasSignatureConflict = false, installedInfo = null)
        )

        val dlRunning = DownloadItem("app_x", "App X", "📱", 0L, 0.42f, "2.0 MB/s", "4 MB", "10 MB", false, status = DownloadStatus.DOWNLOADING)
        val downloadingState = resolveAppActionState(app, dlRunning, false, false, false, null)
        assertTrue(downloadingState is AppActionState.Downloading)

        assertEquals(
            AppActionState.InstallReady(false),
            resolveAppActionState(app, null, isApkReady = true, isInstalling = false, hasSignatureConflict = false, installedInfo = null)
        )

        assertEquals(
            AppActionState.Installing,
            resolveAppActionState(app, null, isApkReady = true, isInstalling = true, hasSignatureConflict = false, installedInfo = null)
        )

        assertEquals(
            AppActionState.Open,
            resolveAppActionState(app, null, isApkReady = false, isInstalling = false, hasSignatureConflict = false, installedInfo = InstalledPackageSummary("com.x", 5L, "1.0.5"))
        )

        assertEquals(
            AppActionState.Update(currentVersionCode = 3L, newVersionCode = 5L),
            resolveAppActionState(app, null, isApkReady = false, isInstalling = false, hasSignatureConflict = false, installedInfo = InstalledPackageSummary("com.x", 3L, "1.0.3"))
        )
    }
}
