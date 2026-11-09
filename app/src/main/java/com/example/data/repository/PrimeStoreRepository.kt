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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.download.DownloadService
import com.example.data.installer.ApkInstaller
import com.example.data.model.DownloadStatus
import com.example.data.supabase.SupabaseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrimeStoreRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("prime_store_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.Default)

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

    var onApkDownloadCompleted: ((appId: String, apkFile: File) -> Unit)? = null

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
                _apps.value = remoteApps
            } catch (e: Exception) {
                // Keep current state
            }
        }
    }

    private fun loadFavoritesFromPrefs() {
        val saved = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        _favorites.value = saved
    }

    private fun saveFavoritesToPrefs() {
        prefs.edit().putStringSet("favorite_ids", _favorites.value).apply()
    }

    private fun loadSearchHistoryFromPrefs() {
        val raw = prefs.getString("search_history", "") ?: ""
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

        val targetFile = ApkInstaller.getApkFile(context, appId)
        if (targetFile.exists() && targetFile.length() > 0) {
            val completedItem = DownloadItem(
                appId = app.id,
                appName = app.nameAr.ifBlank { app.name },
                appIcon = app.iconEmoji,
                accentColor = app.accentColor,
                progress = 1.0f,
                speed = "0 KB/s",
                downloadedSize = formatBytes(targetFile.length()),
                totalSize = formatBytes(targetFile.length()),
                isCompleted = true,
                isPaused = false,
                status = DownloadStatus.COMPLETED,
                apkFilePath = targetFile.absolutePath,
                errorMessage = null
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = completedItem
            } else {
                currentList.add(0, completedItem)
            }
            _downloads.value = currentList
            emitToast("${app.nameAr} محمل بالفعل وجاهز للتثبيت ✔")
            onApkDownloadCompleted?.invoke(appId, targetFile)
            return
        }

        if (activeDownloadJobs.containsKey(appId)) {
            emitToast("جاري تحميل ${app.nameAr} حالياً")
            return
        }

        val rawUrl = app.fileUrl?.trim()
        val isDirectApk = SupabaseClient.isValidDownloadLink(rawUrl)

        if (!isDirectApk) {
            emitToast("رابط التحميل غير مباشر أو غير مدعوم، يرجى مراجعة الأدمن")
            val failedItem = DownloadItem(
                appId = app.id,
                appName = app.nameAr.ifBlank { app.name },
                appIcon = app.iconEmoji,
                accentColor = app.accentColor,
                progress = 0f,
                speed = "فشل",
                downloadedSize = "0 MB",
                totalSize = app.size,
                isCompleted = false,
                isPaused = false,
                status = DownloadStatus.FAILED,
                apkFilePath = null,
                errorMessage = "رابط التحميل غير مباشر، أخبر الأدمن"
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = failedItem
            } else {
                currentList.add(0, failedItem)
            }
            _downloads.value = currentList
            return
        }

        val directUrl = rawUrl!!
        val appDisplayName = app.nameAr.ifBlank { app.name }

        val startingItem = DownloadItem(
            appId = app.id,
            appName = appDisplayName,
            appIcon = app.iconEmoji,
            accentColor = app.accentColor,
            progress = 0.01f,
            speed = "جاري البدء...",
            downloadedSize = "0 MB",
            totalSize = app.size,
            isCompleted = false,
            isPaused = false,
            status = DownloadStatus.DOWNLOADING,
            apkFilePath = null,
            errorMessage = null
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = startingItem
        } else {
            currentList.add(0, startingItem)
        }
        _downloads.value = currentList

        emitToast("بدأ تحميل $appDisplayName")
        DownloadService.start(context, appDisplayName)

        val job = scope.launch(Dispatchers.IO) {
            val tempFile = File(targetFile.parentFile, "${appId}.apk.part")
            if (tempFile.exists()) tempFile.delete()
            var response: okhttp3.Response? = null

            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(directUrl)
                    .header("User-Agent", "PrimeStore/1.0 (Android)")
                    .build()

                response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }

                val responseBody = response.body ?: throw IOException("الملف فارغ أو غير متاح")
                val contentLength = responseBody.contentLength()
                val totalBytes = if (contentLength > 0) contentLength else app.sizeBytes.coerceAtLeast(10_000_000L)

                val inputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(16 * 1024)
                var bytesReadSoFar: Long = 0
                var lastUpdateTime = System.currentTimeMillis()
                var lastUpdateBytes: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        tempFile.delete()
                        DownloadService.stop(context)
                        return@launch
                    }

                    outputStream.write(buffer, 0, read)
                    bytesReadSoFar += read

                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastUpdateTime
                    if (timeDelta >= 350) {
                        val bytesDelta = bytesReadSoFar - lastUpdateBytes
                        val speedMBps = if (timeDelta > 0) {
                            (bytesDelta.toDouble() / (1024.0 * 1024.0)) / (timeDelta.toDouble() / 1000.0)
                        } else 0.0
                        val speedText = String.format(Locale.US, "%.1f MB/s", speedMBps)
                        val progress = (bytesReadSoFar.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                        val downloadedText = formatBytes(bytesReadSoFar)
                        val totalText = formatBytes(totalBytes)

                        _downloads.value = _downloads.value.map { d ->
                            if (d.appId == appId) {
                                d.copy(
                                    progress = progress,
                                    speed = speedText,
                                    downloadedSize = downloadedText,
                                    totalSize = totalText,
                                    status = DownloadStatus.DOWNLOADING,
                                    isPaused = false
                                )
                            } else d
                        }

                        DownloadService.updateProgress(
                            context,
                            appDisplayName,
                            (progress * 100).toInt(),
                            speedText
                        )

                        lastUpdateTime = now
                        lastUpdateBytes = bytesReadSoFar
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)

                val finalTotalText = formatBytes(bytesReadSoFar)
                _downloads.value = _downloads.value.map { d ->
                    if (d.appId == appId) {
                        d.copy(
                            progress = 1.0f,
                            speed = "0 KB/s",
                            downloadedSize = finalTotalText,
                            totalSize = finalTotalText,
                            isCompleted = true,
                            status = DownloadStatus.COMPLETED,
                            apkFilePath = targetFile.absolutePath,
                            errorMessage = null
                        )
                    } else d
                }

                DownloadService.stop(context)
                activeDownloadJobs.remove(appId)

                emitToast("اكتمل تحميل $appDisplayName، جاري فتح التثبيت...")

                scope.launch {
                    com.example.data.supabase.SupabaseClient.incrementDownloads(appId)
                }

                scope.launch(Dispatchers.Main) {
                    onApkDownloadCompleted?.invoke(appId, targetFile)
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    tempFile.delete()
                    DownloadService.stop(context)
                    return@launch
                }
                tempFile.delete()
                DownloadService.stop(context)
                activeDownloadJobs.remove(appId)
                val errorMsg = e.message ?: "فشل التحميل"
                _downloads.value = _downloads.value.map { d ->
                    if (d.appId == appId) {
                        d.copy(
                            status = DownloadStatus.FAILED,
                            speed = "فشل",
                            errorMessage = errorMsg
                        )
                    } else d
                }
                emitToast("فشل تحميل $appDisplayName: $errorMsg")
            } finally {
                response?.close()
            }
        }
        activeDownloadJobs[appId] = job
    }

    fun pauseDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        DownloadService.stop(context)
        _downloads.value = _downloads.value.map {
            if (it.appId == appId) it.copy(isPaused = true, status = DownloadStatus.PAUSED, speed = "متوقف") else it
        }
        emitToast("تم إيقاف التحميل مؤقتاً")
    }

    fun resumeDownload(appId: String) {
        startDownload(appId)
    }

    fun cancelDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        DownloadService.stop(context)
        try {
            val targetFile = ApkInstaller.getApkFile(context, appId)
            val tempFile = File(targetFile.parentFile, "${appId}.apk.part")
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
        } catch (e: Exception) {
            // Ignored
        }
        _downloads.value = _downloads.value.filterNot { it.appId == appId }
        emitToast("تم إلغاء التحميل")
    }

    fun clearAllDownloads() {
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        DownloadService.stop(context)
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

    private fun categoryToString(cat: AppCategory): String {
        return when (cat) {
            AppCategory.GAMES -> "games"
            AppCategory.APPS -> "apps"
            AppCategory.POPULAR -> "trending"
            AppCategory.NEW_GAMES -> "new_games"
            AppCategory.ENTERTAINMENT -> "entertainment"
            AppCategory.SPORTS -> "sports"
            AppCategory.BUSINESS -> "business"
            AppCategory.EDUCATION -> "education"
            AppCategory.MUSIC -> "music"
            AppCategory.TOOLS -> "tools"
            AppCategory.PRODUCTIVITY -> "productivity"
            AppCategory.PERSONALIZATION -> "personalization"
            AppCategory.MEDIA -> "media"
            AppCategory.SOCIAL -> "social"
            AppCategory.ACTION -> "action"
            AppCategory.RPG -> "rpg"
            AppCategory.SIMULATION -> "simulation"
            AppCategory.EMULATORS -> "emulators"
            AppCategory.RETRO -> "retro"
            AppCategory.ROOT_TOOLS -> "root_tools"
            AppCategory.OPEN_SOURCE -> "open_source"
            AppCategory.VIDEO -> "video"
            AppCategory.MORE -> "more"
        }
    }

    fun addApp(app: AppItem, accessToken: String? = null) {
        scope.launch {
            val categoryKey = categoryToString(app.category)
            val result = com.example.data.supabase.SupabaseClient.insertApp(
                accessToken = accessToken,
                name = app.nameAr,
                category = categoryKey,
                rating = app.rating,
                size = app.size,
                description = app.descriptionAr,
                features = app.featuresAr,
                iconUrl = app.iconUrl ?: app.iconEmoji,
                fileUrl = app.fileUrl.orEmpty(),
                screenshots = app.screenshotUrls,
                architecture = app.supportedAbis,
                version = "1.0.0",
                packageName = "com.primestore.${app.nameAr.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                downloads = app.downloadCount
            )
            if (result.success) {
                refreshFromSupabase()
                emitToast("تم نشر التطبيق في المتجر بنجاح! 🎉")
            } else {
                emitToast("خطأ Supabase (${result.code}): ${result.message}")
            }
        }
    }

    fun updateApp(app: AppItem, accessToken: String? = null) {
        scope.launch {
            val categoryKey = categoryToString(app.category)
            val result = com.example.data.supabase.SupabaseClient.updateApp(
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
                screenshots = app.screenshotUrls,
                architecture = app.supportedAbis,
                version = "1.0.0",
                packageName = "com.primestore.${app.nameAr.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                downloads = app.downloadCount
            )
            if (result.success) {
                refreshFromSupabase()
                emitToast("تم تحديث التطبيق بنجاح! ✔")
            } else {
                emitToast("خطأ Supabase (${result.code}): ${result.message}")
            }
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
        _downloads.value = emptyList()
    }

    private fun initDefaultReviews() {
        _reviews.value = emptyMap()
    }

    private fun loadCatalog() {
        _apps.value = emptyList()
    }
}
