package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.download.DownloadService
import com.example.data.installer.ApkInstaller
import com.example.data.installer.PackageHelper
import com.example.data.model.DownloadStatus
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import com.example.data.sync.SyncManager
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

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) throw throwable
        SecureLogger.e("PrimeStoreRepo", "REPO_SCOPE_EXC", "Coroutine error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val appsRepository = AppsRepository.getInstance(context.applicationContext)
    private val syncManager = SyncManager.getInstance(context.applicationContext)

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    var remoteAppsUrl: String = ""

    val apps: StateFlow<List<AppItem>> = appsRepository.appsFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
    var onLocalApkFilesChanged: (() -> Unit)? = null

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        loadFavoritesFromPrefs()
        loadSearchHistoryFromPrefs()
        initDefaultDownloads()
        initDefaultReviews()
    }

    fun refreshFromSupabase(suppressNewBanner: Boolean = true) {
        scope.launch {
            syncManager.syncNow(forceFull = false, suppressNewBanner = suppressNewBanner)
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
        val app = apps.value.find { it.id == appId }
        val appName = app?.nameAr ?: app?.name ?: context.getString(R.string.dl_service_default_app)
        if (current.contains(appId)) {
            current.remove(appId)
            emitToast(context.getString(R.string.toast_fav_removed, appName))
        } else {
            current.add(appId)
            emitToast(context.getString(R.string.toast_fav_added, appName))
        }
        _favorites.value = current
        saveFavoritesToPrefs()
    }

    fun isFavorite(appId: String): Boolean = _favorites.value.contains(appId)

    fun startDownload(appId: String) {
        val app = apps.value.find { it.id == appId } ?: return
        val currentList = _downloads.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.appId == appId }

        val existingFile = ApkInstaller.findExistingPrimaryInstallFile(context, appId)
        if (existingFile != null && existingFile.exists() && existingFile.length() > 0L) {
            val completedItem = DownloadItem(
                appId = app.id,
                appName = app.nameAr.ifBlank { app.name },
                appIcon = app.iconEmoji,
                accentColor = app.accentColor,
                progress = 1.0f,
                speed = "0 KB/s",
                downloadedSize = formatBytes(existingFile.length()),
                totalSize = formatBytes(existingFile.length()),
                isCompleted = true,
                isPaused = false,
                status = DownloadStatus.COMPLETED,
                apkFilePath = existingFile.absolutePath,
                errorMessage = null
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = completedItem
            } else {
                currentList.add(0, completedItem)
            }
            _downloads.value = currentList
            val displayName = app.nameAr.ifBlank { app.name }
            emitToast(context.getString(R.string.toast_already_downloaded, displayName))
            onApkDownloadCompleted?.invoke(appId, existingFile)
            return
        }

        if (activeDownloadJobs.containsKey(appId)) {
            val displayName = app.nameAr.ifBlank { app.name }
            emitToast(context.getString(R.string.toast_already_downloading, displayName))
            return
        }

        val rawUrl = app.fileUrl?.trim()
        val validSplits = app.splitApkUrls.filter { SupabaseClient.isValidDownloadLink(it) }
        val isDirectApk = SupabaseClient.isValidDownloadLink(rawUrl)

        if (!isDirectApk && validSplits.isEmpty()) {
            emitToast(context.getString(R.string.toast_invalid_download_link))
            val failedItem = DownloadItem(
                appId = app.id,
                appName = app.nameAr.ifBlank { app.name },
                appIcon = app.iconEmoji,
                accentColor = app.accentColor,
                progress = 0f,
                speed = context.getString(R.string.status_failed),
                downloadedSize = "0 MB",
                totalSize = app.size,
                isCompleted = false,
                isPaused = false,
                status = DownloadStatus.FAILED,
                apkFilePath = null,
                errorMessage = context.getString(R.string.error_invalid_download_link_short)
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = failedItem
            } else {
                currentList.add(0, failedItem)
            }
            _downloads.value = currentList
            return
        }

        val appDisplayName = app.nameAr.ifBlank { app.name }

        val startingItem = DownloadItem(
            appId = app.id,
            appName = appDisplayName,
            appIcon = app.iconEmoji,
            accentColor = app.accentColor,
            progress = 0.01f,
            speed = context.getString(R.string.status_starting),
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

        emitToast(context.getString(R.string.toast_download_started, appDisplayName))
        DownloadService.start(context, appDisplayName)

        val job = scope.launch(Dispatchers.IO) {
            val bundleDir = ApkInstaller.getBundleDir(context, appId)
            val urlsToDownload = buildList {
                if (isDirectApk && !rawUrl.isNullOrBlank()) add(rawUrl)
                validSplits.forEach { if (!contains(it)) add(it) }
            }
            val useSplitBundle = urlsToDownload.size > 1
            val primaryExt = ApkInstaller.resolveExtensionFromUrl(urlsToDownload.firstOrNull())
            val primaryTargetFile = if (useSplitBundle) {
                File(bundleDir, "00_base.apk")
            } else {
                ApkInstaller.getApkFile(context, appId, primaryExt)
            }
            val activeTempFiles = mutableListOf<File>()

            try {
                val client = downloadClient

                var cumulativeBytes = 0L
                val totalParts = urlsToDownload.size

                for ((partIdx, partUrl) in urlsToDownload.withIndex()) {
                    val destFile = if (useSplitBundle) {
                        val name = if (partIdx == 0) "00_base.apk" else "split_${partIdx}.apk"
                        File(bundleDir, name)
                    } else {
                        primaryTargetFile
                    }
                    val tempFile = File(destFile.parentFile, "${destFile.name}.part")
                    activeTempFiles.add(tempFile)
                    if (tempFile.exists()) tempFile.delete()

                    val request = Request.Builder()
                        .url(partUrl)
                        .header("User-Agent", "PrimeStore/1.0 (Android)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: ${response.message}")
                        }
                        val responseBody = response.body ?: throw IOException(context.getString(R.string.error_empty_file))
                        val contentLength = responseBody.contentLength()
                        val estimatedTotal = if (contentLength > 0) contentLength else (app.sizeBytes / totalParts.coerceAtLeast(1)).coerceAtLeast(5_000_000L)

                        responseBody.byteStream().use { inputStream ->
                            FileOutputStream(tempFile).use { outputStream ->
                                val buffer = ByteArray(64 * 1024)
                                var partRead = 0L
                                var lastUpdateTime = System.currentTimeMillis()
                                var lastUpdateBytes = 0L
                                var read: Int

                                while (inputStream.read(buffer).also { read = it } != -1) {
                                    if (!isActive) {
                                        tempFile.delete()
                                        DownloadService.stop(context)
                                        return@launch
                                    }
                                    outputStream.write(buffer, 0, read)
                                    partRead += read
                                    cumulativeBytes += read

                                    val now = System.currentTimeMillis()
                                    val timeDelta = now - lastUpdateTime
                                    if (timeDelta >= 100) {
                                        val bytesDelta = partRead - lastUpdateBytes
                                        val speedMBps = if (timeDelta > 0) {
                                            (bytesDelta.toDouble() / (1024.0 * 1024.0)) / (timeDelta.toDouble() / 1000.0)
                                        } else 0.0
                                        val speedText = if (useSplitBundle) {
                                            context.getString(R.string.downloading_split_part, partIdx + 1, totalParts)
                                        } else {
                                            String.format(Locale.US, "%.1f MB/s", speedMBps)
                                        }
                                        val partFrac = (partRead.toFloat() / estimatedTotal).coerceIn(0f, 1f)
                                        val overallFrac = ((partIdx + partFrac) / totalParts.toFloat()).coerceIn(0f, 0.99f)
                                        val downloadedText = formatBytes(cumulativeBytes)
                                        val totalText = if (!useSplitBundle && contentLength > 0) formatBytes(contentLength) else app.size

                                        _downloads.value = _downloads.value.map { d ->
                                            if (d.appId == appId) {
                                                d.copy(
                                                    progress = overallFrac,
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
                                            (overallFrac * 100).toInt(),
                                            speedText
                                        )
                                        lastUpdateTime = now
                                        lastUpdateBytes = partRead
                                    }
                                }
                                outputStream.flush()
                            }
                        }
                    }

                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                }

                // Optional OBB download
                val obbUrl = app.obbUrl?.trim()
                if (!obbUrl.isNullOrBlank() && SupabaseClient.isValidDownloadLink(obbUrl)) {
                    val obbDest = ApkInstaller.getObbFile(context, appId)
                    val obbTemp = File(obbDest.parentFile, "${obbDest.name}.part")
                    activeTempFiles.add(obbTemp)
                    if (obbTemp.exists()) obbTemp.delete()

                    _downloads.value = _downloads.value.map { d ->
                        if (d.appId == appId) d.copy(speed = context.getString(R.string.downloading_obb_progress, 0)) else d
                    }

                    val obbReq = Request.Builder().url(obbUrl).header("User-Agent", "PrimeStore/1.0 (Android)").build()
                    client.newCall(obbReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val obbTotal = resp.body?.contentLength() ?: -1L
                            var obbRead = 0L
                            var lastObbPct = 0
                            resp.body?.byteStream()?.use { input ->
                                FileOutputStream(obbTemp).use { out ->
                                    val buf = ByteArray(64 * 1024)
                                    var r: Int
                                    while (input.read(buf).also { r = it } != -1) {
                                        if (!isActive) {
                                            obbTemp.delete()
                                            DownloadService.stop(context)
                                            return@launch
                                        }
                                        out.write(buf, 0, r)
                                        obbRead += r
                                        cumulativeBytes += r
                                        if (obbTotal > 0) {
                                            val pct = ((obbRead * 100) / obbTotal).toInt().coerceIn(0, 100)
                                            if (pct >= lastObbPct + 5 || pct == 100) {
                                                lastObbPct = pct
                                                _downloads.value = _downloads.value.map { d ->
                                                    if (d.appId == appId) d.copy(speed = context.getString(R.string.downloading_obb_progress, pct)) else d
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (obbDest.exists()) obbDest.delete()
                            obbTemp.renameTo(obbDest)
                        }
                    }
                }

                // Optional SHA-256 verification before installing
                if (!app.sha256.isNullOrBlank()) {
                    _downloads.value = _downloads.value.map { d ->
                        if (d.appId == appId) d.copy(speed = context.getString(R.string.verifying_sha256)) else d
                    }
                    if (!PackageHelper.verifySha256(primaryTargetFile, app.sha256)) {
                        throw IOException(context.getString(R.string.sha256_mismatch_error))
                    }
                }

                val finalTotalText = formatBytes(cumulativeBytes.coerceAtLeast(primaryTargetFile.length()))
                _downloads.value = _downloads.value.map { d ->
                    if (d.appId == appId) {
                        d.copy(
                            progress = 1.0f,
                            speed = "0 KB/s",
                            downloadedSize = finalTotalText,
                            totalSize = finalTotalText,
                            isCompleted = true,
                            status = DownloadStatus.COMPLETED,
                            apkFilePath = primaryTargetFile.absolutePath,
                            errorMessage = null
                        )
                    } else d
                }

                DownloadService.stop(context)
                activeDownloadJobs.remove(appId)
                onLocalApkFilesChanged?.invoke()

                emitToast(context.getString(R.string.toast_download_completed, appDisplayName))

                scope.launch {
                    SupabaseClient.incrementDownloads(appId)
                }

                scope.launch(Dispatchers.Main) {
                    onApkDownloadCompleted?.invoke(appId, primaryTargetFile)
                }

            } catch (e: Exception) {
                activeTempFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                if (e is kotlinx.coroutines.CancellationException) {
                    DownloadService.stop(context)
                    return@launch
                }
                if (e.message == context.getString(R.string.sha256_mismatch_error)) {
                    ApkInstaller.cleanupAppInstallFiles(context, appId)
                }
                DownloadService.stop(context)
                activeDownloadJobs.remove(appId)
                val failedLabel = context.getString(R.string.status_failed)
                val errorMsg = SupabaseClient.friendlyNetworkError(context, e)
                _downloads.value = _downloads.value.map { d ->
                    if (d.appId == appId) {
                        d.copy(
                            status = DownloadStatus.FAILED,
                            speed = failedLabel,
                            errorMessage = errorMsg
                        )
                    } else d
                }
                emitToast(context.getString(R.string.toast_download_failed, appDisplayName, errorMsg))
            }
        }
        activeDownloadJobs[appId] = job
    }

    fun pauseDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        DownloadService.stop(context)
        val pausedLabel = context.getString(R.string.status_paused)
        _downloads.value = _downloads.value.map {
            if (it.appId == appId) it.copy(isPaused = true, status = DownloadStatus.PAUSED, speed = pausedLabel) else it
        }
        emitToast(context.getString(R.string.toast_download_paused))
    }

    fun resumeDownload(appId: String) {
        startDownload(appId)
    }

    fun cancelDownload(appId: String) {
        activeDownloadJobs[appId]?.cancel()
        activeDownloadJobs.remove(appId)
        DownloadService.stop(context)
        ApkInstaller.cleanupAppInstallFiles(context, appId)
        _downloads.value = _downloads.value.filterNot { it.appId == appId }
        onLocalApkFilesChanged?.invoke()
        emitToast(context.getString(R.string.toast_download_cancelled))
    }

    fun clearAllDownloads() {
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        DownloadService.stop(context)
        _downloads.value = emptyList()
        emitToast(context.getString(R.string.toast_downloads_cleared))
    }

    fun addReview(appId: String, rating: Int, comment: String, author: String? = null) {
        val reviewerName = author ?: context.getString(R.string.default_reviewer_name)
        val newReview = ReviewItem(
            id = "rev_${System.currentTimeMillis()}",
            userName = reviewerName,
            userAvatar = "👑",
            rating = rating,
            date = context.getString(R.string.review_date_now),
            comment = comment
        )
        val current = _reviews.value.toMutableMap()
        val list = (current[appId] ?: emptyList()).toMutableList()
        list.add(0, newReview)
        current[appId] = list
        _reviews.value = current
        emitToast(context.getString(R.string.toast_review_published))
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
            val categoryKey = app.categoryKey.ifBlank { categoryToString(app.category) }
            val result = SupabaseClient.insertApp(
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
                version = app.version.ifBlank { "1.0.0" },
                packageName = app.packageName ?: "com.primestore.${app.nameAr.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                downloads = app.downloadCount,
                itemType = app.itemType,
                sha256 = app.sha256.orEmpty(),
                splitUrls = app.splitApkUrls,
                obbUrl = app.obbUrl.orEmpty()
            )
            if (result.success) {
                refreshFromSupabase()
                emitToast(context.getString(R.string.toast_app_published))
            } else {
                emitToast(context.getString(R.string.toast_supabase_error, result.httpCode, result.message))
            }
        }
    }

    fun updateApp(app: AppItem, accessToken: String? = null) {
        scope.launch {
            val categoryKey = app.categoryKey.ifBlank { categoryToString(app.category) }
            val result = SupabaseClient.updateApp(
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
                version = app.version.ifBlank { "1.0.0" },
                packageName = app.packageName ?: "com.primestore.${app.nameAr.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                downloads = app.downloadCount,
                itemType = app.itemType,
                sha256 = app.sha256.orEmpty(),
                splitUrls = app.splitApkUrls,
                obbUrl = app.obbUrl.orEmpty()
            )
            if (result.success) {
                refreshFromSupabase()
                emitToast(context.getString(R.string.toast_app_updated))
            } else {
                emitToast(context.getString(R.string.toast_supabase_error, result.httpCode, result.message))
            }
        }
    }

    fun deleteApp(appId: String, accessToken: String? = null) {
        val app = apps.value.find { it.id == appId }
        val name = app?.nameAr ?: app?.name ?: context.getString(R.string.dl_service_default_app)
        emitToast(context.getString(R.string.toast_app_deleted, name))

        scope.launch {
            appsRepository.deleteLocalApp(appId)
            syncManager.invalidateEtag()
            com.example.data.supabase.SupabaseClient.deleteApp(accessToken, appId)
            syncManager.syncNow(forceFull = false, suppressNewBanner = true)
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
}
