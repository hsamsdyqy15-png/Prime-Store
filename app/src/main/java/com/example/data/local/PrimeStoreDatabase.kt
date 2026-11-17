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
    version = 5,
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

        private data class ColumnDef(
            val name: String,
            val type: String,
            val notNull: Boolean,
            val pkOrder: Int,
            val defaultExpr: String
        )

        private val TRANSLATIONS_COLUMNS = listOf(
            ColumnDef("appId", "TEXT", true, 1, "''"),
            ColumnDef("field", "TEXT", true, 2, "''"),
            ColumnDef("targetLang", "TEXT", true, 3, "''"),
            ColumnDef("sourceLang", "TEXT", true, 0, "''"),
            ColumnDef("textHash", "TEXT", true, 0, "''"),
            ColumnDef("translatedText", "TEXT", true, 0, "''"),
            ColumnDef("timestamp", "INTEGER", true, 0, "0")
        )

        private val CACHED_APPS_COLUMNS = listOf(
            ColumnDef("id", "TEXT", true, 1, "''"),
            ColumnDef("name", "TEXT", true, 0, "''"),
            ColumnDef("nameAr", "TEXT", true, 0, "''"),
            ColumnDef("categoryKey", "TEXT", true, 0, "'tools'"),
            ColumnDef("rating", "REAL", true, 0, "4.5"),
            ColumnDef("ratingCount", "TEXT", true, 0, "'0'"),
            ColumnDef("downloadCount", "TEXT", true, 0, "'0'"),
            ColumnDef("size", "TEXT", true, 0, "'0 MB'"),
            ColumnDef("sizeBytes", "INTEGER", true, 0, "25000000"),
            ColumnDef("downloadCountLong", "INTEGER", true, 0, "0"),
            ColumnDef("updatedAtMillis", "INTEGER", true, 0, "0"),
            ColumnDef("createdAtMillis", "INTEGER", true, 0, "0"),
            ColumnDef("versionCode", "INTEGER", true, 0, "1"),
            ColumnDef("isFeatured", "INTEGER", true, 0, "0"),
            ColumnDef("isTrending", "INTEGER", true, 0, "0"),
            ColumnDef("isNew", "INTEGER", true, 0, "1"),
            ColumnDef("iconEmoji", "TEXT", true, 0, "'📱'"),
            ColumnDef("accentColor", "INTEGER", true, 0, "4289222135"),
            ColumnDef("descriptionAr", "TEXT", true, 0, "''"),
            ColumnDef("featuresJson", "TEXT", true, 0, "''"),
            ColumnDef("iconUrl", "TEXT", false, 0, "NULL"),
            ColumnDef("fileUrl", "TEXT", false, 0, "NULL"),
            ColumnDef("screenshotUrlsJson", "TEXT", true, 0, "''"),
            ColumnDef("isOpenSource", "INTEGER", true, 0, "0"),
            ColumnDef("sourceCodeUrl", "TEXT", false, 0, "NULL"),
            ColumnDef("supportedAbis", "TEXT", true, 0, "''"),
            ColumnDef("packageName", "TEXT", false, 0, "NULL"),
            ColumnDef("version", "TEXT", true, 0, "'1.0.0'"),
            ColumnDef("changelog", "TEXT", false, 0, "NULL"),
            ColumnDef("lastUpdated", "INTEGER", true, 0, "0"),
            ColumnDef("itemType", "TEXT", true, 0, "'app'"),
            ColumnDef("sha256", "TEXT", false, 0, "NULL"),
            ColumnDef("splitApkUrlsJson", "TEXT", true, 0, "''"),
            ColumnDef("obbUrl", "TEXT", false, 0, "NULL")
        )

        private val CATEGORIES_COLUMNS = listOf(
            ColumnDef("key", "TEXT", true, 1, "''"),
            ColumnDef("type", "TEXT", true, 0, "'app'"),
            ColumnDef("nameEn", "TEXT", true, 0, "''"),
            ColumnDef("nameAr", "TEXT", true, 0, "''"),
            ColumnDef("emoji", "TEXT", true, 0, "'📱'"),
            ColumnDef("sortOrder", "INTEGER", true, 0, "0"),
            ColumnDef("isHidden", "INTEGER", true, 0, "0"),
            ColumnDef("titleResName", "TEXT", true, 0, "''"),
            ColumnDef("isCustomName", "INTEGER", true, 0, "0"),
            ColumnDef("updatedAt", "INTEGER", true, 0, "0")
        )

        private val PROBLEM_REPORTS_COLUMNS = listOf(
            ColumnDef("id", "INTEGER", true, 1, "0"),
            ColumnDef("appId", "TEXT", true, 0, "''"),
            ColumnDef("appName", "TEXT", true, 0, "''"),
            ColumnDef("reason", "TEXT", true, 0, "''"),
            ColumnDef("details", "TEXT", true, 0, "''"),
            ColumnDef("userEmail", "TEXT", false, 0, "NULL"),
            ColumnDef("syncedToCloud", "INTEGER", true, 0, "0"),
            ColumnDef("timestamp", "INTEGER", true, 0, "0")
        )

        private fun createTranslationsSql(tableName: String) = """
            CREATE TABLE IF NOT EXISTS `$tableName` (
                `appId` TEXT NOT NULL,
                `field` TEXT NOT NULL,
                `targetLang` TEXT NOT NULL,
                `sourceLang` TEXT NOT NULL,
                `textHash` TEXT NOT NULL,
                `translatedText` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`appId`, `field`, `targetLang`)
            )
        """.trimIndent()

        private fun createCachedAppsSql(tableName: String) = """
            CREATE TABLE IF NOT EXISTS `$tableName` (
                `id` TEXT NOT NULL,
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
                `itemType` TEXT NOT NULL,
                `sha256` TEXT,
                `splitApkUrlsJson` TEXT NOT NULL,
                `obbUrl` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent()

        private fun createCategoriesSql(tableName: String) = """
            CREATE TABLE IF NOT EXISTS `$tableName` (
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

        private fun createProblemReportsSql(tableName: String) = """
            CREATE TABLE IF NOT EXISTS `$tableName` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `appId` TEXT NOT NULL,
                `appName` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `details` TEXT NOT NULL,
                `userEmail` TEXT,
                `syncedToCloud` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent()

        private data class ExistingColInfo(
            val name: String,
            val type: String,
            val notNull: Boolean,
            val pk: Int
        )

        private fun readTableInfo(db: SupportSQLiteDatabase, tableName: String): List<ExistingColInfo> {
            val cols = mutableListOf<ExistingColInfo>()
            db.query("PRAGMA table_info(`$tableName`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                val typeIdx = c.getColumnIndex("type")
                val nnIdx = c.getColumnIndex("notnull")
                val pkIdx = c.getColumnIndex("pk")
                while (c.moveToNext()) {
                    cols.add(
                        ExistingColInfo(
                            name = c.getString(nameIdx),
                            type = c.getString(typeIdx).orEmpty().uppercase(),
                            notNull = c.getInt(nnIdx) != 0,
                            pk = c.getInt(pkIdx)
                        )
                    )
                }
            }
            return cols
        }

        private fun reconcileTable(
            db: SupportSQLiteDatabase,
            tableName: String,
            expectedColumns: List<ColumnDef>,
            createSqlFor: (String) -> String
        ) {
            val existing = readTableInfo(db, tableName)
            if (existing.isEmpty()) {
                db.execSQL(createSqlFor(tableName))
                return
            }

            val existingByName = existing.associateBy { it.name }
            val matchesExactly = existing.size == expectedColumns.size && expectedColumns.all { exp ->
                val cur = existingByName[exp.name] ?: return@all false
                cur.type.equals(exp.type, ignoreCase = true) &&
                    cur.notNull == exp.notNull &&
                    cur.pk == exp.pkOrder
            }
            if (matchesExactly) return

            val tmpTable = "${tableName}_v4_tmp"
            db.execSQL("DROP TABLE IF EXISTS `$tmpTable`")
            db.execSQL(createSqlFor(tmpTable))

            val colList = expectedColumns.joinToString(", ") { "`${it.name}`" }
            val selectList = expectedColumns.joinToString(", ") { exp ->
                if (existingByName.containsKey(exp.name)) {
                    if (exp.notNull) {
                        "COALESCE(`${exp.name}`, ${exp.defaultExpr})"
                    } else {
                        "`${exp.name}`"
                    }
                } else {
                    exp.defaultExpr
                }
            }
            val pkCols = expectedColumns.filter { it.pkOrder > 0 }
            val whereClause = if (pkCols.isNotEmpty() && pkCols.all { existingByName.containsKey(it.name) }) {
                " WHERE " + pkCols.joinToString(" AND ") { "`${it.name}` IS NOT NULL" }
            } else {
                ""
            }
            db.execSQL("INSERT OR REPLACE INTO `$tmpTable` ($colList) SELECT $selectList FROM `$tableName`$whereClause")
            db.execSQL("DROP TABLE `$tableName`")
            db.execSQL("ALTER TABLE `$tmpTable` RENAME TO `$tableName`")
        }

        internal fun migrateAnyToV4(db: SupportSQLiteDatabase) {
            reconcileTable(db, "translations", TRANSLATIONS_COLUMNS, ::createTranslationsSql)
            reconcileTable(db, "cached_apps", CACHED_APPS_COLUMNS, ::createCachedAppsSql)
            reconcileTable(db, "categories", CATEGORIES_COLUMNS, ::createCategoriesSql)
            reconcileTable(db, "problem_reports", PROBLEM_REPORTS_COLUMNS, ::createProblemReportsSql)

            db.execSQL(
                """
                UPDATE cached_apps SET itemType = 'game'
                WHERE lower(categoryKey) LIKE 'game\_%' ESCAPE '\'
                   OR lower(categoryKey) IN ('games','action','rpg','simulation','emulators','retro','new_games','mods')
                """.trimIndent()
            )
            seedCategoriesSql(db)
        }

        val MIGRATION_1_4: Migration = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_2_4: Migration = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_1_5: Migration = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_2_5: Migration = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_3_5: Migration = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateAnyToV4(db)
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
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
            if (db.isReadOnly) return
            val seeds = defaultCategorySeedEntities(System.currentTimeMillis())
            val existingCount = runCatching {
                db.query("SELECT COUNT(*) FROM `categories`").use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)
            if (existingCount >= seeds.size) return

            val manageTx = !db.inTransaction()
            if (manageTx) {
                db.beginTransactionNonExclusive()
            }
            try {
                val stmt = db.compileStatement(
                    "INSERT OR IGNORE INTO `categories` (`key`, `type`, `nameEn`, `nameAr`, `emoji`, `sortOrder`, `isHidden`, `titleResName`, `isCustomName`, `updatedAt`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                seeds.forEach { cat ->
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
                if (manageTx) {
                    db.setTransactionSuccessful()
                }
            } finally {
                if (manageTx) {
                    db.endTransaction()
                }
            }
        }

        private const val DB_NAME = "prime_store_offline.db"

        private fun buildDatabase(appContext: Context): PrimeStoreDatabase {
            return Room.databaseBuilder(
                appContext,
                PrimeStoreDatabase::class.java,
                DB_NAME
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    MIGRATION_1_4,
                    MIGRATION_2_4,
                    MIGRATION_3_4,
                    MIGRATION_1_5,
                    MIGRATION_2_5,
                    MIGRATION_3_5,
                    MIGRATION_4_5,
                    MIGRATION_1_2,
                    MIGRATION_2_3
                )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        runCatching { seedCategoriesSql(db) }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        if (!db.isReadOnly) {
                            runCatching { seedCategoriesSql(db) }
                        }
                    }
                })
                .build()
        }

        private fun deleteCorruptDatabaseFiles(appContext: Context) {
            runCatching { appContext.deleteDatabase(DB_NAME) }
            val dbFile = appContext.getDatabasePath(DB_NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                runCatching {
                    val f = java.io.File(dbFile.absolutePath + suffix)
                    if (f.exists()) f.delete()
                }
            }
        }

        fun getInstance(context: Context): PrimeStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val appContext = context.applicationContext
                val candidate = try {
                    val db = buildDatabase(appContext)
                    db.openHelper.writableDatabase
                    db
                } catch (t: Throwable) {
                    com.example.data.supabase.SecureLogger.e(
                        "PrimeStoreDB",
                        "DB_OPEN_CORRUPT_RECOVERY",
                        "Recreating corrupt/incompatible Room database: ${t.message}",
                        t
                    )
                    deleteCorruptDatabaseFiles(appContext)
                    val fresh = buildDatabase(appContext)
                    runCatching { fresh.openHelper.writableDatabase }
                    fresh
                }
                INSTANCE = candidate
                candidate
            }
        }
    }
}
