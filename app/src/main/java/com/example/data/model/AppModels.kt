package com.example.data.model

enum class AppCategory(
    val titleAr: String,
    val titleEn: String,
    val subtitleAr: String,
    val iconEmoji: String,
    val countLabel: String,
    val accentHex: Long
) {
    GAMES("الألعاب", "Games", "أكثر من 5000+ لعبة ممتعة", "🎮", "5000+ لعبة", 0xFFA855F7),
    APPS("التطبيقات", "Apps", "أكثر من 8000+ تطبيق مفيد", "🤖", "8000+ تطبيق", 0xFF22C55E),
    POPULAR("الأكثر تحميلاً", "Most Popular", "الأكثر شعبية هذا الأسبوع", "🔥", "الأكثر شعبية", 0xFFF97316),
    NEW_GAMES("الألعاب الجديدة", "New Games", "أحدث إصدارات الألعاب", "⭐", "جديد هذا الشهر", 0xFFEAB308),
    ENTERTAINMENT("الترفيه", "Entertainment", "أفلام و مسلسلات وبث مباشر", "▶️", "محتوى غير محدود", 0xFFEC4899),
    SPORTS("الرياضة", "Sports", "ألعاب رياضية وبطولات عالمية", "⚽", "بطولات وأندية", 0xFF38BDF8),
    BUSINESS("العملية", "Business", "إنتاجية وأدوات إدارة الأعمال", "💼", "أعمال وإنتاجية", 0xFFF59E0B),
    EDUCATION("التعليم", "Education", "تعليم وتطوير المهارات", "🎓", "دورات وتطوير", 0xFF6366F1),
    MUSIC("موسيقى", "Music", "أغاني وبودكاست ومقاطع صوتية", "🎵", "ملايين المقاطع", 0xFF06B6D4),
    TOOLS("أدوات", "Tools", "أدوات النظام والتحسين", "🔧", "أدوات احترافية", 0xFF8B5CF6),
    VIDEO("فيديو", "Video", "أفلام ومسلسلات وبث مباشر", "▶️", "مقاطع وفيديو", 0xFFEC4899),
    MORE("المزيد", "More", "استكشف كل الأقسام والتصنيفات", "⊞", "جميع الأقسام", 0xFFA855F7)
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
    val screenshotUrls: List<String> = emptyList()
)

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
    val isPaused: Boolean = false
)

data class ReviewItem(
    val id: String,
    val userName: String,
    val userAvatar: String,
    val rating: Int,
    val date: String,
    val comment: String
)
