package com.example.data.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

enum class AppCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val titleEn: String,
    val iconEmoji: String,
    val accentHex: Long,
    val iconResId: Int
) {
    GAMES(R.string.cat_games, R.string.cat_group_games, "Games", "🎮", 0xFFA855F7, R.drawable.ic_cat_games),
    APPS(R.string.cat_apps, R.string.cat_group_apps, "Apps", "🤖", 0xFF22C55E, R.drawable.ic_cat_apps),
    POPULAR(R.string.cat_popular, R.string.cat_group_general, "Most Popular", "🔥", 0xFFF97316, R.drawable.ic_cat_popular),
    NEW_GAMES(R.string.cat_new_games, R.string.cat_group_games, "New Games", "⭐", 0xFFEAB308, R.drawable.ic_cat_new_games),
    ENTERTAINMENT(R.string.cat_entertainment, R.string.cat_group_apps, "Entertainment", "▶️", 0xFFEC4899, R.drawable.ic_cat_entertainment),
    SPORTS(R.string.cat_sports, R.string.cat_group_apps, "Sports", "⚽", 0xFF38BDF8, R.drawable.ic_cat_sports),
    BUSINESS(R.string.cat_business, R.string.cat_group_apps, "Business", "💼", 0xFFF59E0B, R.drawable.ic_cat_business),
    EDUCATION(R.string.cat_education, R.string.cat_group_apps, "Education", "🎓", 0xFF6366F1, R.drawable.ic_cat_education),
    MUSIC(R.string.cat_music, R.string.cat_group_apps, "Music & Audio", "🎵", 0xFF06B6D4, R.drawable.ic_cat_music),
    TOOLS(R.string.cat_tools, R.string.cat_group_apps, "Tools", "🔧", 0xFF8B5CF6, R.drawable.ic_cat_tools),
    PRODUCTIVITY(R.string.cat_productivity, R.string.cat_group_apps, "Productivity", "📊", 0xFF10B981, R.drawable.ic_cat_productivity),
    PERSONALIZATION(R.string.cat_personalization, R.string.cat_group_apps, "Personalization", "🎨", 0xFFF43F5E, R.drawable.ic_cat_personalization),
    MEDIA(R.string.cat_media, R.string.cat_group_apps, "Media & Video", "🎬", 0xFFEC4899, R.drawable.ic_cat_media),
    SOCIAL(R.string.cat_social, R.string.cat_group_apps, "Social", "💬", 0xFF3B82F6, R.drawable.ic_cat_social),
    ACTION(R.string.cat_action, R.string.cat_group_games, "Action", "⚔️", 0xFFEF4444, R.drawable.ic_cat_action),
    RPG(R.string.cat_rpg, R.string.cat_group_games, "RPG", "🛡️", 0xFF8B5CF6, R.drawable.ic_cat_rpg),
    SIMULATION(R.string.cat_simulation, R.string.cat_group_games, "Simulation", "✈️", 0xFF06B6D4, R.drawable.ic_cat_simulation),
    EMULATORS(R.string.cat_emulators, R.string.cat_group_games, "Emulators", "🕹️", 0xFFD97706, R.drawable.ic_cat_emulators),
    RETRO(R.string.cat_retro, R.string.cat_group_games, "Retro & Classic", "👾", 0xFF6366F1, R.drawable.ic_cat_retro),
    ROOT_TOOLS(R.string.cat_root_tools, R.string.cat_group_apps, "Root Tools", "⚡", 0xFFDC2626, R.drawable.ic_cat_root_tools),
    OPEN_SOURCE(R.string.cat_open_source, R.string.cat_group_apps, "Open Source", "🔓", 0xFF3B82F6, R.drawable.ic_cat_open_source),
    VIDEO(R.string.cat_video, R.string.cat_group_apps, "Video", "📹", 0xFFEC4899, R.drawable.ic_cat_video),
    MORE(R.string.cat_more, R.string.cat_group_general, "More", "⊞", 0xFFA855F7, R.drawable.ic_cat_more)
}

