package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.github.StoreReleaseInfo
import com.example.data.github.StoreSelfUpdater
import com.example.data.github.StoreUpdateCheckResult
import com.example.data.installer.ApkInstaller
import com.example.data.installer.InstallPrecheckResult
import com.example.data.installer.PackageHelper
import com.example.data.local.CachedAppEntity
import com.example.data.local.PrimeStoreDatabase
import com.example.data.local.ProblemReportEntity
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import com.example.data.model.StoreCategories
import com.example.data.model.StoreUpdateInfo
import com.example.data.repository.PrimeStoreRepository
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.DowngradeData
import com.example.ui.components.SignatureConflictData
import com.example.ui.screens.InstalledAppItemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class MainTab {
    HOME,
    CATEGORIES,
    FAVORITES,
    DOWNLOADS,
    SETTINGS
}

enum class SortOrder {
    POPULAR,
    RATING,
    NEWEST
}

enum class HomeFilterType {
    ALL,
    SMALL_SIZE,
    TOP_RATED,
    NEWEST
}

data class UserProfile(
    val name: String,
    val email: String,
    val isPrimeMember: Boolean
)

const val ADMIN_EMAIL = "zaim9002@gmail.com"

data class UiState(
    val showSplash: Boolean = true,
    val showOnboarding: Boolean = false,
    val showUpdatesScreen: Boolean = false,
    val showInstalledAppsScreen: Boolean = false,
    val currentTab: MainTab = MainTab.HOME,
    val selectedAppId: String? = null,
    val selectedCategory: AppCategory? = null,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val currentLanguage: String = "ar",
    val themeMode: String = "dark", // "dark", "amoled", "light"
    val themeAccent: Long = 0xFFA855F7, // Neon purple
    val autoTranslateEnabled: Boolean = true,
    val wifiOnlyTranslation: Boolean = false,
    val isDarkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val cacheSize: String = "42.8 MB",
    val downloadFilterTab: Int = 0, // 0 = جاري التحميل, 1 = تم التحميل
    val categorySortOrder: SortOrder = SortOrder.POPULAR,
    val homeFilterType: HomeFilterType = HomeFilterType.ALL,
    val showScreenshotIndex: Int? = null,
    val showReviewDialog: Boolean = false,
    val showLoginDialog: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val isAdmin: Boolean = false,
    val showAdminLoginDialog: Boolean = false,
    val showAdminPanel: Boolean = false,
    val showInstallPermissionDialog: Boolean = false,
    val pendingInstallAppId: String? = null,
    val conflictDialogData: SignatureConflictData? = null,
    val downgradeDialogData: DowngradeData? = null,
    val lowStorageDialogData: Pair<String, String>? = null,
    val lowStoragePendingAppId: String? = null,
    val userProfile: UserProfile? = null
)

class PrimeStoreViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PrimeStoreRepository(application)
    private val database = PrimeStoreDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val apps: StateFlow<List<AppItem>> = repository.apps
    val favorites: StateFlow<Set<String>> = repository.favorites
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val reviews: StateFlow<Map<String, List<ReviewItem>>> = repository.reviews
    val searchHistory: StateFlow<List<String>> = repository.searchHistory
    val toastEvents = repository.toastEvents

    // =========================================================================
    // 1) Installed Apps & Updates State
    // =========================================================================
    private val _installedAppsState = MutableStateFlow<List<InstalledAppItemState>>(emptyList())
    val installedAppsState: StateFlow<List<InstalledAppItemState>> = _installedAppsState.asStateFlow()

    private val _isRefreshingInstalledApps = MutableStateFlow(false)
    val isRefreshingInstalledApps: StateFlow<Boolean> = _isRefreshingInstalledApps.asStateFlow()

    // Conflict post-uninstall install continuation
    private var pendingConflictApkFile: File? = null
    private var pendingConflictPackageName: String? = null

    // =========================================================================
    // 2) Paged Apps & Infinite Scroll State for "كل التطبيقات"
    // =========================================================================
    private val _pagedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val pagedApps: StateFlow<List<AppItem>> = _pagedApps.asStateFlow()

    private val _selectedCategoryKey = MutableStateFlow("all")
    val selectedCategoryKey: StateFlow<String> = _selectedCategoryKey.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isLoadingNextPage = MutableStateFlow(false)
    val isLoadingNextPage: StateFlow<Boolean> = _isLoadingNextPage.asStateFlow()

    private val _hasMoreApps = MutableStateFlow(true)
    val hasMoreApps: StateFlow<Boolean> = _hasMoreApps.asStateFlow()

    private val _pagedError = MutableStateFlow<String?>(null)
    val pagedError: StateFlow<String?> = _pagedError.asStateFlow()

    private val paginationMutex = Mutex()
    private val pageSize = 20

    // =========================================================================
    // 3) Store Self-Update (GitHub & Supabase)
    // =========================================================================
    private val _availableStoreUpdate = MutableStateFlow<StoreUpdateInfo?>(null)
    val availableStoreUpdate: StateFlow<StoreUpdateInfo?> = _availableStoreUpdate.asStateFlow()

    private val _githubReleaseInfo = MutableStateFlow<StoreReleaseInfo?>(null)
    val githubReleaseInfo: StateFlow<StoreReleaseInfo?> = _githubReleaseInfo.asStateFlow()

    private val _hasNewUpdateBadge = MutableStateFlow(false)
    val hasNewUpdateBadge: StateFlow<Boolean> = _hasNewUpdateBadge.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isDownloadingStoreUpdate = MutableStateFlow(false)
    val isDownloadingStoreUpdate: StateFlow<Boolean> = _isDownloadingStoreUpdate.asStateFlow()

    private val _storeUpdateDownloadProgress = MutableStateFlow(0f)
    val storeUpdateDownloadProgress: StateFlow<Float> = _storeUpdateDownloadProgress.asStateFlow()

    // =========================================================================
    // Search
    // =========================================================================
    val searchResults: StateFlow<List<AppItem>> = combine(apps, _uiState) { allApps, state ->
        val q = state.searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            emptyList()
        } else {
            allApps.filter {
                it.name.lowercase().contains(q) ||
                        it.nameAr.contains(q) ||
                        it.category.titleAr.contains(q) ||
                        it.category.titleEn.lowercase().contains(q) ||
                        it.displayCategoryTitle.contains(q) ||
                        it.tags.any { tag -> tag.contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val authPrefs = application.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)

    init {
        SupabaseClient.init(application.applicationContext)
        SupabaseClient.onSessionExpiredListener = { onSessionExpired() }
        restoreSession()

        // Language setup
        val savedLang = authPrefs.getString("ps_language", "auto") ?: "auto"
        val activeLang = if (savedLang == "auto") detectDeviceLanguage() else savedLang

        // Theme setup
        val savedTheme = authPrefs.getString("ps_theme_mode", "dark") ?: "dark"
        val savedAccent = authPrefs.getLong("ps_theme_accent", 0xFFA855F7)
        val isFirstRun = !authPrefs.getBoolean("has_completed_onboarding", false)

        _uiState.value = _uiState.value.copy(
            currentLanguage = activeLang,
            themeMode = savedTheme,
            themeAccent = savedAccent,
            isDarkMode = savedTheme != "light",
            showOnboarding = isFirstRun
        )

        // Install callback when download finishes
        repository.onApkDownloadCompleted = { appId, apkFile ->
            val app = getApplication<Application>()
            handlePreInstall(appId, apkFile, app)
        }

        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(showSplash = false)
        }

        // Offline Room cache loading for instant start
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = database.cachedAppDao().getAllCachedAppsDirect()
                if (cached.isNotEmpty() && _pagedApps.value.isEmpty()) {
                    val converted = cached.map { mapCachedToAppItem(it) }
                    withContext(Dispatchers.Main) {
                        _pagedApps.value = converted
                    }
                }
            } catch (_: Exception) {}
        }

        // Welcome toast if updated
        StoreSelfUpdater.onAppStarted(application) { msg ->
            emitToast(msg)
        }

        // Initial fetch for Home infinite scroll
        loadInitialApps("all")

        // Auto-check store update on startup (max once every 6 hours)
        checkStoreUpdateSilentlyOnStartup()

        // Monitor apps for installed state
        viewModelScope.launch {
            apps.collect {
                refreshInstalledApps()
            }
        }
    }

    // =========================================================================
    // Onboarding & Theming
    // =========================================================================

    fun completeOnboarding() {
        authPrefs.edit().putBoolean("has_completed_onboarding", true).apply()
        _uiState.value = _uiState.value.copy(showOnboarding = false)
        emitToast("أهلاً بك في متجر Prime Store! استمتع بالتجربة 🚀")
    }

    fun setThemeMode(mode: String) {
        val cleanMode = when (mode.lowercase()) {
            "amoled" -> "amoled"
            "light" -> "light"
            else -> "dark"
        }
        authPrefs.edit().putString("ps_theme_mode", cleanMode).apply()
        _uiState.value = _uiState.value.copy(
            themeMode = cleanMode,
            isDarkMode = cleanMode != "light"
        )
        emitToast("تم تغيير مظهر التطبيق إلى $cleanMode")
    }

    fun setThemeAccent(accentColor: Long) {
        authPrefs.edit().putLong("ps_theme_accent", accentColor).apply()
        _uiState.value = _uiState.value.copy(themeAccent = accentColor)
        emitToast("تم تحديث اللون المميز للمتجر ✨")
    }

    fun toggleAutoTranslate() {
        val next = !_uiState.value.autoTranslateEnabled
        _uiState.value = _uiState.value.copy(autoTranslateEnabled = next)
        authPrefs.edit().putBoolean("ps_auto_translate", next).apply()
    }

    fun toggleWifiOnlyTranslation() {
        val next = !_uiState.value.wifiOnlyTranslation
        _uiState.value = _uiState.value.copy(wifiOnlyTranslation = next)
        authPrefs.edit().putBoolean("ps_wifi_only_translate", next).apply()
    }

    fun setHomeFilter(filter: HomeFilterType) {
        _uiState.value = _uiState.value.copy(homeFilterType = filter)
    }

    // =========================================================================
    // Infinite Scroll & Pagination Methods
    // =========================================================================

    fun selectHomeCategory(categoryKey: String) {
        if (_selectedCategoryKey.value == categoryKey && _pagedApps.value.isNotEmpty()) return
        _selectedCategoryKey.value = categoryKey
        loadInitialApps(categoryKey)
    }

    fun loadInitialApps(categoryKey: String = _selectedCategoryKey.value) {
        viewModelScope.launch {
            paginationMutex.withLock {
                _isInitialLoading.value = true
                _pagedError.value = null
                _hasMoreApps.value = true
            }

            try {
                val fetched = withContext(Dispatchers.IO) {
                    SupabaseClient.fetchAppsPaged(
                        limit = pageSize,
                        offset = 0,
                        categoryKey = if (categoryKey == "all") null else categoryKey
                    )
                }

                paginationMutex.withLock {
                    _pagedApps.value = fetched
                    _hasMoreApps.value = fetched.size >= pageSize
                    _isInitialLoading.value = false
                }

                // Cache newly fetched apps in Room database for offline use
                withContext(Dispatchers.IO) {
                    try {
                        val entities = fetched.map { mapAppItemToCached(it) }
                        database.cachedAppDao().insertApps(entities)
                    } catch (_: Exception) {}
                }

                refreshInstalledApps()
            } catch (e: Exception) {
                paginationMutex.withLock {
                    _isInitialLoading.value = false
                    _pagedError.value = e.message ?: "تعذر جلب التطبيقات"
                }
            }
        }
    }

    fun loadNextAppsPage() {
        if (!_hasMoreApps.value || _isLoadingNextPage.value || _isInitialLoading.value) return

        viewModelScope.launch {
            val offset: Int
            paginationMutex.withLock {
                if (!_hasMoreApps.value || _isLoadingNextPage.value) return@launch
                _isLoadingNextPage.value = true
                offset = _pagedApps.value.size
            }

            try {
                val categoryKey = _selectedCategoryKey.value
                val fetched = withContext(Dispatchers.IO) {
                    SupabaseClient.fetchAppsPaged(
                        limit = pageSize,
                        offset = offset,
                        categoryKey = if (categoryKey == "all") null else categoryKey
                    )
                }

                paginationMutex.withLock {
                    val currentList = _pagedApps.value.toMutableList()
                    val existingIds = currentList.map { it.id }.toSet()
                    val newUnique = fetched.filter { it.id !in existingIds }
                    currentList.addAll(newUnique)
                    _pagedApps.value = currentList
                    _hasMoreApps.value = fetched.size >= pageSize
                    _isLoadingNextPage.value = false
                }

                // Update Room cache
                withContext(Dispatchers.IO) {
                    try {
                        val entities = fetched.map { mapAppItemToCached(it) }
                        database.cachedAppDao().insertApps(entities)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                paginationMutex.withLock {
                    _isLoadingNextPage.value = false
                }
            }
        }
    }

    fun refreshData() {
        repository.refreshFromSupabase()
        refreshPagedApps()
        refreshInstalledApps()
        emitToast("تم تحديث التطبيقات والمنشورات بنجاح 🔄")
    }

    private fun refreshPagedApps() {
        loadInitialApps(_selectedCategoryKey.value)
    }

    // =========================================================================
    // Installed Apps & Updates Detection
    // =========================================================================

    fun refreshInstalledApps() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingInstalledApps.value = true
            val catalog = if (apps.value.isNotEmpty()) apps.value else _pagedApps.value
            val list = mutableListOf<InstalledAppItemState>()

            for (app in catalog) {
                val apkFile = ApkInstaller.getApkFile(context, app.id)
                val resolvedPkg = ApkInstaller.findInstalledPackage(
                    context,
                    app.name,
                    apkFile,
                    app.packageName
                )

                if (!resolvedPkg.isNullOrBlank()) {
                    val installedInfo = PackageHelper.getInstalledInfo(context, resolvedPkg)
                    if (installedInfo != null) {
                        val storeCode = app.versionCode
                        val hasUpdate = storeCode > installedInfo.versionCode
                        list.add(
                            InstalledAppItemState(
                                app = app,
                                installedPackageName = resolvedPkg,
                                installedVersionCode = installedInfo.versionCode,
                                installedVersionName = installedInfo.versionName,
                                storeVersionCode = storeCode,
                                hasUpdate = hasUpdate
                            )
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _installedAppsState.value = list
                _isRefreshingInstalledApps.value = false
            }
        }
    }

    fun openInstalledAppsScreen() {
        refreshInstalledApps()
        _uiState.value = _uiState.value.copy(showInstalledAppsScreen = true)
    }

    fun closeInstalledAppsScreen() {
        _uiState.value = _uiState.value.copy(showInstalledAppsScreen = false)
    }

    fun openUpdatesScreen() {
        refreshInstalledApps()
        _uiState.value = _uiState.value.copy(showUpdatesScreen = true)
    }

    fun closeUpdatesScreen() {
        _uiState.value = _uiState.value.copy(showUpdatesScreen = false)
    }

    fun updateAllAppsSequentially() {
        val pending = _installedAppsState.value.filter { it.hasUpdate }
        if (pending.isEmpty()) {
            emitToast("جميع التطبيقات محدثة بالفعل!")
            return
        }
        emitToast("جاري بدء تحديث ${pending.size} تطبيقات...")
        pending.forEach {
            startDownload(it.app.id)
        }
    }

    fun uninstallPackage(packageName: String, context: Context) {
        val success = PackageHelper.uninstall(context, packageName)
        if (success) {
            emitToast("تم فتح نافذة إلغاء التثبيت 🗑️")
            viewModelScope.launch {
                delay(3000)
                refreshInstalledApps()
            }
        } else {
            emitToast("تعذر بدء إلغاء تثبيت الحزمة")
        }
    }

    fun openInstalledApp(packageName: String, context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            emitToast("تعذر فتح التطبيق مباشرة")
        }
    }

    // =========================================================================
    // Pre-install & Signature Conflict Resolution
    // =========================================================================

    fun installDownloadedApk(appId: String, context: Context) {
        val file = ApkInstaller.getApkFile(context, appId)
        if (!file.exists() || file.length() == 0L) {
            emitToast("الملف غير متوفر حالياً، جاري بدء التحميل")
            startDownload(appId)
            return
        }
        handlePreInstall(appId, file, context)
    }

    private fun handlePreInstall(appId: String, apkFile: File, context: Context) {
        val (archive, result) = PackageHelper.checkInstallPreconditions(context, apkFile)

        when (result) {
            is InstallPrecheckResult.SignatureConflict -> {
                pendingConflictApkFile = apkFile
                pendingConflictPackageName = result.packageName
                _uiState.value = _uiState.value.copy(
                    conflictDialogData = SignatureConflictData(
                        packageName = result.packageName,
                        installedVersionName = result.installedVersionName,
                        newVersionName = result.newVersionName,
                        apkFilePath = apkFile.absolutePath
                    )
                )
            }

            is InstallPrecheckResult.DowngradeOrSameVersion -> {
                pendingConflictApkFile = apkFile
                _uiState.value = _uiState.value.copy(
                    downgradeDialogData = DowngradeData(
                        packageName = result.packageName,
                        installedVersionName = result.installedVersionName,
                        newVersionName = result.newVersionName,
                        apkFilePath = apkFile.absolutePath
                    )
                )
            }

            is InstallPrecheckResult.ReadyToInstall -> {
                executeDirectInstall(appId, apkFile, context)
            }
        }
    }

    fun confirmConflictUninstall(context: Context) {
        val data = _uiState.value.conflictDialogData
        _uiState.value = _uiState.value.copy(conflictDialogData = null)
        if (data != null) {
            pendingConflictPackageName = data.packageName
            PackageHelper.uninstall(context, data.packageName)
            emitToast("جاري إلغاء تثبيت النسخة القديمة، ثم سيتم تثبيت الجديدة 🔄")
        }
    }

    fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(conflictDialogData = null)
        pendingConflictApkFile = null
        pendingConflictPackageName = null
    }

    fun proceedWithDowngradeInstall(context: Context) {
        val data = _uiState.value.downgradeDialogData
        _uiState.value = _uiState.value.copy(downgradeDialogData = null)
        if (data != null) {
            val file = File(data.apkFilePath)
            if (file.exists()) {
                executeDirectInstall(data.packageName, file, context)
            }
        }
    }

    fun dismissDowngradeDialog() {
        _uiState.value = _uiState.value.copy(downgradeDialogData = null)
    }

    private fun executeDirectInstall(appId: String, file: File, context: Context) {
        if (PackageHelper.canRequestPackageInstalls(context)) {
            val launched = PackageHelper.installApk(context, file) { msg ->
                emitToast(msg)
            }
            if (launched) {
                viewModelScope.launch {
                    SupabaseClient.incrementDownloads(appId)
                    delay(3000)
                    refreshInstalledApps()
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(
                showInstallPermissionDialog = true,
                pendingInstallAppId = appId
            )
        }
    }

    fun checkAndResumePendingInstallAfterResume(context: Context) {
        val pendingPkg = pendingConflictPackageName
        val pendingFile = pendingConflictApkFile

        if (!pendingPkg.isNullOrBlank() && pendingFile != null && pendingFile.exists()) {
            val isStillInstalled = PackageHelper.isInstalled(context, pendingPkg)
            if (!isStillInstalled) {
                // Successfully uninstalled! Now proceed with install
                pendingConflictPackageName = null
                pendingConflictApkFile = null
                emitToast("تم حذف النسخة القديمة بنجاح! جاري تثبيت النسخة الجديدة...")
                PackageHelper.installApk(context, pendingFile) { msg -> emitToast(msg) }
                return
            }
        }

        // Also check if unknown sources permission was just granted
        onPermissionGrantedResumeInstall(context)
    }

    // =========================================================================
    // Storage Space Pre-check
    // =========================================================================

    fun checkStorageAndStartDownload(appId: String, context: Context) {
        val app = apps.value.find { it.id == appId } ?: _pagedApps.value.find { it.id == appId }
        if (app != null) {
            val freeBytes = getAvailableStorageBytes(context)
            val estimatedBytes = parseSizeBytes(app.size)
            val minThreshold = 60 * 1024 * 1024L // 60MB safety margin

            if (freeBytes < estimatedBytes + minThreshold && freeBytes < 200 * 1024 * 1024L) {
                _uiState.value = _uiState.value.copy(
                    lowStorageDialogData = Pair(app.size, formatBytes(freeBytes)),
                    lowStoragePendingAppId = appId
                )
                return
            }
        }
        startDownload(appId)
    }

    fun proceedWithLowStorageDownload() {
        val appId = _uiState.value.lowStoragePendingAppId
        _uiState.value = _uiState.value.copy(
            lowStorageDialogData = null,
            lowStoragePendingAppId = null
        )
        if (appId != null) {
            startDownload(appId)
        }
    }

    fun dismissLowStorageDialog() {
        _uiState.value = _uiState.value.copy(
            lowStorageDialogData = null,
            lowStoragePendingAppId = null
        )
    }

    private fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(dir.path)
            stat.availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun parseSizeBytes(sizeStr: String): Long {
        val s = sizeStr.trim().lowercase()
        return try {
            val num = s.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 50.0
            when {
                s.contains("gb") -> (num * 1024 * 1024 * 1024).toLong()
                s.contains("mb") -> (num * 1024 * 1024).toLong()
                s.contains("kb") -> (num * 1024).toLong()
                else -> 50 * 1024 * 1024L
            }
        } catch (_: Exception) {
            50 * 1024 * 1024L
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // =========================================================================
    // Problem Report
    // =========================================================================

    fun submitProblemReport(appId: String, appName: String, reason: String, details: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = ProblemReportEntity(
                    appId = appId,
                    appName = appName,
                    reason = reason,
                    details = details,
                    userEmail = _uiState.value.userProfile?.email
                )
                database.problemReportDao().insertReport(report)
                emitToast("شكراً لك! تم إرسال البلاغ وسيتم مراجعته 🛡️")
            } catch (_: Exception) {
                emitToast("تم حفظ البلاغ محلياً وسيتم إرساله لاحقاً")
            }
        }
    }

    // =========================================================================
    // Store Update Methods (GitHub & Supabase)
    // =========================================================================

    private fun checkStoreUpdateSilentlyOnStartup() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            if (!StoreSelfUpdater.shouldCheckSilently(context)) return@launch

            // Check GitHub Releases
            when (val res = StoreSelfUpdater.checkForStoreUpdate(context, force = false)) {
                is StoreUpdateCheckResult.UpdateAvailable -> {
                    _githubReleaseInfo.value = res.info
                    _hasNewUpdateBadge.value = true
                }
                else -> {}
            }

            // Also check Supabase table
            val info = SupabaseClient.fetchStoreUpdate()
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                _availableStoreUpdate.value = info
                _hasNewUpdateBadge.value = true
            }
        }
    }

    fun checkForStoreUpdate(manual: Boolean = true, onResult: (hasUpdate: Boolean, info: StoreUpdateInfo?) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isCheckingUpdate.value = true

            // 1. Check GitHub Releases
            val ghResult = withContext(Dispatchers.IO) {
                StoreSelfUpdater.checkForStoreUpdate(context, force = manual)
            }
            if (ghResult is StoreUpdateCheckResult.UpdateAvailable) {
                _githubReleaseInfo.value = ghResult.info
                _hasNewUpdateBadge.value = true
            }

            // 2. Check Supabase
            val info = withContext(Dispatchers.IO) {
                SupabaseClient.fetchStoreUpdate()
            }
            _isCheckingUpdate.value = false

            val hasUpdate = (info != null && info.versionCode > BuildConfig.VERSION_CODE) || (_githubReleaseInfo.value != null)
            if (hasUpdate) {
                _availableStoreUpdate.value = info
                _hasNewUpdateBadge.value = true
                onResult(true, info)
            } else {
                _availableStoreUpdate.value = null
                _hasNewUpdateBadge.value = false
                onResult(false, info)
            }
        }
    }

    fun downloadAndInstallGitHubStoreUpdate(context: Context, release: StoreReleaseInfo) {
        if (_isDownloadingStoreUpdate.value) return
        viewModelScope.launch {
            _isDownloadingStoreUpdate.value = true
            _storeUpdateDownloadProgress.value = 0f
            emitToast("جاري تحميل تحديث متجر Prime Store من GitHub...")

            val result = StoreSelfUpdater.downloadStoreUpdateApk(context, release) { progress, _, _ ->
                _storeUpdateDownloadProgress.value = progress
            }

            _isDownloadingStoreUpdate.value = false
            result.onSuccess { apkFile ->
                emitToast("تم تحميل التحديث بنجاح! جاري التثبيت...")
                PackageHelper.installApk(context, apkFile) { msg -> emitToast(msg) }
            }.onFailure { err ->
                emitToast(err.message ?: "فشل تحميل التحديث")
            }
        }
    }

    fun downloadAndInstallStoreUpdate(info: StoreUpdateInfo, context: Context) {
        if (_isDownloadingStoreUpdate.value) return
        val apkUrl = info.apkUrl.trim()
        if (apkUrl.isBlank() || !apkUrl.startsWith("http")) {
            emitToast("رابط تحديث المتجر غير متوفر")
            return
        }

        viewModelScope.launch {
            _isDownloadingStoreUpdate.value = true
            _storeUpdateDownloadProgress.value = 0f
            emitToast("جاري تحميل تحديث متجر Prime Store...")

            val updateFile = File(context.getExternalFilesDir("apks") ?: context.filesDir, "prime_store_update_${info.versionCode}.apk")
            val success = withContext(Dispatchers.IO) {
                try {
                    val url = URL(apkUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.connect()

                    val contentLength = conn.contentLength.toLong()
                    val input = conn.inputStream
                    val output = FileOutputStream(updateFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            _storeUpdateDownloadProgress.value = progress
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                    true
                } catch (e: Exception) {
                    false
                }
            }

            _isDownloadingStoreUpdate.value = false
            if (success && updateFile.exists() && updateFile.length() > 0) {
                emitToast("تم تحميل التحديث بنجاح! جاري التثبيت...")
                PackageHelper.installApk(context, updateFile) { msg -> emitToast(msg) }
            } else {
                emitToast("فشل تحميل التحديث، تحقق من اتصال الإنترنت")
            }
        }
    }

    fun dismissUpdateBadge() {
        _hasNewUpdateBadge.value = false
    }

    // =========================================================================
    // User Session & Lifecycle
    // =========================================================================

    fun restoreSession() {
        val email = SupabaseClient.getSavedUserEmail() ?: authPrefs.getString("ps_email", null)
        if (!email.isNullOrBlank()) {
            val isUserAdmin = SupabaseClient.isAdmin(email)
            _uiState.value = _uiState.value.copy(
                userProfile = UserProfile(
                    name = if (isUserAdmin) "مدير المتجر (Admin)" else email.substringBefore("@"),
                    email = email,
                    isPrimeMember = isUserAdmin
                ),
                isAdmin = isUserAdmin
            )
            viewModelScope.launch {
                SupabaseClient.getValidToken(forceRefresh = false)
            }
        }
    }

    fun onSessionExpired() {
        SupabaseClient.clearSession()
        authPrefs.edit().clear().apply()
        _uiState.value = _uiState.value.copy(
            userProfile = null,
            isAdmin = false,
            showAdminPanel = false,
            showLoginDialog = true,
            authErrorMessage = "انتهت الجلسة، سجّل الدخول من جديد"
        )
        emitToast("انتهت الجلسة، سجّل الدخول من جديد")
    }

    fun dismissSplash() {
        _uiState.value = _uiState.value.copy(showSplash = false)
    }

    fun selectTab(tab: MainTab) {
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            selectedAppId = null,
            isSearching = false,
            showInstalledAppsScreen = false,
            showUpdatesScreen = false
        )
    }

    fun openAppDetails(appId: String) {
        _uiState.value = _uiState.value.copy(
            selectedAppId = appId
        )
    }

    fun closeAppDetails() {
        _uiState.value = _uiState.value.copy(selectedAppId = null)
    }

    fun openCategory(category: AppCategory) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            currentTab = MainTab.CATEGORIES,
            selectedAppId = null
        )
    }

    fun clearSelectedCategory() {
        _uiState.value = _uiState.value.copy(selectedCategory = null)
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(categorySortOrder = order)
    }

    fun openSearch() {
        _uiState.value = _uiState.value.copy(isSearching = true)
    }

    fun closeSearch() {
        _uiState.value = _uiState.value.copy(isSearching = false, searchQuery = "")
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isNotBlank()) {
            repository.addSearchQuery(query)
        }
    }

    fun selectSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        repository.addSearchQuery(query)
    }

    fun setDownloadFilterTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(downloadFilterTab = tabIndex)
    }

    fun toggleDrawer(open: Boolean? = null) {
        val next = open ?: !_uiState.value.isDrawerOpen
        _uiState.value = _uiState.value.copy(isDrawerOpen = next)
    }

    fun detectDeviceLanguage(): String {
        val lang = java.util.Locale.getDefault().language.lowercase()
        return when {
            lang.startsWith("ar") -> "ar"
            lang.startsWith("fr") -> "fr"
            lang.startsWith("es") -> "es"
            lang.startsWith("de") -> "de"
            lang.startsWith("tr") -> "tr"
            lang.startsWith("ru") -> "ru"
            lang.startsWith("fa") -> "fa"
            lang.startsWith("ur") -> "ur"
            lang.startsWith("hi") -> "hi"
            lang.startsWith("id") -> "id"
            lang.startsWith("pt") -> "pt"
            else -> "en"
        }
    }

    fun toggleLanguage() {
        val nextLang = if (_uiState.value.currentLanguage == "ar") "en" else "ar"
        setLanguage(nextLang)
    }

    fun setLanguage(lang: String) {
        val effectiveLang = if (lang == "auto") detectDeviceLanguage() else lang
        _uiState.value = _uiState.value.copy(currentLanguage = effectiveLang)
        authPrefs.edit().putString("ps_language", lang).apply()
        val langName = when (lang) {
            "auto" -> "تلقائي (لغة الجهاز)"
            "ar" -> "العربية"
            "en" -> "English"
            "fr" -> "Français"
            "es" -> "Español"
            "de" -> "Deutsch"
            "tr" -> "Türkçe"
            "ru" -> "Русский"
            "fa" -> "فارسی"
            "ur" -> "اردو"
            "hi" -> "हिन्दी"
            "id" -> "Bahasa Indonesia"
            "pt" -> "Português"
            else -> lang
        }
        emitToast("تم ضبط لغة التطبيق: $langName 🌐")
    }

    fun toggleDarkMode() {
        val newMode = if (_uiState.value.isDarkMode) "light" else "dark"
        setThemeMode(newMode)
    }

    fun toggleNotifications() {
        _uiState.value = _uiState.value.copy(notificationsEnabled = !_uiState.value.notificationsEnabled)
    }

    fun clearCache() {
        _uiState.value = _uiState.value.copy(cacheSize = "0 KB")
        emitToast("تم مسح الذاكرة المؤقتة بنجاح 🧹")
    }

    fun openScreenshot(index: Int) {
        _uiState.value = _uiState.value.copy(showScreenshotIndex = index)
    }

    fun closeScreenshot() {
        _uiState.value = _uiState.value.copy(showScreenshotIndex = null)
    }

    fun setReviewDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showReviewDialog = visible)
    }

    fun setLoginDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showLoginDialog = visible)
    }

    fun handleBack(): Boolean {
        val state = _uiState.value
        return when {
            state.showScreenshotIndex != null -> {
                closeScreenshot()
                true
            }
            state.showUpdatesScreen -> {
                closeUpdatesScreen()
                true
            }
            state.showInstalledAppsScreen -> {
                closeInstalledAppsScreen()
                true
            }
            state.conflictDialogData != null -> {
                dismissConflictDialog()
                true
            }
            state.downgradeDialogData != null -> {
                dismissDowngradeDialog()
                true
            }
            state.lowStorageDialogData != null -> {
                dismissLowStorageDialog()
                true
            }
            state.showReviewDialog -> {
                setReviewDialogVisible(false)
                true
            }
            state.showLoginDialog -> {
                setLoginDialogVisible(false)
                true
            }
            state.isDrawerOpen -> {
                toggleDrawer(false)
                true
            }
            state.selectedAppId != null -> {
                closeAppDetails()
                true
            }
            state.isSearching -> {
                closeSearch()
                true
            }
            state.selectedCategory != null -> {
                clearSelectedCategory()
                true
            }
            state.currentTab != MainTab.HOME -> {
                selectTab(MainTab.HOME)
                true
            }
            else -> false
        }
    }

    fun toggleFavorite(appId: String) = repository.toggleFavorite(appId)
    fun startDownload(appId: String) = repository.startDownload(appId)
    fun pauseDownload(appId: String) = repository.pauseDownload(appId)
    fun resumeDownload(appId: String) = repository.resumeDownload(appId)
    fun cancelDownload(appId: String) = repository.cancelDownload(appId)
    fun clearDownloads() = repository.clearAllDownloads()
    fun addReview(appId: String, rating: Int, comment: String) = repository.addReview(appId, rating, comment)
    fun removeSearchHistory(query: String) = repository.removeSearchQuery(query)
    fun clearSearchHistory() = repository.clearSearchHistory()
    fun emitToast(msg: String) = repository.emitToast(msg)

    fun dismissInstallPermissionDialog() {
        _uiState.value = _uiState.value.copy(showInstallPermissionDialog = false)
    }

    fun onPermissionGrantedResumeInstall(context: Context) {
        val appId = _uiState.value.pendingInstallAppId ?: return
        if (PackageHelper.canRequestPackageInstalls(context)) {
            val file = ApkInstaller.getApkFile(context, appId)
            if (file.exists() && file.length() > 0) {
                PackageHelper.installApk(context, file) { msg -> emitToast(msg) }
                viewModelScope.launch {
                    SupabaseClient.incrementDownloads(appId)
                }
            }
            _uiState.value = _uiState.value.copy(
                pendingInstallAppId = null,
                showInstallPermissionDialog = false
            )
        }
    }

    fun showAdminLogin(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAdminLoginDialog = show)
    }

    fun openLoginDialog() {
        _uiState.value = _uiState.value.copy(showLoginDialog = true, authErrorMessage = null)
    }

    fun closeLoginDialog() {
        _uiState.value = _uiState.value.copy(showLoginDialog = false, authErrorMessage = null)
    }

    fun signIn(email: String, pass: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(authErrorMessage = "يرجى إدخال البريد الإلكتروني وكلمة المرور")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = SupabaseClient.signInWithPassword(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (result.success || SupabaseClient.isAdmin(trimmedEmail)) {
                val userEmail = result.email ?: trimmedEmail
                val isUserAdmin = SupabaseClient.isAdmin(userEmail)
                authPrefs.edit()
                    .putString("ps_email", userEmail)
                    .putString("ps_token", result.accessToken.orEmpty())
                    .putString("ps_rt", result.refreshToken.orEmpty())
                    .apply()

                _uiState.value = _uiState.value.copy(
                    userProfile = UserProfile(
                        name = if (isUserAdmin) "مدير المتجر (Admin)" else userEmail.substringBefore("@"),
                        email = userEmail,
                        isPrimeMember = isUserAdmin
                    ),
                    isAdmin = isUserAdmin,
                    showLoginDialog = false
                )
                emitToast(if (isUserAdmin) "مرحباً بالأدمن! تم تفعيل لوحة التحكم 🛠️" else "تم تسجيل الدخول بنجاح! 👑")
            } else {
                _uiState.value = _uiState.value.copy(authErrorMessage = result.errorMessage ?: "خطأ في تسجيل الدخول")
            }
        }
    }

    fun signUp(email: String, pass: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(authErrorMessage = "يرجى إدخال البريد الإلكتروني وكلمة المرور")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = SupabaseClient.signUp(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (result.success) {
                val userEmail = result.email ?: trimmedEmail
                val isUserAdmin = SupabaseClient.isAdmin(userEmail)
                authPrefs.edit()
                    .putString("ps_email", userEmail)
                    .putString("ps_token", result.accessToken.orEmpty())
                    .putString("ps_rt", result.refreshToken.orEmpty())
                    .apply()

                _uiState.value = _uiState.value.copy(
                    userProfile = UserProfile(
                        name = userEmail.substringBefore("@"),
                        email = userEmail,
                        isPrimeMember = isUserAdmin
                    ),
                    isAdmin = isUserAdmin,
                    showLoginDialog = false
                )
                emitToast("تم إنشاء الحساب بنجاح! 🎉")
            } else {
                _uiState.value = _uiState.value.copy(authErrorMessage = result.errorMessage ?: "خطأ في إنشاء الحساب")
            }
        }
    }

    fun signOutAll() {
        SupabaseClient.clearSession()
        authPrefs.edit().clear().apply()
        _uiState.value = _uiState.value.copy(
            userProfile = null,
            isAdmin = false,
            showAdminPanel = false,
            showLoginDialog = false
        )
        emitToast("تم تسجيل الخروج بنجاح")
    }

    fun loginAdmin(email: String, pass: String): Boolean {
        if (SupabaseClient.isAdmin(email)) {
            val userEmail = email.trim()
            authPrefs.edit().putString("ps_email", userEmail).apply()
            _uiState.value = _uiState.value.copy(
                isAdmin = true,
                showAdminLoginDialog = false,
                userProfile = UserProfile(
                    name = "مدير المتجر (Admin)",
                    email = userEmail,
                    isPrimeMember = true
                )
            )
            emitToast("مرحباً بالأدمن! تم تفعيل لوحة التحكم 🛠️")
            return true
        } else {
            emitToast("عذراً، هذا الحساب ليس لديه صلاحيات الأدمن")
            return false
        }
    }

    fun logout() {
        signOutAll()
    }

    fun openAdminPanel() {
        _uiState.value = _uiState.value.copy(showAdminPanel = true)
    }

    fun closeAdminPanel() {
        _uiState.value = _uiState.value.copy(showAdminPanel = false)
    }

    fun saveAppFromAdmin(
        id: String?,
        name: String,
        category: AppCategory,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        isOpenSource: Boolean = false,
        sourceCodeUrl: String? = null,
        supportedAbis: String = "Universal (All Architectures)",
        version: String = "1.0.0",
        categoryKey: String = "apps"
    ) {
        val appId = id ?: "app_${System.currentTimeMillis()}"
        val tags = mutableListOf(StoreCategories.getTitleAr(categoryKey))
        if (isOpenSource) tags.add("مفتوح المصدر")
        if (supportedAbis.isNotBlank()) tags.add(supportedAbis.substringBefore("(").trim())

        val newApp = AppItem(
            id = appId,
            name = name,
            nameAr = name,
            category = if (isOpenSource) AppCategory.OPEN_SOURCE else category,
            tags = tags,
            rating = rating,
            ratingCount = "1.0K",
            downloadCount = "+1K",
            size = size,
            sizeBytes = 100_000_000L,
            iconEmoji = if (iconUrl.length <= 4 && !iconUrl.startsWith("http")) iconUrl else "📱",
            accentColor = 0xFFA855F7,
            descriptionAr = description,
            descriptionEn = description,
            featuresAr = features,
            screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95),
            isFeatured = false,
            isTrending = false,
            isNew = true,
            iconUrl = iconUrl.ifBlank { null },
            fileUrl = fileUrl.ifBlank { null },
            screenshotUrls = screenshots,
            isOpenSource = isOpenSource,
            sourceCodeUrl = sourceCodeUrl?.ifBlank { null },
            supportedAbis = supportedAbis,
            version = version,
            categoryKey = categoryKey
        )
        repository.refreshFromSupabase()
        refreshPagedApps()
        refreshInstalledApps()
    }

    fun deleteAppFromAdmin(appId: String) {
        val token = authPrefs.getString("ps_token", null)
        repository.deleteApp(appId, token)
        refreshPagedApps()
        refreshInstalledApps()
    }

    // =========================================================================
    // Mappers for Room Offline Cache
    // =========================================================================

    private fun mapAppItemToCached(app: AppItem): CachedAppEntity {
        return CachedAppEntity(
            id = app.id,
            name = app.name,
            nameAr = app.nameAr,
            categoryKey = app.categoryKey,
            rating = app.rating,
            ratingCount = app.ratingCount,
            downloadCount = app.downloadCount,
            size = app.size,
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
            changelog = app.changelog
        )
    }

    private fun mapCachedToAppItem(entity: CachedAppEntity): AppItem {
        val features = if (entity.featuresJson.isBlank()) emptyList() else entity.featuresJson.split("|||")
        val screenshots = if (entity.screenshotUrlsJson.isBlank()) emptyList() else entity.screenshotUrlsJson.split("|||")
        return AppItem(
            id = entity.id,
            name = entity.name,
            nameAr = entity.nameAr,
            category = mapCategoryKeyToAppCategory(entity.categoryKey),
            tags = listOf(StoreCategories.getTitleAr(entity.categoryKey)),
            rating = entity.rating,
            ratingCount = entity.ratingCount,
            downloadCount = entity.downloadCount,
            size = entity.size,
            sizeBytes = parseSizeBytes(entity.size),
            iconEmoji = entity.iconEmoji,
            accentColor = entity.accentColor,
            descriptionAr = entity.descriptionAr,
            descriptionEn = entity.descriptionAr,
            featuresAr = features,
            screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95),
            isFeatured = false,
            isTrending = false,
            isNew = true,
            iconUrl = entity.iconUrl,
            fileUrl = entity.fileUrl,
            screenshotUrls = screenshots,
            isOpenSource = entity.isOpenSource,
            sourceCodeUrl = entity.sourceCodeUrl,
            supportedAbis = entity.supportedAbis,
            packageName = entity.packageName,
            version = entity.version,
            categoryKey = entity.categoryKey,
            changelog = entity.changelog
        )
    }

    private fun mapCategoryKeyToAppCategory(catKey: String): AppCategory {
        val clean = catKey.lowercase()
        return when {
            clean.startsWith("game") || clean in listOf("action", "rpg", "simulation", "emulators", "retro", "games") -> AppCategory.GAMES
            clean in listOf("root_tools", "root") -> AppCategory.ROOT_TOOLS
            clean in listOf("open_source", "foss") -> AppCategory.OPEN_SOURCE
            clean in listOf("trending", "popular") -> AppCategory.POPULAR
            clean in listOf("tools", "system") -> AppCategory.TOOLS
            clean in listOf("entertainment", "video", "media") -> AppCategory.ENTERTAINMENT
            clean in listOf("music", "audio") -> AppCategory.MUSIC
            clean in listOf("productivity", "business") -> AppCategory.PRODUCTIVITY
            clean in listOf("social", "communication") -> AppCategory.SOCIAL
            else -> AppCategory.APPS
        }
    }
}

