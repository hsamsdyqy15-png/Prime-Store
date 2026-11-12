package com.example.data.model

import com.example.R

enum class AppCategory(
    val titleAr: String,
    val titleEn: String,
    val subtitleAr: String,
    val iconEmoji: String,
    val countLabel: String,
    val accentHex: Long,
    val iconResId: Int
) {
    GAMES("الألعاب", "Games", "ألعاب ممتعة وحصرية", "🎮", "ألعاب", 0xFFA855F7, R.drawable.ic_cat_games),
    APPS("التطبيقات", "Apps", "تطبيقات مفيدة ومتنوعة", "🤖", "تطبيقات", 0xFF22C55E, R.drawable.ic_cat_apps),
    POPULAR("الأكثر تحميلاً", "Most Popular", "الأكثر شعبية هذا الأسبوع", "🔥", "رائج", 0xFFF97316, R.drawable.ic_cat_popular),
    NEW_GAMES("الألعاب الجديدة", "New Games", "أحدث إصدارات الألعاب", "⭐", "جديد", 0xFFEAB308, R.drawable.ic_cat_new_games),
    ENTERTAINMENT("الترفيه", "Entertainment", "أفلام ومسلسلات وبث مباشر", "▶️", "ترفيه", 0xFFEC4899, R.drawable.ic_cat_entertainment),
    SPORTS("الرياضة", "Sports", "ألعاب رياضية وبطولات", "⚽", "رياضة", 0xFF38BDF8, R.drawable.ic_cat_sports),
    BUSINESS("الإنتاجية والعمل", "Productivity", "إنتاجية وأدوات عمل", "💼", "إنتاجية", 0xFFF59E0B, R.drawable.ic_cat_business),
    EDUCATION("التعليم", "Education", "تعليم وتطوير مهارات", "🎓", "تعليم", 0xFF6366F1, R.drawable.ic_cat_education),
    MUSIC("موسيقى وصوتيات", "Music", "أغاني وبودكاست ومقاطع", "🎵", "صوتيات", 0xFF06B6D4, R.drawable.ic_cat_music),
    TOOLS("أدوات النظام", "Tools", "أدوات النظام والتحسين", "🔧", "أدوات", 0xFF8B5CF6, R.drawable.ic_cat_tools),
    PRODUCTIVITY("الإنتاجية", "Productivity", "مهام وتنظيم الوقت", "📊", "إنتاجية", 0xFF10B981, R.drawable.ic_cat_productivity),
    PERSONALIZATION("التخصيص", "Personalization", "ثيمات وخلفيات وأيقونات", "🎨", "تخصيص", 0xFFF43F5E, R.drawable.ic_cat_personalization),
    MEDIA("الوسائط", "Media", "مشغلات فيديو وصوتيات", "🎬", "وسائط", 0xFFEC4899, R.drawable.ic_cat_media),
    SOCIAL("التواصل الاجتماعي", "Social", "تواصل ومراسلة فورية", "💬", "تواصل", 0xFF3B82F6, R.drawable.ic_cat_social),
    ACTION("أكشن ومغامرات", "Action", "ألعاب حركة وقتال حماسية", "⚔️", "أكشن", 0xFFEF4444, R.drawable.ic_cat_action),
    RPG("تقمص الأدوار RPG", "RPG", "ألعاب تقمص أدوار وقصص", "🛡️", "RPG", 0xFF8B5CF6, R.drawable.ic_cat_rpg),
    SIMULATION("محاكاة", "Simulation", "ألعاب محاكاة واقعية", "✈️", "محاكاة", 0xFF06B6D4, R.drawable.ic_cat_simulation),
    EMULATORS("المحاكيات", "Emulators", "تشغيل ألعاب الكونسول القديمة", "🕹️", "محاكيات", 0xFFD97706, R.drawable.ic_cat_emulators),
    RETRO("ريترو وكلاسيك", "Retro", "ألعاب الزمن الجميل الكلاسيكية", "👾", "ريترو", 0xFF6366F1, R.drawable.ic_cat_retro),
    ROOT_TOOLS("أدوات الروت", "Root Tools", "أدوات النظام المتقدمة وصلاحيات الروت", "⚡", "روت", 0xFFDC2626, R.drawable.ic_cat_root_tools),
    OPEN_SOURCE("مفتوح المصدر", "Open Source", "تطبيقات وألعاب FOSS مفتوحة المصدر", "🔓", "مفتوح المصدر", 0xFF3B82F6, R.drawable.ic_cat_open_source),
    VIDEO("فيديو", "Video", "أفلام ومقاطع فيديو", "📹", "فيديو", 0xFFEC4899, R.drawable.ic_cat_video),
    MORE("المزيد", "More", "استكشف كافة الأقسام", "⊞", "الكل", 0xFFA855F7, R.drawable.ic_cat_more)
}

