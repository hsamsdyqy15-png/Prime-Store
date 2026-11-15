package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.StoreCategories
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "translations", primaryKeys = ["appId", "field", "targetLang"])
data class TranslationEntity(
    val appId: String,
    val field: String, // "description", "features", "changelog"
    val targetLang: String,
    val sourceLang: String = "",
    val textHash: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE appId = :appId AND field = :field AND targetLang = :targetLang AND textHash = :textHash LIMIT 1")
    suspend fun getTranslation(appId: String, field: String, targetLang: String, textHash: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTranslation(entity: TranslationEntity)

    @Query("DELETE FROM translations WHERE appId = :appId")
    suspend fun deleteForApp(appId: String)
}

@Entity(tableName = "cached_apps")
data class CachedAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val categoryKey: String,
    val rating: Float,
    val ratingCount: String,
    val downloadCount: String,
    val size: String,
    val sizeBytes: Long = 25_000_000L,
    val downloadCountLong: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val versionCode: Long = 1L,
    val isFeatured: Boolean = false,
    val isTrending: Boolean = false,
    val isNew: Boolean = true,
    val iconEmoji: String,
    val accentColor: Long,
    val descriptionAr: String,
    val featuresJson: String,
    val iconUrl: String?,
    val fileUrl: String?,
    val screenshotUrlsJson: String,
    val isOpenSource: Boolean,
    val sourceCodeUrl: String?,
    val supportedAbis: String,
    val packageName: String?,
    val version: String,
    val changelog: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val itemType: String = StoreCategories.ITEM_TYPE_APP,
    val sha256: String? = null,
    val splitApkUrlsJson: String = "",
    val obbUrl: String? = null
)

data class CachedAppMeta(
    val id: String,
    val updatedAtMillis: Long
)

@Dao
interface CachedAppDao {
    @Query("SELECT * FROM cached_apps ORDER BY createdAtMillis DESC, updatedAtMillis DESC, lastUpdated DESC")
    fun observeAllApps(): Flow<List<CachedAppEntity>>

    @Query("SELECT * FROM cached_apps ORDER BY createdAtMillis DESC, updatedAtMillis DESC, lastUpdated DESC")
    suspend fun getAllCachedAppsDirect(): List<CachedAppEntity>

    @Query("SELECT id, updatedAtMillis FROM cached_apps")
    suspend fun getAllMeta(): List<CachedAppMeta>

