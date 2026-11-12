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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "translations", primaryKeys = ["appId", "field", "targetLang"])
data class TranslationEntity(
    val appId: String,
    val field: String, // "description", "features", "changelog"
    val targetLang: String,
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
    val iconEmoji: String,
    val accentColor: Long,
    val descriptionAr: String,
    val featuresJson: String, // comma or newline separated
    val iconUrl: String?,
    val fileUrl: String?,
    val screenshotUrlsJson: String,
    val isOpenSource: Boolean,
    val sourceCodeUrl: String?,
    val supportedAbis: String,
    val packageName: String?,
    val version: String,
    val changelog: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface CachedAppDao {
    @Query("SELECT * FROM cached_apps ORDER BY lastUpdated DESC")
    fun getAllCachedApps(): Flow<List<CachedAppEntity>>

    @Query("SELECT * FROM cached_apps ORDER BY lastUpdated DESC")
    suspend fun getAllCachedAppsDirect(): List<CachedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<CachedAppEntity>)

    @Query("DELETE FROM cached_apps")
    suspend fun clearAll()
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
    entities = [TranslationEntity::class, CachedAppEntity::class, ProblemReportEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PrimeStoreDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun cachedAppDao(): CachedAppDao
    abstract fun problemReportDao(): ProblemReportDao

    companion object {
        @Volatile
        private var INSTANCE: PrimeStoreDatabase? = null

        fun getInstance(context: Context): PrimeStoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrimeStoreDatabase::class.java,
                    "prime_store_offline.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
