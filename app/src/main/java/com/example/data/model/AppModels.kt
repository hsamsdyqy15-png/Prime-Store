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
    val version: String = "1.0.0"
)

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