    @Query("SELECT COUNT(*) FROM cached_apps")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: CachedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<CachedAppEntity>)

    @Query("UPDATE cached_apps SET packageName = :packageName WHERE id = :appId AND (packageName IS NULL OR packageName = '' OR packageName != :packageName)")
    suspend fun updatePackageName(appId: String, packageName: String)

    @Query("DELETE FROM cached_apps WHERE id = :appId")
    suspend fun deleteById(appId: String)

    @Query("DELETE FROM cached_apps WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Transaction
    suspend fun syncDelta(upserts: List<CachedAppEntity>, deleteIds: List<String>) {
        if (deleteIds.isNotEmpty()) {
            deleteIds.chunked(400).forEach { chunk ->
                deleteByIds(chunk)
            }
        }
        if (upserts.isNotEmpty()) {
            insertApps(upserts)
        }
    }

    @Query("DELETE FROM cached_apps")
    suspend fun clearAll()
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,
    val type: String, // "game" or "app"
    val nameEn: String,
    val nameAr: String,
    val emoji: String,
    val sortOrder: Int,
    val isHidden: Boolean = false,
    val titleResName: String = "",
    val isCustomName: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND isHidden = 0 ORDER BY sortOrder ASC, nameEn ASC")
    fun observeVisibleByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, nameEn ASC")
    fun observeAllByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, nameEn ASC")
    suspend fun getByTypeDirect(type: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE type = :type")
    suspend fun getMaxSortOrder(type: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET isHidden = :hidden, updatedAt = :updatedAt WHERE `key` = :key")
    suspend fun setHidden(key: String, hidden: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET nameEn = :nameEn, nameAr = :nameAr, emoji = :emoji, isCustomName = 1, updatedAt = :updatedAt WHERE `key` = :key")
    suspend fun updateMetadata(key: String, nameEn: String, nameAr: String, emoji: String, updatedAt: Long = System.currentTimeMillis())
}

@Entity(tableName = "problem_reports")
data class ProblemReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: String,
    val appName: String,
    val reason: String,
    val details: String,
    val userEmail: String?,
    val syncedToCloud: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ProblemReportDao {
    @Insert
    suspend fun insertReport(report: ProblemReportEntity): Long

    @Query("SELECT * FROM problem_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ProblemReportEntity>>

    @Query("UPDATE problem_reports SET syncedToCloud = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}

@Database(
    entities = [
        TranslationEntity::class,
        CachedAppEntity::class,
        CategoryEntity::class,
        ProblemReportEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PrimeStoreDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun cachedAppDao(): CachedAppDao
    abstract fun categoryDao(): CategoryDao
    abstract fun problemReportDao(): ProblemReportDao

    companion object {
        @Volatile
        private var INSTANCE: PrimeStoreDatabase? = null

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_apps ADD COLUMN itemType TEXT NOT NULL DEFAULT 'app'")
                db.execSQL("ALTER TABLE cached_apps ADD COLUMN sha256 TEXT")
                db.execSQL("ALTER TABLE cached_apps ADD COLUMN splitApkUrlsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_apps ADD COLUMN obbUrl TEXT")
                db.execSQL(
                    """
                    UPDATE cached_apps SET itemType = 'game'
                    WHERE lower(categoryKey) LIKE 'game\_%' ESCAPE '\'
                       OR lower(categoryKey) IN ('games','action','rpg','simulation','emulators','retro','new_games','mods')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `key` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `nameEn` TEXT NOT NULL,
                        `nameAr` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isHidden` INTEGER NOT NULL,
                        `titleResName` TEXT NOT NULL,
                        `isCustomName` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
                seedCategoriesSql(db)
            }
        }

        fun defaultCategorySeedEntities(now: Long = System.currentTimeMillis()): List<CategoryEntity> {
            val games = StoreCategories.GAMES.mapIndexed { index, item ->
                CategoryEntity(
                    key = item.key,
                    type = StoreCategories.ITEM_TYPE_GAME,
                    nameEn = item.titleEn,
                    nameAr = item.titleAr,
                    emoji = item.emoji,
                    sortOrder = index,
                    isHidden = false,
                    titleResName = StoreCategories.titleResNameFor(item.key),
                    isCustomName = false,
                    updatedAt = now
                )
            }
            val apps = StoreCategories.APPS.mapIndexed { index, item ->
                CategoryEntity(
                    key = item.key,
                    type = StoreCategories.ITEM_TYPE_APP,
                    nameEn = item.titleEn,
                    nameAr = item.titleAr,
                    emoji = item.emoji,
                    sortOrder = index,
                    isHidden = false,
                    titleResName = StoreCategories.titleResNameFor(item.key),
                    isCustomName = false,
                    updatedAt = now
                )
            }
            return games + apps
        }

        fun seedCategoriesSql(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val stmt = db.compileStatement(
                "INSERT OR IGNORE INTO `categories` (`key`, `type`, `nameEn`, `nameAr`, `emoji`, `sortOrder`, `isHidden`, `titleResName`, `isCustomName`, `updatedAt`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            defaultCategorySeedEntities(now).forEach { cat ->
                stmt.clearBindings()
                stmt.bindString(1, cat.key)
                stmt.bindString(2, cat.type)
                stmt.bindString(3, cat.nameEn)
                stmt.bindString(4, cat.nameAr)
                stmt.bindString(5, cat.emoji)
                stmt.bindLong(6, cat.sortOrder.toLong())
                stmt.bindLong(7, if (cat.isHidden) 1L else 0L)
                stmt.bindString(8, cat.titleResName)
                stmt.bindLong(9, if (cat.isCustomName) 1L else 0L)
                stmt.bindLong(10, cat.updatedAt)
                stmt.executeInsert()
            }
        }

        fun getInstance(context: Context): PrimeStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrimeStoreDatabase::class.java,
                    "prime_store_offline.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedCategoriesSql(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            seedCategoriesSql(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