data class CategoryItem(
    val key: String,
    val titleAr: String,
    val titleEn: String,
    val emoji: String,
    val group: String, // "عام", "تطبيقات", "ألعاب"
    val iconResId: Int = R.drawable.ic_cat_apps
)

object StoreCategories {
    // 1) عام (General)
    val GENERAL = listOf(
        CategoryItem("all", "جميع الأصناف", "All", "🌐", "عام", R.drawable.ic_cat_more),
        CategoryItem("trending", "الأكثر تحميلاً", "Trending", "🔥", "عام", R.drawable.ic_cat_popular),
        CategoryItem("games", "ألعاب (عام)", "Games", "🎮", "عام", R.drawable.ic_cat_games),
        CategoryItem("apps", "تطبيقات (عام)", "Apps", "🤖", "عام", R.drawable.ic_cat_apps)
    )

    // 2) تطبيقات (Google Play Apps Categories)
    val APPS = listOf(
        CategoryItem("art_design", "فن وتصميم", "Art & Design", "🎨", "تطبيقات"),
        CategoryItem("auto_vehicles", "سيارات ومركبات", "Auto & Vehicles", "🚗", "تطبيقات"),
        CategoryItem("beauty", "جمال", "Beauty", "💄", "تطبيقات"),
        CategoryItem("books_reference", "كتب ومراجع", "Books & Reference", "📚", "تطبيقات"),
        CategoryItem("business", "أعمال", "Business", "💼", "تطبيقات", R.drawable.ic_cat_business),
        CategoryItem("comics", "قصص مصورة", "Comics", "💬", "تطبيقات"),
        CategoryItem("communication", "تواصل", "Communication", "📞", "تطبيقات", R.drawable.ic_cat_social),
        CategoryItem("dating", "تعارف", "Dating", "❤️", "تطبيقات"),
        CategoryItem("education", "تعليم", "Education", "🎓", "تطبيقات", R.drawable.ic_cat_education),
        CategoryItem("entertainment", "ترفيه", "Entertainment", "▶️", "تطبيقات", R.drawable.ic_cat_entertainment),
        CategoryItem("events", "فعاليات", "Events", "🎟️", "تطبيقات"),
        CategoryItem("finance", "مال", "Finance", "💰", "تطبيقات"),
        CategoryItem("food_drink", "طعام وشراب", "Food & Drink", "🍔", "تطبيقات"),
        CategoryItem("health_fitness", "صحة ولياقة", "Health & Fitness", "🏃", "تطبيقات"),
        CategoryItem("house_home", "المنزل والديكور", "House & Home", "🏡", "تطبيقات"),
        CategoryItem("libraries_demo", "مكتبات وعروض", "Libraries & Demo", "📦", "تطبيقات"),
        CategoryItem("lifestyle", "نمط الحياة", "Lifestyle", "✨", "تطبيقات"),
        CategoryItem("maps_navigation", "خرائط وملاحة", "Maps & Navigation", "🗺️", "تطبيقات"),
        CategoryItem("medical", "طبي", "Medical", "🩺", "تطبيقات"),
        CategoryItem("music_audio", "موسيقى وصوتيات", "Music & Audio", "🎵", "تطبيقات", R.drawable.ic_cat_music),
        CategoryItem("news_magazines", "أخبار ومجلات", "News & Magazines", "📰", "تطبيقات"),
        CategoryItem("parenting", "الأبوة والأمومة", "Parenting", "🍼", "تطبيقات"),
        CategoryItem("personalization", "تخصيص", "Personalization", "🎭", "تطبيقات", R.drawable.ic_cat_personalization),
        CategoryItem("photography", "تصوير", "Photography", "📷", "تطبيقات"),
        CategoryItem("productivity", "إنتاجية", "Productivity", "📊", "تطبيقات", R.drawable.ic_cat_productivity),
        CategoryItem("shopping", "تسوق", "Shopping", "🛍️", "تطبيقات"),
        CategoryItem("social", "اجتماعي", "Social", "👥", "تطبيقات", R.drawable.ic_cat_social),
        CategoryItem("sports", "رياضة", "Sports", "⚽", "تطبيقات", R.drawable.ic_cat_sports),
        CategoryItem("tools", "أدوات", "Tools", "🔧", "تطبيقات", R.drawable.ic_cat_tools),
        CategoryItem("travel_local", "سفر ومحلي", "Travel & Local", "✈️", "تطبيقات"),
        CategoryItem("video_players_editors", "مشغلات ومحررات الفيديو", "Video Players & Editors", "🎬", "تطبيقات", R.drawable.ic_cat_video),
        CategoryItem("weather", "طقس", "Weather", "🌤️", "تطبيقات")
    )

