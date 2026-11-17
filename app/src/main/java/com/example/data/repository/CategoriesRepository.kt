package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.PrimeStoreDatabase
import com.example.data.model.CategoryItem
import com.example.data.model.StoreCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

class CategoriesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = PrimeStoreDatabase.getInstance(appContext).categoryDao()

    val visibleGameCategories: Flow<List<CategoryEntity>> =
        dao.observeVisibleByType(StoreCategories.ITEM_TYPE_GAME).flowOn(Dispatchers.IO)

    val visibleAppCategories: Flow<List<CategoryEntity>> =
        dao.observeVisibleByType(StoreCategories.ITEM_TYPE_APP).flowOn(Dispatchers.IO)

    val allGameCategories: Flow<List<CategoryEntity>> =
        dao.observeAllByType(StoreCategories.ITEM_TYPE_GAME).flowOn(Dispatchers.IO)

    val allAppCategories: Flow<List<CategoryEntity>> =
        dao.observeAllByType(StoreCategories.ITEM_TYPE_APP).flowOn(Dispatchers.IO)

    val allCategoriesFlow: Flow<List<CategoryEntity>> =
        dao.observeAll().flowOn(Dispatchers.IO)

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.getCount() >= 94) return@withContext
        dao.insertIgnoreAll(PrimeStoreDatabase.defaultCategorySeedEntities())
    }

    suspend fun addCustomCategory(
        nameEn: String,
        nameAr: String,
        emoji: String,
        type: String
    ): CategoryEntity = withContext(Dispatchers.IO) {
        val cleanType = StoreCategories.normalizeItemType(type)
        val cleanEn = nameEn.trim().ifBlank { nameAr.trim() }.ifBlank { "Custom" }
        val cleanAr = nameAr.trim().ifBlank { cleanEn }
        val cleanEmoji = emoji.trim().ifBlank { if (cleanType == StoreCategories.ITEM_TYPE_GAME) "🎮" else "📱" }
        val slugBase = cleanEn.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "custom" }
        val keyPrefix = if (cleanType == StoreCategories.ITEM_TYPE_GAME) "game_custom_" else "custom_"
        val uniqueKey = "${keyPrefix}${slugBase}_${System.currentTimeMillis() % 10000}"
        val nextOrder = dao.getMaxSortOrder(cleanType) + 1
        val entity = CategoryEntity(
            key = uniqueKey,
            type = cleanType,
            nameEn = cleanEn,
            nameAr = cleanAr,
            emoji = cleanEmoji,
            sortOrder = nextOrder,
            isHidden = false,
            titleResName = "",
            isCustomName = true,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(entity)
        entity
    }

    suspend fun updateCategory(
        key: String,
        nameEn: String,
        nameAr: String,
        emoji: String
    ) = withContext(Dispatchers.IO) {
        val cleanEn = nameEn.trim()
        val cleanAr = nameAr.trim().ifBlank { cleanEn }
        val cleanEmoji = emoji.trim().ifBlank { "📱" }
        if (cleanEn.isNotBlank()) {
            dao.updateMetadata(key, cleanEn, cleanAr, cleanEmoji)
        }
    }

    suspend fun setCategoryHidden(key: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        dao.setHidden(key, hidden)
    }

    suspend fun moveCategory(key: String, type: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val cleanType = StoreCategories.normalizeItemType(type)
        val list = dao.getByTypeDirect(cleanType).toMutableList()
        val idx = list.indexOfFirst { it.key == key }
        if (idx < 0) return@withContext
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in list.indices) return@withContext
        val current = list[idx]
        list[idx] = list[targetIdx]
        list[targetIdx] = current
        val reindexed = list.mapIndexed { order, entity ->
            entity.copy(sortOrder = order, updatedAt = System.currentTimeMillis())
        }
        dao.updateAll(reindexed)
    }

    fun toCategoryItem(entity: CategoryEntity): CategoryItem {
        val base = StoreCategories.find(entity.key, entity.type)
        val resolvedTitleRes = StoreCategories.getTitleResByName(entity.titleResName)
            ?: base.titleRes
        return CategoryItem(
            key = entity.key,
            titleRes = resolvedTitleRes,
            titleEn = entity.nameEn,
            emoji = entity.emoji.ifBlank { base.emoji },
            groupRes = if (entity.type == StoreCategories.ITEM_TYPE_GAME) R.string.cat_group_games else R.string.cat_group_apps,
            iconResId = base.iconResId,
            titleAr = entity.nameAr,
            type = entity.type,
            isHidden = entity.isHidden,
            sortOrder = entity.sortOrder,
            isCustomName = entity.isCustomName
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: CategoriesRepository? = null

        fun getInstance(context: Context): CategoriesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CategoriesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun resolveDisplayTitle(context: Context, entity: CategoryEntity): String {
            if (!entity.isCustomName && entity.titleResName.isNotBlank()) {
                val resId = StoreCategories.getTitleResByName(entity.titleResName)
                if (resId != null) {
                    return context.getString(resId)
                }
            }
            val lang = SettingsRepository.getInstance(context).getEffectiveLanguageCode()
            return if (lang == "ar" || lang == "fa" || lang == "ur") {
                entity.nameAr.ifBlank { entity.nameEn }
            } else {
                entity.nameEn.ifBlank { entity.nameAr }
            }
        }
    }
}