data class CategoryItem(
    val key: String,
    @StringRes val titleRes: Int,
    val titleEn: String,
    val emoji: String,
    @StringRes val groupRes: Int,
    val iconResId: Int = R.drawable.ic_cat_apps
)

object StoreCategories {
    val GENERAL = listOf(
        CategoryItem("all", R.string.cat_all, "All", "🌐", R.string.cat_group_general, R.drawable.ic_cat_more),
        CategoryItem("trending", R.string.cat_popular, "Trending", "🔥", R.string.cat_group_general, R.drawable.ic_cat_popular),
        CategoryItem("games", R.string.cat_games, "Games", "🎮", R.string.cat_group_general, R.drawable.ic_cat_games),
        CategoryItem("apps", R.string.cat_apps, "Apps", "🤖", R.string.cat_group_general, R.drawable.ic_cat_apps)
    )

    val APPS = listOf(
        CategoryItem("art_design", R.string.cat_art_design, "Art & Design", "🎨", R.string.cat_group_apps),
        CategoryItem("auto_vehicles", R.string.cat_auto_vehicles, "Auto & Vehicles", "🚗", R.string.cat_group_apps),
        CategoryItem("beauty", R.string.cat_beauty, "Beauty", "💄", R.string.cat_group_apps),
        CategoryItem("books_reference", R.string.cat_books_reference, "Books & Reference", "📚", R.string.cat_group_apps),
        CategoryItem("business", R.string.cat_business, "Business", "💼", R.string.cat_group_apps, R.drawable.ic_cat_business),
        CategoryItem("comics", R.string.cat_comics, "Comics", "💬", R.string.cat_group_apps),
        CategoryItem("communication", R.string.cat_communication, "Communication", "📞", R.string.cat_group_apps, R.drawable.ic_cat_social),
        CategoryItem("dating", R.string.cat_dating, "Dating", "❤️", R.string.cat_group_apps),
        CategoryItem("education", R.string.cat_education, "Education", "🎓", R.string.cat_group_apps, R.drawable.ic_cat_education),
        CategoryItem("entertainment", R.string.cat_entertainment, "Entertainment", "▶️", R.string.cat_group_apps, R.drawable.ic_cat_entertainment),
        CategoryItem("events", R.string.cat_events, "Events", "🎟️", R.string.cat_group_apps),
        CategoryItem("finance", R.string.cat_finance, "Finance", "💰", R.string.cat_group_apps),
        CategoryItem("food_drink", R.string.cat_food_drink, "Food & Drink", "🍔", R.string.cat_group_apps),
        CategoryItem("health_fitness", R.string.cat_health_fitness, "Health & Fitness", "🏃", R.string.cat_group_apps),
        CategoryItem("house_home", R.string.cat_house_home, "House & Home", "🏡", R.string.cat_group_apps),
        CategoryItem("libraries_demo", R.string.cat_libraries_demo, "Libraries & Demo", "📦", R.string.cat_group_apps),
        CategoryItem("lifestyle", R.string.cat_lifestyle, "Lifestyle", "✨", R.string.cat_group_apps),
        CategoryItem("maps_navigation", R.string.cat_maps_navigation, "Maps & Navigation", "🗺️", R.string.cat_group_apps),
        CategoryItem("medical", R.string.cat_medical, "Medical", "🩺", R.string.cat_group_apps),
        CategoryItem("music_audio", R.string.cat_music, "Music & Audio", "🎵", R.string.cat_group_apps, R.drawable.ic_cat_music),
        CategoryItem("news_magazines", R.string.cat_news_magazines, "News & Magazines", "📰", R.string.cat_group_apps),
        CategoryItem("parenting", R.string.cat_parenting, "Parenting", "🍼", R.string.cat_group_apps),
        CategoryItem("personalization", R.string.cat_personalization, "Personalization", "🎭", R.string.cat_group_apps, R.drawable.ic_cat_personalization),
        CategoryItem("photography", R.string.cat_photography, "Photography", "📷", R.string.cat_group_apps),
        CategoryItem("productivity", R.string.cat_productivity, "Productivity", "📊", R.string.cat_group_apps, R.drawable.ic_cat_productivity),
        CategoryItem("shopping", R.string.cat_shopping, "Shopping", "🛍️", R.string.cat_group_apps),
        CategoryItem("social", R.string.cat_social, "Social", "👥", R.string.cat_group_apps, R.drawable.ic_cat_social),
        CategoryItem("sports", R.string.cat_sports, "Sports", "⚽", R.string.cat_group_apps, R.drawable.ic_cat_sports),
        CategoryItem("tools", R.string.cat_tools, "Tools", "🔧", R.string.cat_group_apps, R.drawable.ic_cat_tools),
        CategoryItem("travel_local", R.string.cat_travel_local, "Travel & Local", "✈️", R.string.cat_group_apps),
        CategoryItem("video_players_editors", R.string.cat_video, "Video Players & Editors", "🎬", R.string.cat_group_apps, R.drawable.ic_cat_video),
        CategoryItem("weather", R.string.cat_weather, "Weather", "🌤️", R.string.cat_group_apps)
    )