    // 3) ألعاب (Google Play Games Categories)
    val GAMES = listOf(
        CategoryItem("game_action", "أكشن", "Action", "⚔️", "ألعاب", R.drawable.ic_cat_action),
        CategoryItem("game_adventure", "مغامرات", "Adventure", "🧭", "ألعاب"),
        CategoryItem("game_arcade", "أركيد", "Arcade", "🕹️", "ألعاب"),
        CategoryItem("game_board", "ألواح", "Board", "🎲", "ألعاب"),
        CategoryItem("game_card", "ورق", "Card", "🃏", "ألعاب"),
        CategoryItem("game_casino", "كازينو", "Casino", "🎰", "ألعاب"),
        CategoryItem("game_casual", "ترفيهية خفيفة", "Casual", "🎈", "ألعاب"),
        CategoryItem("game_educational", "تعليمية", "Educational", "🧩", "ألعاب"),
        CategoryItem("game_music", "موسيقى", "Music", "🎼", "ألعاب"),
        CategoryItem("game_puzzle", "ألغاز", "Puzzle", "🧠", "ألعاب"),
        CategoryItem("game_racing", "سباق", "Racing", "🏎️", "ألعاب"),
        CategoryItem("game_role_playing", "تقمص أدوار", "Role Playing", "🛡️", "ألعاب", R.drawable.ic_cat_rpg),
        CategoryItem("game_simulation", "محاكاة", "Simulation", "🛫", "ألعاب", R.drawable.ic_cat_simulation),
        CategoryItem("game_strategy", "استراتيجية", "Strategy", "🏰", "ألعاب"),
        CategoryItem("game_sports", "رياضة", "Sports", "🏆", "ألعاب", R.drawable.ic_cat_sports),
        CategoryItem("game_trivia", "معلومات عامة", "Trivia", "❓", "ألعاب"),
        CategoryItem("game_word", "كلمات", "Word", "🔤", "ألعاب")
    )

    // All categories combined for selection & filtering
    val ALL_SELECTIONS: List<CategoryItem> = GENERAL.filter { it.key != "all" } + APPS + GAMES

    // All including "all" filter
    val ALL_FILTER_ITEMS: List<CategoryItem> = listOf(GENERAL.first()) + GENERAL.drop(1) + APPS + GAMES

    // Legacy Key Mappings
    private val LEGACY_MAP = mapOf(
        "music" to "موسيقى وصوتيات",
        "video" to "مشغلات ومحررات الفيديو",
        "root_tools" to "أدوات الروت",
        "open_source" to "مفتوح المصدر",
        "new_games" to "ألعاب جديدة",
        "popular" to "الأكثر تحميلاً",
        "tools" to "أدوات",
        "games" to "ألعاب (عام)",
        "apps" to "تطبيقات (عام)",
        "sports" to "رياضة",
        "education" to "تعليم",
        "entertainment" to "ترفيه",
        "business" to "أعمال"
    )

    fun getTitleAr(key: String?): String {
        if (key.isNullOrBlank()) return "تطبيقات"
        val clean = key.trim().lowercase()
        val match = ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
        if (match != null) return match.titleAr
        return LEGACY_MAP[clean] ?: clean
    }

    fun getEmoji(key: String?): String {
        if (key.isNullOrBlank()) return "📱"
        val clean = key.trim().lowercase()
        return ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }?.emoji ?: "📱"
    }

    fun find(key: String?): CategoryItem {
        val clean = key?.trim()?.lowercase().orEmpty()
        return ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
            ?: CategoryItem(key = clean.ifBlank { "apps" }, titleAr = getTitleAr(clean), titleEn = "App", emoji = getEmoji(clean), group = "تطبيقات")
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
    val displayCategoryTitle: String
        get() = StoreCategories.getTitleAr(categoryKey.ifBlank { category.name.lowercase() })

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
