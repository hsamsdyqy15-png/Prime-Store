package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CachedAppEntity
import com.example.data.local.CachedAppMeta
import com.example.data.local.PrimeStoreDatabase
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.StoreCategories
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = PrimeStoreDatabase.getInstance(appContext).cachedAppDao()

    val appsFlow: Flow<List<AppItem>> = dao.observeAllApps()
        .map { entities ->
            entities.mapIndexed { index, entity -> entityToAppItem(entity, index) }
        }
        .flowOn(Dispatchers.Default)

    suspend fun getAllMeta(): List<CachedAppMeta> = withContext(Dispatchers.IO) {
        dao.getAllMeta()
    }

    suspend fun getCountDirect(): Int = withContext(Dispatchers.IO) {
        dao.getCount()
    }

    suspend fun getAllAppsDirect(): List<AppItem> = withContext(Dispatchers.IO) {
        dao.getAllCachedAppsDirect().mapIndexed { index, entity -> entityToAppItem(entity, index) }
    }

    suspend fun applyServerDelta(upserts: List<AppItem>, deleteIds: List<String>) = withContext(Dispatchers.IO) {
        val entities = upserts.map { appItemToEntity(it) }
        dao.syncDelta(entities, deleteIds)
    }

    suspend fun upsertLocalApp(app: AppItem) = withContext(Dispatchers.IO) {
        dao.upsertApp(appItemToEntity(app))
    }

    suspend fun deleteLocalApp(appId: String) = withContext(Dispatchers.IO) {
        dao.deleteById(appId)
    }

    suspend fun updatePackageName(appId: String, packageName: String) = withContext(Dispatchers.IO) {
        if (packageName.isNotBlank()) {
            dao.updatePackageName(appId, packageName.trim())
        }
    }

    private fun appItemToEntity(app: AppItem): CachedAppEntity {
        val category = SupabaseClient.mapCategory(app.categoryKey)
        val resolvedType = StoreCategories.inferItemType(app.categoryKey, app.itemType, category)
        return CachedAppEntity(
            id = app.id,
            name = app.name,
            nameAr = app.nameAr,
            categoryKey = app.categoryKey,
            rating = app.rating,
            ratingCount = app.ratingCount,
            downloadCount = app.downloadCount,
            size = app.size,
            sizeBytes = app.sizeBytes,
            downloadCountLong = app.downloadCountLong,
            updatedAtMillis = app.updatedAtMillis,
            createdAtMillis = app.createdAtMillis,
            versionCode = app.versionCode,
            isFeatured = app.isFeatured,
            isTrending = app.isTrending,
            isNew = app.isNew,
            iconEmoji = app.iconEmoji,
            accentColor = app.accentColor,
            descriptionAr = app.descriptionAr,
            featuresJson = app.featuresAr.joinToString("|||"),
            iconUrl = app.iconUrl,
            fileUrl = app.fileUrl,
            screenshotUrlsJson = app.screenshotUrls.joinToString("|||"),
            isOpenSource = app.isOpenSource,
            sourceCodeUrl = app.sourceCodeUrl,
            supportedAbis = app.supportedAbis,
            packageName = app.packageName,
            version = app.version,
            changelog = app.changelog,
            lastUpdated = System.currentTimeMillis(),
            itemType = resolvedType,
            sha256 = app.sha256?.takeIf { it.isNotBlank() },
            splitApkUrlsJson = app.splitApkUrls.filter { it.isNotBlank() }.joinToString("|||"),
            obbUrl = app.obbUrl?.takeIf { it.isNotBlank() }
        )
    }

    private fun entityToAppItem(entity: CachedAppEntity, index: Int): AppItem {
        val features = if (entity.featuresJson.isBlank()) emptyList() else entity.featuresJson.split("|||")
        val screenshots = if (entity.screenshotUrlsJson.isBlank()) emptyList() else entity.screenshotUrlsJson.split("|||")
        val splitUrls = if (entity.splitApkUrlsJson.isBlank()) emptyList() else entity.splitApkUrlsJson.split("|||").map { it.trim() }.filter { it.isNotBlank() }
        val category = if (entity.isOpenSource) AppCategory.OPEN_SOURCE else SupabaseClient.mapCategory(entity.categoryKey)
        val resolvedType = StoreCategories.inferItemType(entity.categoryKey, entity.itemType, category)
        val tags = mutableListOf(StoreCategories.getTitle(appContext, entity.categoryKey))
        if (entity.isOpenSource) {
            tags.add(appContext.getString(R.string.cat_open_source))
        }
        if (entity.supportedAbis.isNotBlank()) {
            tags.add(entity.supportedAbis.substringBefore("(").trim())
        }
        return AppItem(
            id = entity.id,
            name = entity.name,
            nameAr = entity.nameAr,
            category = category,
            tags = tags,
            rating = entity.rating,
            ratingCount = entity.ratingCount,
            downloadCount = entity.downloadCount,
            size = entity.size,
            sizeBytes = entity.sizeBytes,
            iconEmoji = entity.iconEmoji,
            accentColor = entity.accentColor,
            descriptionAr = entity.descriptionAr,
            descriptionEn = entity.descriptionAr,
            featuresAr = features,
            screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95),
            isFeatured = index < 2 || entity.isFeatured,
            isTrending = entity.isTrending || entity.downloadCountLong >= 1000L || index % 2 == 0,
            isNew = entity.isNew,
            iconUrl = entity.iconUrl,
            fileUrl = entity.fileUrl,
            screenshotUrls = screenshots,
            isOpenSource = entity.isOpenSource,
            sourceCodeUrl = entity.sourceCodeUrl,
            supportedAbis = entity.supportedAbis,
            packageName = entity.packageName,
            version = entity.version,
            categoryKey = entity.categoryKey,
            changelog = entity.changelog,
            versionCode = entity.versionCode,
            updatedAtMillis = entity.updatedAtMillis,
            createdAtMillis = entity.createdAtMillis,
            downloadCountLong = entity.downloadCountLong,
            itemType = resolvedType,
            sha256 = entity.sha256,
            splitApkUrls = splitUrls,
            obbUrl = entity.obbUrl
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AppsRepository? = null

        fun getInstance(context: Context): AppsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