    val GAMES = listOf(
        CategoryItem("game_action", R.string.cat_action, "Action", "⚔️", R.string.cat_group_games, R.drawable.ic_cat_action),
        CategoryItem("game_adventure", R.string.cat_game_adventure, "Adventure", "🧭", R.string.cat_group_games),
        CategoryItem("game_arcade", R.string.cat_game_arcade, "Arcade", "🕹️", R.string.cat_group_games),
        CategoryItem("game_board", R.string.cat_game_board, "Board", "🎲", R.string.cat_group_games),
        CategoryItem("game_card", R.string.cat_game_card, "Card", "🃏", R.string.cat_group_games),
        CategoryItem("game_casino", R.string.cat_game_casino, "Casino", "🎰", R.string.cat_group_games),
        CategoryItem("game_casual", R.string.cat_game_casual, "Casual", "🎈", R.string.cat_group_games),
        CategoryItem("game_educational", R.string.cat_game_educational, "Educational", "🧩", R.string.cat_group_games),
        CategoryItem("game_music", R.string.cat_game_music, "Music Games", "🎼", R.string.cat_group_games),
        CategoryItem("game_puzzle", R.string.cat_game_puzzle, "Puzzle", "🧠", R.string.cat_group_games),
        CategoryItem("game_racing", R.string.cat_game_racing, "Racing", "🏎️", R.string.cat_group_games),
        CategoryItem("game_role_playing", R.string.cat_game_role_playing, "Role Playing", "🛡️", R.string.cat_group_games, R.drawable.ic_cat_rpg),
        CategoryItem("game_simulation", R.string.cat_simulation, "Simulation", "🛫", R.string.cat_group_games, R.drawable.ic_cat_simulation),
        CategoryItem("game_strategy", R.string.cat_game_strategy, "Strategy", "🏰", R.string.cat_group_games),
        CategoryItem("game_sports", R.string.cat_sports, "Sports", "🏆", R.string.cat_group_games, R.drawable.ic_cat_sports),
        CategoryItem("game_trivia", R.string.cat_game_trivia, "Trivia", "❓", R.string.cat_group_games),
        CategoryItem("game_word", R.string.cat_game_word, "Word", "🔤", R.string.cat_group_games)
    )

    val ALL_SELECTIONS: List<CategoryItem> = GENERAL.filter { it.key != "all" } + APPS + GAMES
    val ALL_FILTER_ITEMS: List<CategoryItem> = listOf(GENERAL.first()) + GENERAL.drop(1) + APPS + GAMES

