package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class PrimeStoreRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("prime_store_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.Default)

    // Optional remote JSON URL configuration for dynamic updates
    var remoteAppsUrl: String = ""

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val apps: StateFlow<List<AppItem>> = _apps.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _reviews = MutableStateFlow<Map<String, List<ReviewItem>>>(emptyMap())
    val reviews: StateFlow<Map<String, List<ReviewItem>>> = _reviews.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        loadCatalog()
        refreshFromSupabase()
        loadFavoritesFromPrefs()
        loadSearchHistoryFromPrefs()
        initDefaultDownloads()
        initDefaultReviews()
    }

    fun refreshFromSupabase() {
        scope.launch {
            try {
                val remoteApps = com.example.data.supabase.SupabaseClient.fetchApps()
                if (remoteApps.isNotEmpty()) {
                    val current = _apps.value.toMutableList()
                    val merged = mutableListOf<AppItem>()
                    merged.addAll(remoteApps)
                    for (local in current) {
                        if (merged.none { it.id == local.id || it.name.equals(local.name, ignoreCase = true) }) {
                            merged.add(local)
                        }
                    }
                    _apps.value = merged
                }
            } catch (e: Exception) {
                // Keep local catalog fallback
            }
        }
    }

    private fun loadFavoritesFromPrefs() {
        val saved = prefs.getStringSet("favorite_ids", setOf("gta_v", "minecraft", "roblox", "stumble_guys")) ?: emptySet()
        _favorites.value = saved
    }

    private fun saveFavoritesToPrefs() {
        prefs.edit().putStringSet("favorite_ids", _favorites.value).apply()
    }

    private fun loadSearchHistoryFromPrefs() {
        val raw = prefs.getString("search_history", "Minecraft,GTA V,Roblox,Free Fire") ?: ""
        if (raw.isNotEmpty()) {
            _searchHistory.value = raw.split(",").filter { it.isNotBlank() }
        }
    }

    private fun saveSearchHistoryToPrefs() {
        prefs.edit().putString("search_history", _searchHistory.value.joinToString(",")).apply()
    }

    fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 10) current.removeAt(current.size - 1)
        _searchHistory.value = current
        saveSearchHistoryToPrefs()
    }

    fun removeSearchQuery(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        _searchHistory.value = current
        saveSearchHistoryToPrefs()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistoryToPrefs()
    }

    fun toggleFavorite(appId: String) {
        val current = _favorites.value.toMutableSet()
        val app = _apps.value.find { it.id == appId }
        val appName = app?.nameAr ?: app?.name ?: "التطبيق"
        if (current.contains(appId)) {
            current.remove(appId)
            emitToast("تمت إزالة $appName من المفضلة")
        } else {
            current.add(appId)
            emitToast("تمت إضافة $appName إلى المفضلة ❤️")
        }
        _favorites.value = current
        saveFavoritesToPrefs()
    }

    fun isFavorite(appId: String): Boolean = _favorites.value.contains(appId)

    fun startDownload(appId: String) {
        val app = _apps.value.find { it.id == appId } ?: return
        val currentList = _downloads.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.appId == appId }

        if (existingIndex >= 0 && currentList[existingIndex].isCompleted) {
            emitToast("${app.nameAr} مثبت بالفعل")
            return
        }

        if (activeDownloadJobs.containsKey(appId)) {
            emitToast("جاري تحميل ${app.nameAr} حالياً")
            return
        }

        val item = if (existingIndex >= 0) {
            currentList[existingIndex].copy(isPaused = false)
        } else {
            DownloadItem(
                appId = app.id,
                appName = app.name,
                appIcon = app.iconEmoji,
                accentColor = app.accentColor,
                progress = 0.05f,
                speed = "5.8 MB/s",
                downloadedSize = "6.0 MB",
                totalSize = app.size,
                isCompleted = false,
                isPaused = false
            )
        }

        if (existingIndex >= 0) {
            currentList[existingIndex] = item
        } else {
            currentList.add(0, item)
        }
        _downloads.value = currentList

        emitToast("بدأ تحميل ${app.nameAr}")

        val job = scope.launch {
            var currentProgress = item.progress
            val totalBytes = app.sizeBytes.coerceAtLeast(50_000_000L)

            while (currentProgress < 1.0f) {
                delay(400)
                val checkItem = _downloads.value.find { it.appId == appId }
                if (checkItem == null || checkItem.isPaused) {
                    break
                }

                val speedMbps = Random.nextDouble(4.2, 7.8)
                val speedText = String.format("%.1f MB/s", speedMbps)
                val increment = (speedMbps * 0.4 * 1024 * 1024) / totalBytes
                currentProgress = (currentProgress + increment.toFloat()).coerceAtMost(1.0f)

                val downloadedBytes = (currentProgress * totalBytes).toLong()
                val downloadedText = formatBytes(downloadedBytes)

                val updatedList = _downloads.value.map { d ->
                    if (d.appId == appId) {
                        d.copy(
                            progress = currentProgress,
                            speed = speedText,
                            downloadedSize = downloadedText,
                            isCompleted = currentProgress >= 1.0f
                        )
                    } else d
                }
                _downloads.value = updatedList

                if (currentProgress >= 1.0f) {
                    activeDownloadJobs.remove(appId)
                    emitToast("تم تحميل وتثبيت ${app.nameAr} بنجاح! ✔")

                    scope.launch {
                        com.example.data.supabase.SupabaseClient.incrementDownloads(appId)
                    }

                    if (!app.fileUrl.isNullOrBlank()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.fileUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignored if invalid intent
                        }
                    }
                    break
                }
            }
        }
        activeDownloadJobs[appId] = job
    }

    fun pauseDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        _downloads.value = _downloads.value.map {
            if (it.appId == appId) it.copy(isPaused = true, speed = "متوقف") else it
        }
        emitToast("تم إيقاف التحميل مؤقتاً")
    }

    fun resumeDownload(appId: String) {
        startDownload(appId)
    }

    fun cancelDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        _downloads.value = _downloads.value.filterNot { it.appId == appId }
        emitToast("تم إلغاء التحميل")
    }

    fun clearAllDownloads() {
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        _downloads.value = emptyList()
        emitToast("تم مسح جميع التحميلات")
    }

    fun addReview(appId: String, rating: Int, comment: String, author: String = "أنت") {
        val newReview = ReviewItem(
            id = "rev_${System.currentTimeMillis()}",
            userName = author,
            userAvatar = "👑",
            rating = rating,
            date = "الآن",
            comment = comment
        )
        val current = _reviews.value.toMutableMap()
        val list = (current[appId] ?: emptyList()).toMutableList()
        list.add(0, newReview)
        current[appId] = list
        _reviews.value = current
        emitToast("شكراً! تم نشر تقييمك بنجاح ⭐")
    }

    fun emitToast(msg: String) {
        scope.launch {
            _toastEvents.emit(msg)
        }
    }

    fun addApp(app: AppItem, accessToken: String? = null) {
        val current = _apps.value.toMutableList()
        current.add(0, app)
        _apps.value = current
        emitToast("تمت إضافة تطبيق ${app.nameAr} بنجاح إلى المتجر! ✔")

        scope.launch {
            val categoryKey = when (app.category) {
                AppCategory.GAMES -> "games"
                AppCategory.APPS -> "apps"
                AppCategory.POPULAR -> "trending"
                AppCategory.ENTERTAINMENT -> "entertainment"
                AppCategory.SPORTS -> "sports"
                AppCategory.BUSINESS -> "business"
                AppCategory.EDUCATION -> "education"
                AppCategory.MUSIC -> "music"
                AppCategory.VIDEO -> "video"
                AppCategory.TOOLS -> "tools"
                else -> "apps"
            }
            com.example.data.supabase.SupabaseClient.insertApp(
                accessToken = accessToken,
                name = app.nameAr,
                category = categoryKey,
                rating = app.rating,
                size = app.size,
                description = app.descriptionAr,
                features = app.featuresAr,
                iconUrl = app.iconUrl ?: app.iconEmoji,
                fileUrl = app.fileUrl.orEmpty(),
                screenshots = app.screenshotUrls
            )
            refreshFromSupabase()
        }
    }

    fun updateApp(app: AppItem, accessToken: String? = null) {
        val current = _apps.value.toMutableList()
        val index = current.indexOfFirst { it.id == app.id }
        if (index >= 0) {
            current[index] = app
            _apps.value = current
            emitToast("تم تحديث بيانات ${app.nameAr} بنجاح! ✔")
        }
        scope.launch {
            val categoryKey = when (app.category) {
                AppCategory.GAMES -> "games"
                AppCategory.APPS -> "apps"
                AppCategory.POPULAR -> "trending"
                AppCategory.ENTERTAINMENT -> "entertainment"
                AppCategory.SPORTS -> "sports"
                AppCategory.BUSINESS -> "business"
                AppCategory.EDUCATION -> "education"
                AppCategory.MUSIC -> "music"
                AppCategory.VIDEO -> "video"
                AppCategory.TOOLS -> "tools"
                else -> "apps"
            }
            com.example.data.supabase.SupabaseClient.updateApp(
                accessToken = accessToken,
                id = app.id,
                name = app.nameAr,
                category = categoryKey,
                rating = app.rating,
                size = app.size,
                description = app.descriptionAr,
                features = app.featuresAr,
                iconUrl = app.iconUrl ?: app.iconEmoji,
                fileUrl = app.fileUrl.orEmpty(),
                screenshots = app.screenshotUrls
            )
            refreshFromSupabase()
        }
    }

    fun deleteApp(appId: String, accessToken: String? = null) {
        val current = _apps.value.toMutableList()
        val app = current.find { it.id == appId }
        val name = app?.nameAr ?: app?.name ?: "التطبيق"
        current.removeAll { it.id == appId }
        _apps.value = current
        emitToast("تم حذف تطبيق $name من المتجر 🗑️")

        scope.launch {
            com.example.data.supabase.SupabaseClient.deleteApp(accessToken, appId)
            refreshFromSupabase()
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%d KB", bytes / 1024)
        }
    }

    private fun initDefaultDownloads() {
        // Matches the screenshot: Clash of Clans downloading at 72%, Subway Surfers completed, WhatsApp completed
        _downloads.value = listOf(
            DownloadItem(
                appId = "clash_of_clans",
                appName = "Clash of Clans",
                appIcon = "🏰",
                accentColor = 0xFFF59E0B,
                progress = 0.72f,
                speed = "5.8 MB/s",
                downloadedSize = "120 MB",
                totalSize = "280 MB",
                isCompleted = false,
                isPaused = false
            ),
            DownloadItem(
                appId = "subway_surfers",
                appName = "Subway Surfers",
                appIcon = "🏃",
                accentColor = 0xFF06B6D4,
                progress = 1.0f,
                speed = "0 KB/s",
                downloadedSize = "150 MB",
                totalSize = "150 MB",
                isCompleted = true,
                isPaused = false
            ),
            DownloadItem(
                appId = "whatsapp",
                appName = "WhatsApp",
                appIcon = "💬",
                accentColor = 0xFF22C55E,
                progress = 1.0f,
                speed = "0 KB/s",
                downloadedSize = "85 MB",
                totalSize = "85 MB",
                isCompleted = true,
                isPaused = false
            )
        )
    }

    private fun initDefaultReviews() {
        _reviews.value = mapOf(
            "minecraft" to listOf(
                ReviewItem("r1", "أحمد الشمري", "🎮", 5, "قبل يومين", "أفضل لعبة بناء وبقاء في التاريخ! التحديث الأخير رهيب والسيرفرات سريعة جداً."),
                ReviewItem("r2", "سارة خالد", "⭐", 5, "قبل 4 أيام", "رسوميات كلاسيكية ممتعة وإبداع لا حدود له مع الأصدقاء."),
                ReviewItem("r3", "محمد العمري", "👑", 4, "قبل أسبوع", "اللعبة ممتازة وتستحق 5 نجوم لكن أتمنى إضافة تحسينات لطور النجاة.")
            ),
            "gta_v" to listOf(
                ReviewItem("g1", "عمر الفاروق", "🏎️", 5, "قبل 3 أيام", "جرافيكس أسطوري ومهمات حماسية لأقصى درجة، عالم لوس سانتوس واسع جداً!"),
                ReviewItem("g2", "فيصل العتيبي", "🔥", 5, "قبل أسبوع", "تحكم سلس وتجربة سينمائية لا توصف، أفضل لعبة أكشن ومطاردات.")
            )
        )
    }

    private fun loadCatalog() {
        val list = mutableListOf<AppItem>()

        // 1. Grand Theft Auto V (Hero Banner Game)
        list.add(
            AppItem(
                id = "gta_v",
                name = "Grand Theft Auto V",
                nameAr = "Grand Theft Auto V",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "أكشن", "عالم مفتوح", "مغامرات"),
                rating = 4.5f,
                ratingCount = "3.8M",
                downloadCount = "+50M",
                size = "2.1 GB",
                sizeBytes = 2_254_857_830L,
                iconEmoji = "🏎️",
                accentColor = 0xFF8B5CF6,
                bannerRes = R.drawable.banner_gta5,
                descriptionAr = "ادخل إلى عالم لوس سانتوس المثير في مغامرة أكشن لا تنتهي مع مايكل وفرانكلين وتريفور. رسوميات سينمائية ومهمات سرقة وتحديات أونلاين ملحمية.",
                descriptionEn = "Explore Los Santos in the iconic open-world action adventure with high-stakes heists and thrilling criminal gameplay.",
                featuresAr = listOf(
                    "عالم لوس سانتوس المفتوح والضخم",
                    "قصة درامية متداخلة لثلاث شخصيات رئيسية",
                    "أسلحة ومركبات وطائرات قابلة للتخصيص الكامل",
                    "دعم اللعب الجماعي مع ملايين اللاعبين حول العالم"
                ),
                screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95, 0xFF5B21B6, 0xFF6D28D9),
                isFeatured = true,
                isTrending = true
            )
        )

        // 2. Minecraft (Featured Detail App from Screenshot)
        list.add(
            AppItem(
                id = "minecraft",
                name = "Minecraft",
                nameAr = "Minecraft",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "مغامرات", "بناء"),
                rating = 4.6f,
                ratingCount = "2.4M",
                downloadCount = "+100M",
                size = "180 MB",
                sizeBytes = 188_743_680L,
                iconEmoji = "🧱",
                accentColor = 0xFF16A34A,
                descriptionAr = "استكشف عالماً لا نهائياً، وابنِ، والعب مع أصدقائك في لعبة Minecraft الأسطورية. اصنع عوالمك الخاصة من أبسط المنازل وحتى أفخم القلاع.",
                descriptionEn = "Explore infinite worlds and build everything from the simplest of homes to the grandest of castles.",
                featuresAr = listOf(
                    "عالم مفتوح ضخم لا نهائي",
                    "طور البقاء والإبداع بلا قيود",
                    "لعب متعدد اللاعبين عبر الشبكة والسيرفرات",
                    "دعم التعديلات (Mods) وعوالم مخصصة لا حصر لها"
                ),
                screenshotsColors = listOf(0xFF15803D, 0xFF166534, 0xFF14532D, 0xFF047857),
                isFeatured = true,
                isTrending = true
            )
        )

        // 3. Roblox
        list.add(
            AppItem(
                id = "roblox",
                name = "Roblox",
                nameAr = "Roblox",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "مغامرات", "عوالم افتراضية"),
                rating = 4.3f,
                ratingCount = "18.2M",
                downloadCount = "+500M",
                size = "165 MB",
                sizeBytes = 173_015_040L,
                iconEmoji = "🎲",
                accentColor = 0xFFDC2626,
                descriptionAr = "روبلوكس هو العالم الافتراضي الأمثل الذي يتيح لك اللعب والإنشاء وأن تكون أي شيء تتخيله. انضم إلى ملايين المبتكرين واكتشف تنوعاً لا نهائياً من التجارب العالمية.",
                descriptionEn = "Roblox is the ultimate virtual universe that lets you create, share experiences with friends, and be anything you can imagine.",
                featuresAr = listOf(
                    "ملايين العوالم والتجارب المجانية المختلفة",
                    "تخصيص كامل للأفاتار مع ملابس وإكسسوارات مميزة",
                    "تواصل فوري ودردشة صوتية مع أصدقائك",
                    "إمكانية تصميم ألعابك الخاصة بسهولة"
                ),
                screenshotsColors = listOf(0xFF991B1B, 0xFFB91C1C, 0xFF7F1D1D, 0xFF450A0A),
                isFeatured = true,
                isTrending = true
            )
        )

        // 4. Stumble Guys
        list.add(
            AppItem(
                id = "stumble_guys",
                name = "Stumble Guys",
                nameAr = "Stumble Guys",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "حفلات", "باتل رويال", "مرح"),
                rating = 4.4f,
                ratingCount = "5.6M",
                downloadCount = "+100M",
                size = "140 MB",
                sizeBytes = 146_800_640L,
                iconEmoji = "🏃‍♂️",
                accentColor = 0xFFF59E0B,
                descriptionAr = "لعبة خروج المغلوب الجماعية الممتعة التي تضم ما يصل إلى 32 لاعباً عبر الإنترنت. اركض، واقفز، وتفادَ العوائق للوصول لخط النهاية أولاً!",
                descriptionEn = "Massive multiplayer party knockout game with up to 32 players online struggling through obstacle courses.",
                featuresAr = listOf(
                    "مباريات سريعة ومثيرة مع 32 لاعباً أونلاين",
                    "عشرات الحلبات المليئة بالفخاخ والمفاجآت",
                    "شخصيات وإيموتات مضحكة ومتنوعة",
                    "لعب متواصل خفيف ومناسب لجميع الأعمار"
                ),
                screenshotsColors = listOf(0xFFD97706, 0xFFB45309, 0xFF92400E, 0xFF78350F),
                isFeatured = true,
                isNew = false
            )
        )

        // 5. Clash of Clans
        list.add(
            AppItem(
                id = "clash_of_clans",
                name = "Clash of Clans",
                nameAr = "Clash of Clans",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "استراتيجية", "حروب قبائل"),
                rating = 4.5f,
                ratingCount = "59M",
                downloadCount = "+500M",
                size = "280 MB",
                sizeBytes = 293_601_280L,
                iconEmoji = "🏰",
                accentColor = 0xFFF59E0B,
                descriptionAr = "ابنِ قريتك، وشكّل قبيلتك، ونافس في حروب القبائل الملحمية! درّب جيوشاً من البرابرة والسحرة والتنانين لتغزو أراضي الأعداء وتدافع عن حصنك.",
                descriptionEn = "Join millions of players worldwide as you build your village, raise a clan, and compete in epic Clan Wars!",
                featuresAr = listOf(
                    "بناء وتطوير دفاعات القرية وحصونها",
                    "حروب قبائل تكتيكية ضد تحالفات حول العالم",
                    "أبطال أسطوريون بقدرات خارقة وفريدة",
                    "تحديثات مستمرة ومواسم مليئة بالمكافآت"
                ),
                screenshotsColors = listOf(0xFFB45309, 0xFF92400E, 0xFF78350F, 0xFF451A03),
                isFeatured = true,
                isTrending = true
            )
        )

        // 6. Subway Surfers
        list.add(
            AppItem(
                id = "subway_surfers",
                name = "Subway Surfers",
                nameAr = "Subway Surfers",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "ركض لا نهائي", "أركيد"),
                rating = 4.5f,
                ratingCount = "42M",
                downloadCount = "+1B",
                size = "150 MB",
                sizeBytes = 157_286_400L,
                iconEmoji = "🛹",
                accentColor = 0xFF0284C7,
                descriptionAr = "اركض بأسرع ما يمكنك على قضبان القطارات وتفادَ المفتش الغاضب وكلبه! لوحات تزلج سريعة، مغناطيس عملات، وجولات عالمية في مدن رائعة.",
                descriptionEn = "Dash as fast as you can through subway tracks dodging obstacles in this world-famous endless runner.",
                featuresAr = listOf(
                    "طيران بحقائب الطائرات النفاثة الملونة",
                    "بهلوانيات تزلج فائقة السرعة بلمسة واحدة",
                    "جولة حول مدن العالم مع كل تحديث جديد",
                    "تحديات يومية ولوحات متصدرين عالمية"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49),
                isFeatured = true
            )
        )

        // 7. Free Fire MAX
        list.add(
            AppItem(
                id = "free_fire",
                name = "Free Fire MAX",
                nameAr = "Free Fire MAX",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "أكشن", "باتل رويال", "إطلاق نار"),
                rating = 4.4f,
                ratingCount = "15M",
                downloadCount = "+100M",
                size = "620 MB",
                sizeBytes = 650_117_120L,
                iconEmoji = "🔥",
                accentColor = 0xFFEA580C,
                descriptionAr = "تجربة باتل رويال فائقة الحماس بدقة Ultra HD وتأثيرات بصرية استثنائية. اهبط بالمظلة في جزيرة مهجورة مع 50 لاعباً وكن الناجي الأخير.",
                descriptionEn = "Free Fire MAX brings high-intensity battle royale thrills with enhanced graphics and thrilling 10-minute survivals.",
                featuresAr = listOf(
                    "مباريات بقاء حماسية مدتها 10 دقائق فقط",
                    "جرافيكس فائق الدقة مع مؤثرات واقعية متطورة",
                    "أوضاع لعب متعددة تشمل Clash Squad والفرق",
                    "شخصيات ذات مهارات قتالية تكتيكية فريدة"
                ),
                screenshotsColors = listOf(0xFFC2410C, 0xFF9A3412, 0xFF7C2D12, 0xFF431407),
                isFeatured = true,
                isTrending = true
            )
        )

        // 8. PUBG Mobile
        list.add(
            AppItem(
                id = "pubg_mobile",
                name = "PUBG Mobile",
                nameAr = "PUBG Mobile",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "أكشن", "باتل رويال"),
                rating = 4.3f,
                ratingCount = "45M",
                downloadCount = "+1B",
                size = "1.2 GB",
                sizeBytes = 1_288_490_188L,
                iconEmoji = "🪖",
                accentColor = 0xFFF59E0B,
                descriptionAr = "لعبة الباتل رويال الأصلية الأكثر شهرة في العالم! خرائط متنوعة، أسلحة عسكرية واقعية، ودردشة صوتية واضحة لتكتيكات الفريق المحترفة.",
                descriptionEn = "The original battle royale phenomenon on mobile. 100 players, massive maps, and realistic gunplay.",
                featuresAr = listOf(
                    "خرائط عملاقة كلاسيكية تشمل إرانغل وميرامار",
                    "محرك Unreal Engine 4 لفيزياء ورسوم واقعية",
                    "مركبات برية وبحرية متنوعة للمطاردات التكتيكية",
                    "مواسم دورية وجوائز رويال باس حصرية"
                ),
                screenshotsColors = listOf(0xFF78350F, 0xFF92400E, 0xFFB45309, 0xFFD97706),
                isFeatured = true,
                isTrending = true
            )
        )

        // 9. WhatsApp Messenger
        list.add(
            AppItem(
                id = "whatsapp",
                name = "WhatsApp",
                nameAr = "واتساب",
                category = AppCategory.APPS,
                tags = listOf("تطبيقات", "تواصل", "رسائل", "مكالمات"),
                rating = 4.3f,
                ratingCount = "190M",
                downloadCount = "+5B",
                size = "85 MB",
                sizeBytes = 89_128_960L,
                iconEmoji = "💬",
                accentColor = 0xFF22C55E,
                descriptionAr = "تطبيق المراسلة والمكالمات الأكثر أماناً واستخداماً في العالم. تواصل فوري ومكالمات صوت وصورة عالية الدقة ومشفرة تماماً بين الطرفين.",
                descriptionEn = "Simple, reliable, and private messaging and calling around the world, completely free.",
                featuresAr = listOf(
                    "تشفير تام بين الطرفين لجميع الرسائل والمكالمات",
                    "مكالمات صوت وفيديو جماعية بجودة فائقة",
                    "مشاركة المستندات والصور والفيديوهات الكبيرة",
                    "حالات يومية وقنوات إخبارية تفاعلية"
                ),
                screenshotsColors = listOf(0xFF15803D, 0xFF166534, 0xFF14532D, 0xFF052E16),
                isTrending = true
            )
        )

        // 10. Telegram
        list.add(
            AppItem(
                id = "telegram",
                name = "Telegram",
                nameAr = "تيليجرام",
                category = AppCategory.APPS,
                tags = listOf("تطبيقات", "تواصل", "سحابي", "أمان"),
                rating = 4.4f,
                ratingCount = "14M",
                downloadCount = "+1B",
                size = "65 MB",
                sizeBytes = 68_157_440L,
                iconEmoji = "✈️",
                accentColor = 0xFF0284C7,
                descriptionAr = "مراسلة سريعة، قوية، وسحابية بدون حدود للملفات. قنوات، بوتات برمجية ذكية، ومجموعات تتسع لـ 200 ألف عضو.",
                descriptionEn = "Pure instant messaging — simple, fast, secure, and synced across all your devices.",
                featuresAr = listOf(
                    "مزامنة سحابية فورية ومساحة تخزين غير محدودة",
                    "إرسال ملفات ضخمة بحجم يصل إلى 2 جيجابايت",
                    "قنوات عامة وبوتات ذكية متطورة",
                    "أمان ومحادثات سرية مع ميزة التدمير الذاتي"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49)
            )
        )

        // 11. TikTok
        list.add(
            AppItem(
                id = "tiktok",
                name = "TikTok",
                nameAr = "تيك توك",
                category = AppCategory.ENTERTAINMENT,
                tags = listOf("ترفيه", "فيديو", "موسيقى", "تواصل"),
                rating = 4.4f,
                ratingCount = "60M",
                downloadCount = "+1B",
                size = "110 MB",
                sizeBytes = 115_343_360L,
                iconEmoji = "🎵",
                accentColor = 0xFF000000,
                descriptionAr = "منصة الفيديوهات القصيرة الأولى في العالم. استكشف محتوى ترفيهي مبدعاً، وتعلم مهارات جديدة، واصنع مقاطعك بمؤثرات وفلاتر مذهلة.",
                descriptionEn = "The leading destination for short-form mobile video with endless trends, comedy, and music.",
                featuresAr = listOf(
                    "خلاصة فيديوهات مخصصة لك بالذكاء الاصطناعي",
                    "مكتبة ملايين الأغاني والمقاطع الصوتية المجانية",
                    "أدوات مونتاج وفلاتر واقع معزز احترافية",
                    "بث مباشر ومكافآت وتفاعل حي مع المبدعين"
                ),
                screenshotsColors = listOf(0xFF1E1B4B, 0xFF312E81, 0xFF3730A3, 0xFF4338CA),
                isTrending = true
            )
        )

        // 12. YouTube
        list.add(
            AppItem(
                id = "youtube",
                name = "YouTube",
                nameAr = "يوتيوب",
                category = AppCategory.VIDEO,
                tags = listOf("فيديو", "ترفيه", "بث مباشر"),
                rating = 4.2f,
                ratingCount = "150M",
                downloadCount = "+10B",
                size = "75 MB",
                sizeBytes = 78_643_200L,
                iconEmoji = "▶️",
                accentColor = 0xFFDC2626,
                descriptionAr = "شاهد ما يشاهده العالم من أحدث الفيديوهات الموسيقية والتعليمية والألعاب والبرامج الوثائقية والقصص القصيرة Shorts.",
                descriptionEn = "Get the official YouTube app on Android phones and tablets to explore trending videos.",
                featuresAr = listOf(
                    "مقاطع فيديو Shorts سريعة وجذابة",
                    "اشتراكات قنوات وتنبيهات فورية لكل جديد",
                    "تشغيل بجودة 4K مع ترجمات متعددة اللغات",
                    "تنزيل لمشاهدة المحتوى أوفلاين بلا إنترنت"
                ),
                screenshotsColors = listOf(0xFF991B1B, 0xFFB91C1C, 0xFF7F1D1D, 0xFF450A0A)
            )
        )

        // 13. Spotify
        list.add(
            AppItem(
                id = "spotify",
                name = "Spotify: Music and Podcasts",
                nameAr = "سبوتيفاي",
                category = AppCategory.MUSIC,
                tags = listOf("موسيقى", "بودكاست", "صوتيات"),
                rating = 4.4f,
                ratingCount = "32M",
                downloadCount = "+1B",
                size = "45 MB",
                sizeBytes = 47_185_920L,
                iconEmoji = "🎧",
                accentColor = 0xFF10B981,
                descriptionAr = "استمع إلى ملايين الأغاني وقوائم التشغيل والبودكاست من كل أنحاء العالم بجودة صوت نقية واقتراحات موسيقية ذكية.",
                descriptionEn = "With Spotify, you have access to a world of free music, curated playlists, and podcasts.",
                featuresAr = listOf(
                    "اكتشاف أسبوعي مخصص لذوقك الموسيقي",
                    "قوائم تشغيل جاهزة لجميع الحالات المزاجية والرياضة",
                    "برامج بودكاست حصرية مع أشهر الإعلاميين",
                    "كلمات الأغاني المزامنة مع اللحن بالوقت الحقيقي"
                ),
                screenshotsColors = listOf(0xFF047857, 0xFF065F46, 0xFF064E3B, 0xFF022C22)
            )
        )

        // 14. Netflix
        list.add(
            AppItem(
                id = "netflix",
                name = "Netflix",
                nameAr = "نتفليكس",
                category = AppCategory.ENTERTAINMENT,
                tags = listOf("ترفيه", "أفلام", "مسلسلات"),
                rating = 4.3f,
                ratingCount = "15M",
                downloadCount = "+1B",
                size = "50 MB",
                sizeBytes = 52_428_800L,
                iconEmoji = "🎬",
                accentColor = 0xFFE11D48,
                descriptionAr = "الوجهة الترفيهية لمشاهدة الأفلام والمسلسلات الحائزة على جوائز، وبرامج الأطفال والألعاب الحصرية على هاتفك أينما كنت.",
                descriptionEn = "Looking for the most talked about TV shows and movies from around the world? They’re all on Netflix.",
                featuresAr = listOf(
                    "إنتاجات نتفليكس الأصلية الحصرية بتقنية HDR",
                    "ملفات شخصية مخصصة للأطفال وآمنة تماماً",
                    "تحميل الحلقات لمشاهدتها في السفر بلا إنترنت",
                    "ألعاب فيديو مدمجة مجانية بدون إعلانات"
                ),
                screenshotsColors = listOf(0xFFBE123C, 0xFF9F1239, 0xFF881337, 0xFF4C0519)
            )
        )

        // 15. CapCut
        list.add(
            AppItem(
                id = "capcut",
                name = "CapCut - Video Editor",
                nameAr = "كاب كت",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "مونتاج", "فيديو", "تصميم"),
                rating = 4.5f,
                ratingCount = "12M",
                downloadCount = "+500M",
                size = "130 MB",
                sizeBytes = 136_314_880L,
                iconEmoji = "✂️",
                accentColor = 0xFF0284C7,
                descriptionAr = "محرر الفيديو الشامل والمجاني الأسهل استخداماً. قوالب ترند جاهزة، نصوص متحركة، فلاتر سينمائية، وتفريغ خلفيات بالذكاء الاصطناعي.",
                descriptionEn = "CapCut is an easy-to-use video editing app with trending templates and smart effects.",
                featuresAr = listOf(
                    "إزالة الخلفية الذكية بنقرة واحدة (AI Chroma)",
                    "توليد تلقائي للترجمة والكتابة على الفيديو (Auto Captions)",
                    "مؤثرات انتقالية وانسيابية الحركة المتقدمة",
                    "تصدير بدقة 4K بمعدل 60 إطاراً في الثانية"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49)
            )
        )

        // 16. Duolingo
        list.add(
            AppItem(
                id = "duolingo",
                name = "Duolingo: Language Lessons",
                nameAr = "دوليقو",
                category = AppCategory.EDUCATION,
                tags = listOf("تعليم", "لغات", "تطوير"),
                rating = 4.7f,
                ratingCount = "20M",
                downloadCount = "+500M",
                size = "60 MB",
                sizeBytes = 62_914_560L,
                iconEmoji = "🦉",
                accentColor = 0xFF16A34A,
                descriptionAr = "تعلم أكثر من 40 لغة عبر دروس يومية ممتعة وجذابة تشبه الألعاب. مارس الإنجليزية والفرنسية والإسبانية والألمانية بطريقة تفاعلية مثبتة علمياً.",
                descriptionEn = "Learn a new language with the world's most downloaded education app with quick, bite-sized lessons.",
                featuresAr = listOf(
                    "دروس تفاعلية قصيرة تناسب وقتك اليومي",
                    "نظام جوائز وحماس يحافظ على التزامك",
                    "تدريبات استماع ونطق ومحادثة عملية",
                    "شخصيات مرحة ترشدك خطوة بخطوة"
                ),
                screenshotsColors = listOf(0xFF15803D, 0xFF166534, 0xFF14532D, 0xFF052E16)
            )
        )

        // 17. Picsart AI Photo Editor
        list.add(
            AppItem(
                id = "picsart",
                name = "Picsart AI Photo Editor",
                nameAr = "بيكس آرت",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "تصميم", "صور", "ذكاء اصطناعي"),
                rating = 4.4f,
                ratingCount = "13M",
                downloadCount = "+1B",
                size = "95 MB",
                sizeBytes = 99_614_720L,
                iconEmoji = "🎨",
                accentColor = 0xFFA855F7,
                descriptionAr = "استوديو التصميم المتكامل ومحرر الصور والفيديوهات بالذكاء الاصطناعي. أنشئ ملصقات احترافية وفلاتر ساحرة وتصميمات لمنصات التواصل.",
                descriptionEn = "Join the creator community with all-in-one AI powered photo and video editing tools.",
                featuresAr = listOf(
                    "توليد صور وتعديلها بنصوص الذكاء الاصطناعي",
                    "ممحاة سحرية لحذف العناصر غير المرغوبة من الصور",
                    "آلاف الخطوط والملصقات والخطوط الاحترافية",
                    "قوالب جاهزة لمنشورات وقصص إنستغرام"
                ),
                screenshotsColors = listOf(0xFF7E22CE, 0xFF6B21A8, 0xFF581C87, 0xFF3B0764)
            )
        )

        // 18. EA SPORTS FC Mobile Soccer
        list.add(
            AppItem(
                id = "fc_mobile",
                name = "EA SPORTS FC Mobile",
                nameAr = "FC Mobile (فيفا)",
                category = AppCategory.SPORTS,
                tags = listOf("رياضة", "كرة قدم", "ألعاب"),
                rating = 4.2f,
                ratingCount = "16M",
                downloadCount = "+100M",
                size = "180 MB",
                sizeBytes = 188_743_680L,
                iconEmoji = "⚽",
                accentColor = 0xFF16A34A,
                descriptionAr = "ابنِ فريق أحلامك Ultimate Team™ من نجوم الكرة وأساطير دوري أبطال أوروبا والبريميرليغ والدوري الإسباني مع واقعية فيزيائية غير مسبوقة.",
                descriptionEn = "Build an Ultimate Team™ of your favorite football legends and test your skills in real-time PvP.",
                featuresAr = listOf(
                    "أكثر من 15 ألف لاعب حقيقي مرخص رسمياً",
                    "نظام تسديد وتمرير واقعي وتاكتيكي",
                    "بطولات دوري أبطال أوروبا الحصرية UEFA",
                    "مباريات لاعب ضد لاعب وجه لوجه 11 ضد 11"
                ),
                screenshotsColors = listOf(0xFF15803D, 0xFF166534, 0xFF14532D, 0xFF052E16)
            )
        )

        // 19. Brawl Stars
        list.add(
            AppItem(
                id = "brawl_stars",
                name = "Brawl Stars",
                nameAr = "براول ستارز",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "أكشن", "متعدد اللاعبين"),
                rating = 4.5f,
                ratingCount = "24M",
                downloadCount = "+500M",
                size = "450 MB",
                sizeBytes = 471_859_200L,
                iconEmoji = "⭐",
                accentColor = 0xFFF59E0B,
                descriptionAr = "معارك 3 ضد 3 سريعة الإيقاع وطور بقاء جماعي مصمم للأجهزة المحمولة! افتح واجمع العشرات من المقاتلين بقدرات خارقة وفريدة.",
                descriptionEn = "Fast-paced 3v3 multiplayer and battle royale made for mobile! Play with friends across various game modes.",
                featuresAr = listOf(
                    "مباريات تكتيكية مدتها أقل من 3 دقائق",
                    "عشرات الأبطال (Brawlers) بمظاهر رائعة",
                    "خرائط متجددة من تصميم مجتمع اللاعبين",
                    "أحداث وتحديات بطولة شهرية بمكافآت قيّمة"
                ),
                screenshotsColors = listOf(0xFFB45309, 0xFF92400E, 0xFF78350F, 0xFF451A03),
                isNew = true
            )
        )

        // 20. Asphalt 9: Legends
        list.add(
            AppItem(
                id = "asphalt_9",
                name = "Asphalt 9: Legends",
                nameAr = "أسفلت 9",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "سباقات", "سيارات"),
                rating = 4.4f,
                ratingCount = "3.2M",
                downloadCount = "+50M",
                size = "2.4 GB",
                sizeBytes = 2_576_980_377L,
                iconEmoji = "🏎️",
                accentColor = 0xFFE11D48,
                descriptionAr = "قُد أشهر السيارات الخارقة من فيراري وبورشه ولامبورغيني على حلبات مذهلة عبر العالم! جرافيكس كونسول حقيقي وتأثيرات نيترو خارقة.",
                descriptionEn = "Tear up the asphalt in hypercars from Ferrari, Porsche, and Lamborghini in console-quality racing.",
                featuresAr = listOf(
                    "سيارات خارقة مرخصة من أشهر المصانع العالمية",
                    "تحكم TouchDrive المبتكر للتركيز على السرعة والمناورات",
                    "حلبات سباق في مواقع حقيقية حول العالم",
                    "تحديات متعددة اللاعبين حتى 8 متسابقين أونلاين"
                ),
                screenshotsColors = listOf(0xFFBE123C, 0xFF9F1239, 0xFF881337, 0xFF4C0519)
            )
        )

        // 21. Call of Duty: Mobile
        list.add(
            AppItem(
                id = "cod_mobile",
                name = "Call of Duty: Mobile",
                nameAr = "كول أوف ديوتي",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "إطلاق نار", "أكشن"),
                rating = 4.4f,
                ratingCount = "17M",
                downloadCount = "+500M",
                size = "2.0 GB",
                sizeBytes = 2_147_483_648L,
                iconEmoji = "🎯",
                accentColor = 0xFF4B5563,
                descriptionAr = "تجربة كول أوف ديوتي المحبوبة على هاتفك بأوضاع لعب متعددة تشمل النزاع بين الفرق، وطور الزومبي، والباتل رويال لـ 100 لاعب.",
                descriptionEn = "Iconic Call of Duty first-person action on the go with Multiplayer and Battle Royale modes.",
                featuresAr = listOf(
                    "خرائط أسطورية مثل Crash و Nuketown",
                    "تخصيص تفصيلي لكل قطعة في السلاح (Gunsmith)",
                    "جرافيكس ثلاثي الأبعاد وصوت محيطي نقي",
                    "مواسم دورية حافلة بالخرائط والمشغلين الجدد"
                ),
                screenshotsColors = listOf(0xFF374151, 0xFF1F2937, 0xFF111827, 0xFF030712)
            )
        )

        // 22. Instagram
        list.add(
            AppItem(
                id = "instagram",
                name = "Instagram",
                nameAr = "إنستغرام",
                category = AppCategory.APPS,
                tags = listOf("تطبيقات", "صور", "فيديو", "تواصل"),
                rating = 4.1f,
                ratingCount = "150M",
                downloadCount = "+5B",
                size = "60 MB",
                sizeBytes = 62_914_560L,
                iconEmoji = "📸",
                accentColor = 0xFFE1306C,
                descriptionAr = "شارك لحظاتك اليومية عبر الصور والقصص ومقاطع Reels، وابقَ على اتصال دائم مع أصدقائك وصناع المحتوى المفضلين لديك.",
                descriptionEn = "Bringing you closer to the people and things you love through photos, Stories, and Reels.",
                featuresAr = listOf(
                    "مقاطع Reels الترفيهية القصيرة",
                    "قصص تختفي بعد 24 ساعة مع فلاتر وملصقات تفاعلية",
                    "مراسلة خاصة Direct مع مشاركة المنشورات والمكالمات",
                    "استكشاف إبداعي للموضة والفن والطبخ"
                ),
                screenshotsColors = listOf(0xFF833AB4, 0xFFFD1D1D, 0xFFFCB045, 0xFF405DE6)
            )
        )

        // 23. ChatGPT
        list.add(
            AppItem(
                id = "chatgpt",
                name = "ChatGPT",
                nameAr = "شات جي بي تي",
                category = AppCategory.BUSINESS,
                tags = listOf("أعمال", "إنتاجية", "ذكاء اصطناعي"),
                rating = 4.7f,
                ratingCount = "6M",
                downloadCount = "+100M",
                size = "35 MB",
                sizeBytes = 36_700_160L,
                iconEmoji = "🤖",
                accentColor = 0xFF10A37F,
                descriptionAr = "المساعد الذكي الرسمي من OpenAI على جهازك. احصل على إجابات فورية، واكتب رسائل احترافية، ولخص المستندات، واستشر في البرمجة والتعلم.",
                descriptionEn = "The official ChatGPT app syncs your history across devices and brings the newest AI capabilities.",
                featuresAr = listOf(
                    "محادثات ذكية فورية ودقيقة",
                    "ميزة المحادثة الصوتية المباشرة الطبيعية",
                    "تحليل الصور والوثائق واستخراج المعلومات",
                    "مزامنة كاملة لتاريخ المحادثات عبر جميع أجهزتك"
                ),
                screenshotsColors = listOf(0xFF0D9488, 0xFF0F766E, 0xFF115E59, 0xFF134E4A),
                isNew = true
            )
        )

        // 24. Zoom Workplace
        list.add(
            AppItem(
                id = "zoom",
                name = "Zoom Workplace",
                nameAr = "زووم",
                category = AppCategory.BUSINESS,
                tags = listOf("أعمال", "مؤتمرات", "مكالمات", "إنتاجية"),
                rating = 4.3f,
                ratingCount = "4.2M",
                downloadCount = "+500M",
                size = "95 MB",
                sizeBytes = 99_614_720L,
                iconEmoji = "💼",
                accentColor = 0xFF0284C7,
                descriptionAr = "منصة العمل الافتراضية الشاملة للاجتماعات المرئية ومشاركة الشاشة ومحادثات الفريق والسبورة البيضاء التفاعلية.",
                descriptionEn = "Connect, collaborate, and work anywhere with video meetings, team chat, and whiteboard.",
                featuresAr = listOf(
                    "اجتماعات فيديو عالية الجودة مع ما يصل إلى 1000 مشارك",
                    "مشاركة شاشة تفاعلية مع أدوات رسم وشرح",
                    "خلفيات افتراضية مخصصة وعزل ذكي للضوضاء",
                    "تسجيل الاجتماعات سحابياً ومشاركتها بسهولة"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49)
            )
        )

        // 25. VLC for Android
        list.add(
            AppItem(
                id = "vlc",
                name = "VLC for Android",
                nameAr = "مشغل VLC",
                category = AppCategory.VIDEO,
                tags = listOf("فيديو", "أدوات", "مشغل وسائط"),
                rating = 4.4f,
                ratingCount = "2.1M",
                downloadCount = "+100M",
                size = "35 MB",
                sizeBytes = 36_700_160L,
                iconEmoji = "🎞️",
                accentColor = 0xFFF97316,
                descriptionAr = "أفضل مشغل وسائط مجاني ومفتوح المصدر بدون إعلانات. يدعم تشغيل جميع صيغ الفيديو والصوت والترجمات والبث الشبكي.",
                descriptionEn = "The best open source video and music player, fast and easy, with support for all formats.",
                featuresAr = listOf(
                    "تشغيل جميع صيغ الفيديو (MKV, MP4, AVI, MOV) تلقائياً",
                    "دعم ترجمات متعددة اللغات مع ضبط التوقيت",
                    "تحكم كامل بالإيماءات لمستوى الصوت والسطوع",
                    "خالٍ تماماً من الإعلانات والتتبع وبدون أذونات تجسس"
                ),
                screenshotsColors = listOf(0xFFEA580C, 0xFFC2410C, 0xFF9A3412, 0xFF7C2D12)
            )
        )

        // 26. Canva: Design, Photo & Video
        list.add(
            AppItem(
                id = "canva",
                name = "Canva",
                nameAr = "كانفا",
                category = AppCategory.TOOLS,
                tags = listOf("تصميم", "أدوات", "إنتاجية"),
                rating = 4.8f,
                ratingCount = "14M",
                downloadCount = "+100M",
                size = "40 MB",
                sizeBytes = 41_943_040L,
                iconEmoji = "✨",
                accentColor = 0xFF06B6D4,
                descriptionAr = "اجعل التصميم سهلاً وممتعاً! آلاف القوالب الاحترافية للشعارات، وبطاقات الأعمال، ومنشورات التواصل، والعروض التقديمية بنقرة واحدة.",
                descriptionEn = "Canva makes design amazingly simple and fun! Create stunning logos, presentations, and social graphics.",
                featuresAr = listOf(
                    "أكثر من مليون قالب مجاني جاهز للتعديل الفوري",
                    "مكتبة ضخمة من الصور والأيقونات والخطوط العربية",
                    "حفظ ومشاركة بصيغ PDF و PNG وفيديو بجودة عالية",
                    "تعاون فوري وتعديل التصاميم مع فريقك"
                ),
                screenshotsColors = listOf(0xFF0891B2, 0xFF0E7490, 0xFF155E75, 0xFF164E63)
            )
        )

        // 27. Among Us
        list.add(
            AppItem(
                id = "among_us",
                name = "Among Us",
                nameAr = "Among Us",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "أكشن", "ذكاء", "حفلات"),
                rating = 4.2f,
                ratingCount = "14M",
                downloadCount = "+500M",
                size = "250 MB",
                sizeBytes = 262_144_000L,
                iconEmoji = "🚀",
                accentColor = 0xFFEF4444,
                descriptionAr = "العب عبر الإنترنت أو عبر Wi-Fi المحلي مع 4 إلى 15 لاعباً لإعداد سفينتك الفضائية للانطلاق، ولكن احذر المحتال بينكم!",
                descriptionEn = "Play online or over local WiFi with 4-15 players as you attempt to prep your spaceship for departure.",
                featuresAr = listOf(
                    "مباريات خدع وذكاء اجتماعي ممتعة مع أصدقائك",
                    "تخصيص شكلي رائع للشخصيات والحيوانات الأليفة",
                    "مهام متنوعة وتصويت لاكتشاف الدخيل الخفي",
                    "أدوار خاصة جديدة مثل العالم والملاك الحارس"
                ),
                screenshotsColors = listOf(0xFFDC2626, 0xFFB91C1C, 0xFF991B1B, 0xFF7F1D1D)
            )
        )

        // 28. 8 Ball Pool
        list.add(
            AppItem(
                id = "eight_ball_pool",
                name = "8 Ball Pool",
                nameAr = "8 Ball Pool (بلياردو)",
                category = AppCategory.SPORTS,
                tags = listOf("رياضة", "بلياردو", "ألعاب"),
                rating = 4.5f,
                ratingCount = "25M",
                downloadCount = "+1B",
                size = "95 MB",
                sizeBytes = 99_614_720L,
                iconEmoji = "🎱",
                accentColor = 0xFF1E3A8A,
                descriptionAr = "لعبة البلياردو رقم 1 في العالم من Miniclip! تنافس 1 ضد 1 مع أصدقائك أو ادخل بطولات 8 لاعبين لتفوز بعصي بلياردو نادرة وطاولات فخمة.",
                descriptionEn = "The World's #1 Pool game! Play with friends and legends in online 1-on-1 matches.",
                featuresAr = listOf(
                    "فيزياء واقعية مذهلة لحركة كرات البلياردو",
                    "بطولات دولية وكؤوس مرموقة في مدن كبرى",
                    "مئات العصي الحصرية القابلة للتطوير وزيادة القوة",
                    "تحديات أسبوعية وجوائز عملات مستمرة"
                ),
                screenshotsColors = listOf(0xFF1E40AF, 0xFF1D4ED8, 0xFF2563EB, 0xFF3B82F6)
            )
        )

        // 29. Candy Crush Saga
        list.add(
            AppItem(
                id = "candy_crush",
                name = "Candy Crush Saga",
                nameAr = "كاندي كراش",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "ألغاز", "أركيد"),
                rating = 4.6f,
                ratingCount = "36M",
                downloadCount = "+1B",
                size = "90 MB",
                sizeBytes = 94_371_840L,
                iconEmoji = "🍬",
                accentColor = 0xFFEC4899,
                descriptionAr = "لعبة الألغاز الأسطورية التي يحبها مئات الملايين! طابق الحلوى الملونة وتخطَّ آلاف المستويات في مغامرة حلوة لذيذة.",
                descriptionEn = "Match candies in this tasty puzzle adventure to progress to the next level for that sweet winning feeling!",
                featuresAr = listOf(
                    "أكثر من 10 آلاف مستوى ممتع ومتنوع الصعوبة",
                    "معززات سحرية وانفجارات حلوى مبهرة",
                    "عجلة الحظ اليومية لربح مساعدات مجانية",
                    "مزامنة تقدمك عبر فيسبوك والمنافسة مع الأصدقاء"
                ),
                screenshotsColors = listOf(0xFFDB2777, 0xFFBE185D, 0xFF9D174D, 0xFF831843)
            )
        )

        // 30. eFootball 2025
        list.add(
            AppItem(
                id = "efootball",
                name = "eFootball 2025",
                nameAr = "eFootball 2025 (بيس)",
                category = AppCategory.SPORTS,
                tags = listOf("رياضة", "كرة قدم", "ألعاب"),
                rating = 4.3f,
                ratingCount = "13M",
                downloadCount = "+100M",
                size = "2.8 GB",
                sizeBytes = 3_006_477_107L,
                iconEmoji = "🏆",
                accentColor = 0xFF2563EB,
                descriptionAr = "عصر جديد من كرة القدم الافتراضية مع كونامي! تحكم تكتيكي متطور، ركلات حرة واقعية، وتشكيل فريقك الخاص بمساعدة أساطير كرة القدم العالمية.",
                descriptionEn = "A new era of digital football. Realistic ball physics and authentic club action on your phone.",
                featuresAr = listOf(
                    "فيزياء دقيقة مستوحاة من حركة نجوم الكرة في الواقع",
                    "تراخيص نوادي كبرى تشمل برشلونة وبايرن ميونخ ومانشستر يونايتد",
                    "بطولات دوري تنافسية أسبوعية وجوائز ترقية",
                    "رسوميات عالية بمعدل 60 إطاراً في الثانية"
                ),
                screenshotsColors = listOf(0xFF1D4ED8, 0xFF1E40AF, 0xFF1E3A8A, 0xFF172554)
            )
        )

        // 31. Mobile Legends: Bang Bang
        list.add(
            AppItem(
                id = "mobile_legends",
                name = "Mobile Legends: Bang Bang",
                nameAr = "موبايل ليجندز",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "موبا", "أكشن", "استراتيجية"),
                rating = 4.3f,
                ratingCount = "34M",
                downloadCount = "+500M",
                size = "150 MB",
                sizeBytes = 157_286_400L,
                iconEmoji = "⚔️",
                accentColor = 0xFF8B5CF6,
                descriptionAr = "انضم إلى أصدقائك في أحدث معارك 5 ضد 5 MOBA ضد لاعبين حقيقيين! اختر أبطالك المفضلين، وابنِ الفريق المثالي، واستمتع باللعب التنافسي الممتع.",
                descriptionEn = "Join your friends in 5v5 MOBA showdowns against real players. Matchmaking in 10 seconds, battles in 10 minutes.",
                featuresAr = listOf(
                    "خرائط MOBA كلاسيكية بثلاثة مسارات وغابة كاملة",
                    "تطابق في 10 ثوانٍ ومعارك ملحمية تستمر 10 دقائق",
                    "أكثر من 100 بطل بأدوار متنوعة بين الهجوم والدفاع",
                    "تحكم بسيط ومريح بالأصابع مع تصويب ذكي"
                ),
                screenshotsColors = listOf(0xFF7C3AED, 0xFF6D28D9, 0xFF5B21B6, 0xFF4C1D95)
            )
        )

        // 32. Genshin Impact
        list.add(
            AppItem(
                id = "genshin_impact",
                name = "Genshin Impact",
                nameAr = "قينشين إمباكت",
                category = AppCategory.GAMES,
                tags = listOf("ألعاب", "آر بي جي", "عالم مفتوح", "مغامرات"),
                rating = 4.4f,
                ratingCount = "5.5M",
                downloadCount = "+100M",
                size = "3.2 GB",
                sizeBytes = 3_435_973_836L,
                iconEmoji = "🗡️",
                accentColor = 0xFF3B82F6,
                descriptionAr = "ادخل إلى تيفات، عالم شاسع يعج بالحياة ويتدفق بالطاقة الأولية. استكشف ممالك سبع عناصر، وقابل شخصيات مميزة، واكتشف أسرار العالم المفقود.",
                descriptionEn = "Step into Teyvat, a vast world teeming with life and flowing with elemental energy in this award-winning RPG.",
                featuresAr = listOf(
                    "عالم مفتوح مذهل برسوميات أنمي غاية في الجمال",
                    "نظام قتالي عنصري متطور يعتمد على دمج العناصر السحرية",
                    "موسيقى أوركسترالية حية تؤديها أعظم الفرق العالمية",
                    "تحديثات ضخمة دورية تضيف مناطق وشخصيات وقصصاً جديدة"
                ),
                screenshotsColors = listOf(0xFF2563EB, 0xFF1D4ED8, 0xFF1E40AF, 0xFF1E3A8A)
            )
        )

        // 33. Shazam: Find Music
        list.add(
            AppItem(
                id = "shazam",
                name = "Shazam",
                nameAr = "شازام",
                category = AppCategory.MUSIC,
                tags = listOf("موسيقى", "أدوات", "صوتيات"),
                rating = 4.7f,
                ratingCount = "8M",
                downloadCount = "+500M",
                size = "25 MB",
                sizeBytes = 26_214_400L,
                iconEmoji = "⚡",
                accentColor = 0xFF0284C7,
                descriptionAr = "سيتعرف Shazam على أي أغنية في ثوانٍ. اكتشف الفنانين وكلمات الأغاني ومقاطع الفيديو وقوائم التشغيل بنقرة زر واحدة في أي مكان.",
                descriptionEn = "Identify any song in seconds. Listen and add to Apple Music or Spotify playlists.",
                featuresAr = listOf(
                    "تعرف فوري على الموسيقى في ثوانٍ معدودة",
                    "عرض متزامن لكلمات الأغنية مع اللحن",
                    "الاستماع المباشر في Spotify أو YouTube",
                    "ميزة التعرف العائم في الخلفية للتطبيقات الأخرى"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49)
            )
        )

        // 34. Speedtest by Ookla
        list.add(
            AppItem(
                id = "speedtest",
                name = "Speedtest by Ookla",
                nameAr = "سبيد تست",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "شبكة", "إنترنت"),
                rating = 4.4f,
                ratingCount = "3.5M",
                downloadCount = "+100M",
                size = "45 MB",
                sizeBytes = 47_185_920L,
                iconEmoji = "🚀",
                accentColor = 0xFF6366F1,
                descriptionAr = "المعيار العالمي لقياس سرعة الإنترنت واستقرار الاتصال وشبكات الجيل الخامس 5G بنقرة واحدة مع خرائط تغطية تفصيلية.",
                descriptionEn = "Use Speedtest for an easy, one-tap connection internet performance and speed test.",
                featuresAr = listOf(
                    "قياس دقيق لسرعات التنزيل والرفع والـ Ping والـ Jitter",
                    "اختبار جودة بث الفيديو ودقة التشغيل",
                    "خرائط تغطية شبكات المحمول 5G و 4G",
                    "سجل مفصل للنتائج ومشاركتها بسهولة"
                ),
                screenshotsColors = listOf(0xFF4F46E5, 0xFF4338CA, 0xFF3730A3, 0xFF312E81)
            )
        )

        // 35. CPU-Z
        list.add(
            AppItem(
                id = "cpu_z",
                name = "CPU-Z",
                nameAr = "CPU-Z",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "عتاد", "معالج"),
                rating = 4.6f,
                ratingCount = "900K",
                downloadCount = "+100M",
                size = "8 MB",
                sizeBytes = 8_388_608L,
                iconEmoji = "⚙️",
                accentColor = 0xFF64748B,
                descriptionAr = "أداة شهيرة تقدم معلومات مفصلة عن معالج هاتفك ومستشعراته، ودرجة حرارة البطارية والذاكرة العشوائية وسرعة التردد بالوقت الحقيقي.",
                descriptionEn = "Reports information about your device's SoC, system, battery, and sensors in real-time.",
                featuresAr = listOf(
                    "معلومات معمارية المعالج والأنوية والترددات الحية",
                    "مراقبة درجة حرارة البطارية ونسبة الشحن والجهد",
                    "فحص الذاكرة RAM والتخزين الداخلي المتاح",
                    "قراءات حية لجميع مستشعرات الهاتف"
                ),
                screenshotsColors = listOf(0xFF475569, 0xFF334155, 0xFF1E293B, 0xFF0F172A)
            )
        )

        // 36. RAR for Android
        list.add(
            AppItem(
                id = "rar",
                name = "RAR",
                nameAr = "برنامج RAR",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "ضغط ملفات", "ملفات"),
                rating = 4.4f,
                ratingCount = "950K",
                downloadCount = "+100M",
                size = "12 MB",
                sizeBytes = 12_582_912L,
                iconEmoji = "🗜️",
                accentColor = 0xFF0D9488,
                descriptionAr = "برنامج ضغط وفك ضغط الملفات الأصلي من RARLAB. يدعم إنشاء وفك ملفات RAR و ZIP وتشفيرها بكلمة مرور وإصلاح الأرشيفات التالفة.",
                descriptionEn = "All-in-one, original, free, simple, easy and quick compression program and archiver.",
                featuresAr = listOf(
                    "إنشاء أرشيفات RAR و ZIP عالية الضغط",
                    "فك ضغط ملفات RAR, ZIP, TAR, GZ, 7z, ISO",
                    "تشفير بكلمة مرور وحماية الملفات الحساسة",
                    "اختبار قياس أداء المعالج وسرعة الضغط"
                ),
                screenshotsColors = listOf(0xFF0F766E, 0xFF115E59, 0xFF134E4A, 0xFF042F2E)
            )
        )

        // 37. X-plore File Manager
        list.add(
            AppItem(
                id = "xplore",
                name = "X-plore File Manager",
                nameAr = "إكس بلور للملفات",
                category = AppCategory.TOOLS,
                tags = listOf("أدوات", "مدير ملفات", "سحابي"),
                rating = 4.5f,
                ratingCount = "500K",
                downloadCount = "+10M",
                size = "15 MB",
                sizeBytes = 15_728_640L,
                iconEmoji = "📁",
                accentColor = 0xFFF97316,
                descriptionAr = "مدير ملفات احترافي بلوحتين متجاورتين يتيح تصفح الملفات والشبكات والسحابة وتشغيل الوسائط وعرض التطبيقات المثبتة بسهولة.",
                descriptionEn = "Dual-pane file manager with tree view, LAN, Root, FTP, and cloud storage support.",
                featuresAr = listOf(
                    "واجهة الشاشتين المزدوجتين لنقل الملفات بالسحب والإفلات",
                    "دعم التخزين السحابي (Google Drive, Dropbox, OneDrive)",
                    "مشغل وسائط ومستعرض صور ونصوص مدمج",
                    "نقل الملفات عبر Wi-Fi بدون كابلات"
                ),
                screenshotsColors = listOf(0xFFEA580C, 0xFFC2410C, 0xFF9A3412, 0xFF7C2D12)
            )
        )

        // 38. Pinterest
        list.add(
            AppItem(
                id = "pinterest",
                name = "Pinterest",
                nameAr = "بينترست",
                category = AppCategory.ENTERTAINMENT,
                tags = listOf("ترفيه", "تصميم", "أفكار", "صور"),
                rating = 4.5f,
                ratingCount = "10M",
                downloadCount = "+500M",
                size = "45 MB",
                sizeBytes = 47_185_920L,
                iconEmoji = "📌",
                accentColor = 0xFFE60023,
                descriptionAr = "منصة الإلهام البصري الأولى. ابحث عن أفكار جديدة للموضة والديكور المنزلي والوصفات والمشاريع الحرفية واحفظ لوحات إلهامك.",
                descriptionEn = "Discover billions of ideas to inspire your style, home, recipes, and life projects.",
                featuresAr = listOf(
                    "اكتشاف مرئي بالصور ومطابقة الأفكار بالذكاء الاصطناعي",
                    "إنشاء لوحات خاصة وعامة لتنظيم إلهامك",
                    "حفظ الروابط والمنتجات للشراء لاحقاً",
                    "مشاركة المنشورات مع الأصدقاء والمصممين"
                ),
                screenshotsColors = listOf(0xFFB91C1C, 0xFF991B1B, 0xFF7F1D1D, 0xFF450A0A)
            )
        )

        // 39. Microsoft Teams
        list.add(
            AppItem(
                id = "teams",
                name = "Microsoft Teams",
                nameAr = "مايكروسوفت تيمز",
                category = AppCategory.BUSINESS,
                tags = listOf("أعمال", "تعليم", "مكالمات", "فرق"),
                rating = 4.3f,
                ratingCount = "5.8M",
                downloadCount = "+100M",
                size = "80 MB",
                sizeBytes = 83_886_080L,
                iconEmoji = "👥",
                accentColor = 0xFF4F46E5,
                descriptionAr = "مساحة العمل الجماعية الرائدة من مايكروسوفت. محادثات فرق، واجتماعات فيديو، وقنوات عمل، ومشاركة وثائق Office في بيئة موحدة.",
                descriptionEn = "Whether you’re working with teammates or friends, Microsoft Teams brings people together.",
                featuresAr = listOf(
                    "قنوات عمل منظمة لكل مشروع أو قسم",
                    "تكامل كامل مع Word و Excel و PowerPoint",
                    "مكالمات فيديو عالية الأمان مع ترجمة فورية",
                    "تقويم موحد وإشعارات المواعيد والمهام"
                ),
                screenshotsColors = listOf(0xFF4338CA, 0xFF3730A3, 0xFF312E81, 0xFF1E1B4B)
            )
        )

        // 40. MX Player Pro
        list.add(
            AppItem(
                id = "mx_player",
                name = "MX Player",
                nameAr = "إم إكس بلاير",
                category = AppCategory.VIDEO,
                tags = listOf("فيديو", "مشغل", "أدوات"),
                rating = 4.4f,
                ratingCount = "12M",
                downloadCount = "+1B",
                size = "55 MB",
                sizeBytes = 57_671_680L,
                iconEmoji = "🎞️",
                accentColor = 0xFF0284C7,
                descriptionAr = "مشغل فيديو قوي مع تسريع عتادي متقدم ودعم فك التشفير متعدد الأنوية وإيماءات الترجمة وقفل أمان الأطفال.",
                descriptionEn = "Powerful video player with advanced hardware acceleration and subtitle gesture controls.",
                featuresAr = listOf(
                    "تسريع عتادي فائق بتقنية HW+ Decoder",
                    "إيماءات تكبير وتصغير الشاشة وحجم الصوت",
                    "قفل الأطفال للحفاظ على استمرار المشاهدة بأمان",
                    "تحميل مباشر لترجمات الأفلام والمسلسلات أونلاين"
                ),
                screenshotsColors = listOf(0xFF0369A1, 0xFF075985, 0xFF0C4A6E, 0xFF082F49)
            )
        )

        _apps.value = list
    }
}