    private val LEGACY_RES_MAP = mapOf(
        "music" to R.string.cat_music,
        "video" to R.string.cat_video,
        "root_tools" to R.string.cat_root_tools,
        "open_source" to R.string.cat_open_source,
        "new_games" to R.string.cat_new_games,
        "popular" to R.string.cat_popular,
        "tools" to R.string.cat_tools,
        "games" to R.string.cat_games,
        "apps" to R.string.cat_apps,
        "sports" to R.string.cat_sports,
        "education" to R.string.cat_education,
        "entertainment" to R.string.cat_entertainment,
        "business" to R.string.cat_business,
        "media" to R.string.cat_media,
        "action" to R.string.cat_action,
        "rpg" to R.string.cat_rpg,
        "simulation" to R.string.cat_simulation,
        "emulators" to R.string.cat_emulators,
        "retro" to R.string.cat_retro,
        "more" to R.string.cat_more
    )

    @StringRes
    fun getTitleRes(key: String?): Int {
        if (key.isNullOrBlank()) return R.string.cat_apps
        val clean = key.trim().lowercase()
        val match = ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
        if (match != null) return match.titleRes
        return LEGACY_RES_MAP[clean] ?: R.string.cat_apps
    }

    fun getTitle(context: Context, key: String?): String {
        return context.getString(getTitleRes(key))
    }

    fun getEmoji(key: String?): String {
        if (key.isNullOrBlank()) return "📱"
        val clean = key.trim().lowercase()
        return ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }?.emoji ?: "📱"
    }

    fun find(key: String?): CategoryItem {
        val clean = key?.trim()?.lowercase().orEmpty()
        return ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
            ?: CategoryItem(
                key = clean.ifBlank { "apps" },
                titleRes = getTitleRes(clean),
                titleEn = clean.ifBlank { "Apps" },
                emoji = getEmoji(clean),
                groupRes = R.string.cat_group_apps
            )
    }
}

data class StoreUpdateInfo(
    val id: Int = 1,
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val notes: String = "",
    val apkUrl: String = "",
    val updatedAt: String? = null
)

data class AppItem(
    val id: String,
    val name: String,
    val nameAr: String = name,
    val category: AppCategory,
    val tags: List<String>,
    val rating: Float,
    val ratingCount: String,
    val downloadCount: String,
    val size: String,
    val sizeBytes: Long,
    val iconEmoji: String,
    val accentColor: Long,
    val bannerRes: Int? = null,
    val descriptionAr: String,
    val descriptionEn: String,
    val featuresAr: List<String>,
    val screenshotsColors: List<Long>,
    val isFeatured: Boolean = false,
    val isTrending: Boolean = false,
    val isNew: Boolean = false,
    val iconUrl: String? = null,
    val fileUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val isOpenSource: Boolean = false,
    val sourceCodeUrl: String? = null,
    val supportedAbis: String = "Universal (All Architectures)",
    val packageName: String? = null,
    val version: String = "1.0.0",
    val categoryKey: String = "apps",
    val changelog: String? = null,
    val versionCode: Long = 1L
) {
    @Composable
    fun localizedCategoryTitle(): String {
        val key = categoryKey.ifBlank { category.name.lowercase() }
        return stringResource(id = StoreCategories.getTitleRes(key))
    }

    val displayCategoryTitle: String
        get() = StoreCategories.find(categoryKey.ifBlank { category.name.lowercase() }).titleEn

    val displayVersionBadge: String
        get() = if (version.isNotBlank()) "v$version" else "v1.0.0"
}

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}

data class DownloadItem(
    val appId: String,
    val appName: String,
    val appIcon: String,
    val accentColor: Long,
    val progress: Float,
    val speed: String,
    val downloadedSize: String,
    val totalSize: String,
    val isCompleted: Boolean,
    val isPaused: Boolean = false,
    val status: DownloadStatus = if (isCompleted) DownloadStatus.COMPLETED else (if (isPaused) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING),
    val apkFilePath: String? = null,
    val errorMessage: String? = null
)

data class ReviewItem(
    val id: String,
    val userName: String,
    val userAvatar: String,
    val rating: Int,
    val date: String,
    val comment: String
)
