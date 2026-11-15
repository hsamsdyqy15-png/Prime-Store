package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.StatFs
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.github.AdminPublishCoordinator
import com.example.data.github.AdminPublishState
import com.example.data.github.GitHubStorageHelper
import com.example.data.github.GitHubTokenProvider
import com.example.data.github.GitHubTokenStatusSnapshot
import com.example.data.github.StoreReleaseInfo
import com.example.data.github.StoreSelfUpdater
import com.example.data.github.StoreUpdateCheckResult
import com.example.data.installer.ApkInstaller
import com.example.data.installer.InstallPrecheckResult
import com.example.data.installer.PackageEventBus
import com.example.data.installer.PackageHelper
import com.example.data.installer.PackageInstallEvent
import com.example.data.local.CategoryEntity
import com.example.data.local.PrimeStoreDatabase
import com.example.data.local.ProblemReportEntity
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.ReviewItem
import com.example.data.model.StoreCategories
import com.example.data.model.StoreUpdateInfo
import com.example.data.repository.AppsRepository
import com.example.data.repository.CategoriesRepository
import com.example.data.repository.PrimeStoreRepository
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import com.example.data.sync.SyncManager
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

enum class HomeSection {
    GAMES,
    APPS
}

sealed interface CategoryDestination {
    data object AllAppsHub : CategoryDestination
    data object AllGamesHub : CategoryDestination
    data class SingleCategory(
        val category: AppCategory,
        val categoryItem: CategoryItem? = null
    ) : CategoryDestination
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
    val selectedLanguageTag: String = "auto",
    val currentLanguage: String = "en",
    val themeMode: String = "dark", // "system", "dark", "amoled", "light"
    val themeAccent: Long = 0xFFA855F7, // Neon purple
    val autoTranslateEnabled: Boolean = true,
    val wifiOnlyTranslation: Boolean = false,
    val isDarkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val deleteApkAfterInstall: Boolean = true,
    val cacheSize: String = "0 KB",
    val apksSize: String = "0 KB",
    val downloadFilterTab: Int = 0,
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
    private val syncManager = SyncManager.getInstance(application)
    private val categoriesRepository = CategoriesRepository.getInstance(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val apps: StateFlow<List<AppItem>> = repository.apps
    val favorites: StateFlow<Set<String>> = repository.favorites
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val reviews: StateFlow<Map<String, List<ReviewItem>>> = repository.reviews
    val searchHistory: StateFlow<List<String>> = repository.searchHistory
    val toastEvents = repository.toastEvents
    val githubTokenStatus: StateFlow<GitHubTokenStatusSnapshot> = GitHubTokenProvider.status
    val adminPublishState: StateFlow<AdminPublishState> = AdminPublishCoordinator.state
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
    val newAppsAddedCount: StateFlow<Int> = syncManager.newAppsAddedCount
    val lastSyncTimeMillis: StateFlow<Long> = syncManager.lastSyncTimeMillis
    val lastSyncResult: StateFlow<String> = syncManager.lastSyncResult

    val visibleGameCategories: StateFlow<List<CategoryEntity>> = categoriesRepository.visibleGameCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val visibleAppCategories: StateFlow<List<CategoryEntity>> = categoriesRepository.visibleAppCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allGameCategories: StateFlow<List<CategoryEntity>> = categoriesRepository.allGameCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAppCategories: StateFlow<List<CategoryEntity>> = categoriesRepository.allAppCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _homeSection = MutableStateFlow(HomeSection.GAMES)
    val homeSection: StateFlow<HomeSection> = _homeSection.asStateFlow()

    private val _searchAllTabs = MutableStateFlow(false)
    val searchAllTabs: StateFlow<Boolean> = _searchAllTabs.asStateFlow()

    private var pendingReturnToAdminAfterLogin: Boolean = false

    // =========================================================================
    // 1) Installed Apps, Downloaded APKs & Updates State (Precomputed on IO)
    // =========================================================================
    private val _installedAppsState = MutableStateFlow<List<InstalledAppItemState>>(emptyList())
    val installedAppsState: StateFlow<List<InstalledAppItemState>> = _installedAppsState.asStateFlow()

    private val _installedPkgByAppId = MutableStateFlow<Map<String, InstalledPackageSummary>>(emptyMap())
    val installedPkgByAppId: StateFlow<Map<String, InstalledPackageSummary>> = _installedPkgByAppId.asStateFlow()

    private val _downloadedReadyIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedReadyIds: StateFlow<Set<String>> = _downloadedReadyIds.asStateFlow()

    private val _installingAppIds = MutableStateFlow<Set<String>>(emptySet())
    val installingAppIds: StateFlow<Set<String>> = _installingAppIds.asStateFlow()

    private val _signatureConflictAppIds = MutableStateFlow<Set<String>>(emptySet())
    val signatureConflictAppIds: StateFlow<Set<String>> = _signatureConflictAppIds.asStateFlow()

    private val _isRefreshingInstalledApps = MutableStateFlow(false)
    val isRefreshingInstalledApps: StateFlow<Boolean> = _isRefreshingInstalledApps.asStateFlow()

    private val _isPullRefreshing = MutableStateFlow(false)
    val isPullRefreshing: StateFlow<Boolean> = _isPullRefreshing.asStateFlow()

    // Category Hub navigation & filter state
    private val _categoryDestination = MutableStateFlow<CategoryDestination?>(null)
    val categoryDestination: StateFlow<CategoryDestination?> = _categoryDestination.asStateFlow()

    private val _selectedHubFilterKey = MutableStateFlow("all")
    val selectedHubFilterKey: StateFlow<String> = _selectedHubFilterKey.asStateFlow()

    // Conflict post-uninstall install continuation
    private var pendingConflictApkFile: File? = null
    private var pendingConflictPackageName: String? = null
    private var pendingConflictAppId: String? = null
    private val resolvedPkgWriteCache = HashSet<String>()
    private var isPackageReceiverRegistered = false

    private val packageBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart?.trim()
            if (!pkg.isNullOrBlank()) {
                val matchingIds = apps.value
                    .filter {
                        it.packageName.equals(pkg, ignoreCase = true) ||
                            _installedPkgByAppId.value[it.id]?.packageName.equals(pkg, ignoreCase = true)
                    }
                    .map { it.id }
                    .toSet()
                if (matchingIds.isNotEmpty()) {
                    _installingAppIds.value = _installingAppIds.value - matchingIds
                    _signatureConflictAppIds.value = _signatureConflictAppIds.value - matchingIds
                }
            }
            refreshInstalledApps()
            refreshDownloadedReadyIds()
            refreshStorageStats()
            context?.let { checkAndResumePendingInstallAfterResume(it) }
        }
    }

    // =========================================================================
    // 2) Unified Room-backed Home Feed & Infinite Display Window
    // =========================================================================
    private val pageSize = 20
    private val _selectedCategoryKey = MutableStateFlow("all")
    val selectedCategoryKey: StateFlow<String> = _selectedCategoryKey.asStateFlow()

    private val _homeDisplayLimit = MutableStateFlow(pageSize)

    private val filteredHomeCatalog = combine(apps, _homeSection, _selectedCategoryKey, _uiState) { allApps, section, catKey, state ->
        val requiredType = if (section == HomeSection.GAMES) {
            StoreCategories.ITEM_TYPE_GAME
        } else {
            StoreCategories.ITEM_TYPE_APP
        }
        val sectionFiltered = if (section == HomeSection.GAMES) {
            allApps.filter { StoreCategories.isGameApp(it) }
        } else {
            allApps.filter { StoreCategories.isNonGameApp(it) }
        }
        val categoryFiltered = if (catKey.equals("all", ignoreCase = true)) {
            sectionFiltered
        } else {
            sectionFiltered.filter { StoreCategories.matchesCategoryFilter(it, catKey, requiredType) }
        }
        when (state.homeFilterType) {
            HomeFilterType.ALL -> categoryFiltered
            HomeFilterType.SMALL_SIZE -> categoryFiltered.filter { app ->
                if (app.sizeBytes > 0L) {
                    app.sizeBytes < 35L * 1024L * 1024L
                } else {
                    val sizeClean = app.size.trim().lowercase()
                    sizeClean.contains("kb") || (sizeClean.contains("mb") && (sizeClean.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 100.0) < 35.0)
                }
            }
            HomeFilterType.TOP_RATED -> categoryFiltered.sortedByDescending { it.rating }
            HomeFilterType.NEWEST -> categoryFiltered.sortedByDescending {
                maxOf(it.updatedAtMillis, it.createdAtMillis)
            }
        }
    }

    val pagedApps: StateFlow<List<AppItem>> = combine(filteredHomeCatalog, _homeDisplayLimit) { filtered, limit ->
        filtered.take(limit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isInitialLoading: StateFlow<Boolean> = combine(syncManager.isSyncing, apps) { syncing, allApps ->
        syncing && allApps.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isLoadingNextPage = MutableStateFlow(false)
    val isLoadingNextPage: StateFlow<Boolean> = _isLoadingNextPage.asStateFlow()

    val hasMoreApps: StateFlow<Boolean> = combine(filteredHomeCatalog, _homeDisplayLimit) { filtered, limit ->
        filtered.size > limit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _pagedError = MutableStateFlow<String?>(null)
    val pagedError: StateFlow<String?> = _pagedError.asStateFlow()

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
    // Search (scoped to active Home tab unless "Search All" is toggled)
    // =========================================================================
    val searchResults: StateFlow<List<AppItem>> = combine(apps, _uiState, _homeSection, _searchAllTabs) { allApps, state, section, searchAll ->
        val q = state.searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            emptyList()
        } else {
            val appCtx = getApplication<Application>()
            val scopedApps = when {
                searchAll -> allApps
                section == HomeSection.GAMES -> allApps.filter { StoreCategories.isGameApp(it) }
                else -> allApps.filter { StoreCategories.isNonGameApp(it) }
            }
            scopedApps.filter {
                it.name.lowercase().contains(q) ||
                        it.nameAr.contains(q) ||
                        StoreCategories.getTitle(appCtx, it.categoryKey).lowercase().contains(q) ||
                        StoreCategories.find(it.categoryKey, it.itemType).titleEn.lowercase().contains(q) ||
                        it.displayCategoryTitle.lowercase().contains(q) ||
                        it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val authPrefs = application.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)
    private val settingsRepo = com.example.data.repository.SettingsRepository.getInstance(application)
    val shizukuRootInstallEnabled: StateFlow<Boolean> = settingsRepo.shizukuRootInstallEnabled

    init {
        viewModelScope.launch(Dispatchers.IO) {
            categoriesRepository.ensureSeeded()
        }
        syncManager.attachToApplication(application)
        GitHubStorageHelper.purgeLegacyLocalToken(application.applicationContext)
        SupabaseClient.init(application.applicationContext)
        SupabaseClient.onSessionExpiredListener = { onSessionExpired() }
        restoreSession()

        // Language & translation setup
        val savedLang = settingsRepo.languageTag.value
        val activeLang = settingsRepo.getEffectiveLanguageCode()
        val autoTranslate = settingsRepo.autoTranslateEnabled.value
        val wifiOnly = settingsRepo.wifiOnlyTranslation.value

        // Theme setup
        val currentThemeMode = settingsRepo.themeMode.value
        val currentAccent = settingsRepo.accentHex.value
        val isFirstRun = !authPrefs.getBoolean("has_completed_onboarding", false)

        _uiState.value = _uiState.value.copy(
            selectedLanguageTag = savedLang,
            currentLanguage = activeLang,
            autoTranslateEnabled = autoTranslate,
            wifiOnlyTranslation = wifiOnly,
            themeMode = currentThemeMode.name.lowercase(),
            themeAccent = currentAccent,
            isDarkMode = currentThemeMode != com.example.ui.theme.ThemeMode.LIGHT,
            notificationsEnabled = settingsRepo.notificationsEnabled.value,
            deleteApkAfterInstall = settingsRepo.deleteApkAfterInstall.value,
            showOnboarding = isFirstRun
        )
        refreshStorageStats()

        // Register dynamic system broadcast receiver for instant install/uninstall updates
        try {
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                application,
                packageBroadcastReceiver,
                pkgFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isPackageReceiverRegistered = true
        } catch (e: Exception) {
            SecureLogger.w("PrimeStoreVM", "PKG_RECEIVER", "Failed to register package receiver: ${e.message}")
        }

        // Collect PackageEventBus install session outcomes
        viewModelScope.launch {
            PackageEventBus.events.collect { event ->
                val appCtx = getApplication<Application>()
                when (event) {
                    is PackageInstallEvent.Installing -> {
                        val ids = buildSet {
                            if (event.appId.isNotBlank()) add(event.appId)
                            apps.value.filter { it.packageName.equals(event.packageName, ignoreCase = true) }
                                .forEach { add(it.id) }
                        }
                        if (ids.isNotEmpty()) {
                            _installingAppIds.value = _installingAppIds.value + ids
                        }
                    }

                    is PackageInstallEvent.Success -> {
                        val ids = buildSet {
                            if (event.appId.isNotBlank()) add(event.appId)
                            apps.value.filter { it.packageName.equals(event.packageName, ignoreCase = true) }
                                .forEach { add(it.id) }
                        }
                        _installingAppIds.value = _installingAppIds.value - ids
                        _signatureConflictAppIds.value = _signatureConflictAppIds.value - ids

                        val matchedApp = apps.value.find {
                            it.id == event.appId ||
                                (event.packageName.isNotBlank() && it.packageName.equals(event.packageName, ignoreCase = true))
                        }
                        val displayName = matchedApp?.nameAr?.ifBlank { matchedApp.name }
                            ?: event.packageName.ifBlank { appCtx.getString(R.string.dl_service_default_app) }
                        emitToast(appCtx.getString(R.string.toast_app_installed_fmt, displayName))

                        if (settingsRepo.deleteApkAfterInstall.value && event.appId.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                ApkInstaller.cleanupAppInstallFiles(appCtx, event.appId)
                            }
                        }
                        refreshInstalledApps()
                        refreshDownloadedReadyIds()
                        refreshStorageStats()
                    }

                    is PackageInstallEvent.SignatureConflict -> {
                        val ids = buildSet {
                            if (event.appId.isNotBlank()) add(event.appId)
                            apps.value.filter { it.packageName.equals(event.packageName, ignoreCase = true) }
                                .forEach { add(it.id) }
                        }
                        _installingAppIds.value = _installingAppIds.value - ids
                        if (ids.isNotEmpty()) {
                            _signatureConflictAppIds.value = _signatureConflictAppIds.value + ids
                        }

                        if (event.appId.isNotBlank()) {
                            val apkFile = ApkInstaller.findExistingPrimaryInstallFile(appCtx, event.appId)
                                ?: ApkInstaller.getApkFile(appCtx, event.appId)
                            if (apkFile.exists() && _uiState.value.conflictDialogData == null) {
                                val archive = PackageHelper.getArchiveInfo(appCtx, apkFile)
                                val pkg = event.packageName.ifBlank { archive?.packageName.orEmpty() }
                                val installed = if (pkg.isNotBlank()) PackageHelper.getInstalledInfo(appCtx, pkg) else null
                                if (pkg.isNotBlank()) {
                                    pendingConflictApkFile = apkFile
                                    pendingConflictPackageName = pkg
                                    pendingConflictAppId = event.appId
                                    _uiState.value = _uiState.value.copy(
                                        conflictDialogData = SignatureConflictData(
                                            packageName = pkg,
                                            installedVersionName = installed?.versionName ?: "—",
                                            newVersionName = archive?.versionName ?: "—",
                                            apkFilePath = apkFile.absolutePath
                                        )
                                    )
                                }
                            }
                        }
                        refreshInstalledApps()
                    }

                    is PackageInstallEvent.Failure -> {
                        val ids = buildSet {
                            if (event.appId.isNotBlank()) add(event.appId)
                            apps.value.filter { it.packageName.equals(event.packageName, ignoreCase = true) }
                                .forEach { add(it.id) }
                        }
                        _installingAppIds.value = _installingAppIds.value - ids
                        if (event.message.isNotBlank()) {
                            emitToast(event.message)
                        }
                    }
                }
            }
        }

        // Install callback & local APK cache refresh when download finishes
        repository.onApkDownloadCompleted = { appId, apkFile ->
            val app = getApplication<Application>()
            refreshDownloadedReadyIds()
            refreshStorageStats()
            handlePreInstall(appId, apkFile, app)
        }
        repository.onLocalApkFilesChanged = {
            refreshDownloadedReadyIds()
            refreshStorageStats()
        }

        viewModelScope.launch {
            delay(1400)
            _uiState.value = _uiState.value.copy(showSplash = false)
        }

        // Welcome toast if updated
        StoreSelfUpdater.onAppStarted(application) { msg ->
            emitToast(msg)
        }

        // Start foreground live sync (delta metadata + 20s polling + Realtime WebSocket)
        syncManager.onForegroundStarted()

        // Auto-check store update on startup (max once every 6 hours)
        checkStoreUpdateSilentlyOnStartup()

        // Monitor apps & downloads on IO for installed packages & local APKs
        viewModelScope.launch {
            apps.collect {
                refreshInstalledApps()
                refreshDownloadedReadyIds()
            }
        }
        viewModelScope.launch {
            downloads.collect {
                refreshDownloadedReadyIds()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isPackageReceiverRegistered) {
            runCatching {
                getApplication<Application>().unregisterReceiver(packageBroadcastReceiver)
            }
            isPackageReceiverRegistered = false
        }
    }

    // =========================================================================
    // Onboarding & Theming
    // =========================================================================

    fun completeOnboarding() {
        authPrefs.edit().putBoolean("has_completed_onboarding", true).apply()
        _uiState.value = _uiState.value.copy(showOnboarding = false)
        emitToast(getApplication<Application>().getString(R.string.toast_onboarding_done))
    }

    fun setThemeMode(mode: String) {
        val targetMode = when (mode.lowercase()) {
            "amoled" -> com.example.ui.theme.ThemeMode.AMOLED
            "light" -> com.example.ui.theme.ThemeMode.LIGHT
            "system" -> com.example.ui.theme.ThemeMode.SYSTEM
            else -> com.example.ui.theme.ThemeMode.DARK
        }
        settingsRepo.setThemeMode(targetMode)
        authPrefs.edit().putString("ps_theme_mode", targetMode.name.lowercase()).apply()
        _uiState.value = _uiState.value.copy(
            themeMode = targetMode.name.lowercase(),
            isDarkMode = targetMode != com.example.ui.theme.ThemeMode.LIGHT
        )
        val ctx = getApplication<Application>()
        emitToast(ctx.getString(R.string.toast_theme_changed, ctx.getString(targetMode.labelRes)))
    }

    fun setThemeAccent(accentColor: Long) {
        settingsRepo.setAccentHex(accentColor)
        authPrefs.edit().putLong("ps_theme_accent", accentColor).apply()
        _uiState.value = _uiState.value.copy(themeAccent = accentColor)
        emitToast(getApplication<Application>().getString(R.string.toast_accent_updated))
    }

    fun toggleAutoTranslate() {
        val next = !_uiState.value.autoTranslateEnabled
        settingsRepo.setAutoTranslateEnabled(next)
        _uiState.value = _uiState.value.copy(autoTranslateEnabled = next)
        authPrefs.edit().putBoolean("ps_auto_translate", next).apply()
    }

    fun toggleWifiOnlyTranslation() {
        val next = !_uiState.value.wifiOnlyTranslation
        settingsRepo.setWifiOnlyTranslation(next)
        _uiState.value = _uiState.value.copy(wifiOnlyTranslation = next)
        authPrefs.edit().putBoolean("ps_wifi_only_translate", next).apply()
    }

    fun disableWifiOnlyTranslation() {
        settingsRepo.setWifiOnlyTranslation(false)
        _uiState.value = _uiState.value.copy(wifiOnlyTranslation = false)
        authPrefs.edit().putBoolean("ps_wifi_only_translate", false).apply()
    }

    fun setHomeFilter(filter: HomeFilterType) {
        _uiState.value = _uiState.value.copy(homeFilterType = filter)
    }

    // =========================================================================
    // Sync, Display Window & Refresh Methods
    // =========================================================================

    fun setHomeSection(section: HomeSection) {
        if (_homeSection.value == section) return
        _homeSection.value = section
        _selectedCategoryKey.value = "all"
        _homeDisplayLimit.value = pageSize
    }

    fun selectHomeCategory(categoryKey: String) {
        if (_selectedCategoryKey.value.equals(categoryKey, ignoreCase = true)) return
        _selectedCategoryKey.value = categoryKey
        _homeDisplayLimit.value = pageSize
    }

    fun loadInitialApps(categoryKey: String = _selectedCategoryKey.value) {
        _selectedCategoryKey.value = categoryKey
        _homeDisplayLimit.value = pageSize
        viewModelScope.launch {
            _pagedError.value = null
            val ok = syncManager.syncNow(forceFull = false, suppressNewBanner = true)
            if (!ok && apps.value.isEmpty()) {
                _pagedError.value = getApplication<Application>().getString(R.string.error_fetch_apps)
            }
        }
    }

    fun loadNextAppsPage() {
        if (!hasMoreApps.value || _isLoadingNextPage.value) return
        _isLoadingNextPage.value = true
        _homeDisplayLimit.value = _homeDisplayLimit.value + pageSize
        _isLoadingNextPage.value = false
    }

    fun consumeNewAppsBanner() {
        syncManager.consumeNewAppsBanner()
    }

    fun onAppResumed(context: Context) {
        val savedTag = settingsRepo.languageTag.value
        val activeLang = settingsRepo.getEffectiveLanguageCode()
        if (_uiState.value.selectedLanguageTag != savedTag || _uiState.value.currentLanguage != activeLang) {
            _uiState.value = _uiState.value.copy(
                selectedLanguageTag = savedTag,
                currentLanguage = activeLang
            )
        }
        syncManager.onForegroundResumed()
        checkAndResumePendingInstallAfterResume(context)
        refreshInstalledApps()
        refreshDownloadedReadyIds()
        refreshStorageStats()
    }

    fun pullToRefresh(silent: Boolean = false) {
        if (_isPullRefreshing.value) return
        viewModelScope.launch {
            _isPullRefreshing.value = true
            _pagedError.value = null
            syncManager.invalidateEtag()
            val ok = syncManager.syncNow(forceFull = false, suppressNewBanner = true)
            _isPullRefreshing.value = false
            refreshInstalledApps()
            refreshDownloadedReadyIds()
            if (!ok && apps.value.isEmpty()) {
                _pagedError.value = getApplication<Application>().getString(R.string.error_fetch_apps)
            } else if (!silent && ok) {
                emitToast(getApplication<Application>().getString(R.string.toast_catalog_refreshed))
            }
        }
    }

    fun refreshData() {
        pullToRefresh(silent = false)
    }

    // =========================================================================
    // Installed Apps, Downloaded APKs & Updates Detection
    // =========================================================================

    fun refreshDownloadedReadyIds() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val ids = HashSet<String>()
            val catalog = apps.value
            val appsRepo = AppsRepository.getInstance(context)
            for (app in catalog) {
                val apkFile = ApkInstaller.findExistingPrimaryInstallFile(context, app.id)
                if (apkFile != null && apkFile.exists() && apkFile.length() > 0L) {
                    ids.add(app.id)
                    if ((app.packageName.isNullOrBlank() || app.packageName.startsWith("com.primestore.")) &&
                        !resolvedPkgWriteCache.contains(app.id)
                    ) {
                        val realPkg = ApkInstaller.getApkPackageName(context, apkFile)?.trim()
                        if (!realPkg.isNullOrBlank() && realPkg != app.packageName) {
                            resolvedPkgWriteCache.add(app.id)
                            appsRepo.updatePackageName(app.id, realPkg)
                        }
                    }
                }
            }
            for (dl in downloads.value) {
                if (dl.isCompleted) {
                    val apkFile = ApkInstaller.findExistingPrimaryInstallFile(context, dl.appId)
                    if (apkFile != null && apkFile.exists() && apkFile.length() > 0L) {
                        ids.add(dl.appId)
                    }
                }
            }
            _downloadedReadyIds.value = ids
        }
    }

    fun refreshInstalledApps() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingInstalledApps.value = true
            val catalog = apps.value
            val list = mutableListOf<InstalledAppItemState>()
            val pkgMap = HashMap<String, InstalledPackageSummary>(catalog.size)
            val appsRepo = AppsRepository.getInstance(context)

            for (app in catalog) {
                val apkFile = ApkInstaller.findExistingPrimaryInstallFile(context, app.id)
                val resolvedPkg = ApkInstaller.findInstalledPackage(
                    context,
                    app.name,
                    apkFile,
                    app.packageName
                )

                if (!resolvedPkg.isNullOrBlank()) {
                    if ((app.packageName.isNullOrBlank() || app.packageName.startsWith("com.primestore.")) &&
                        !resolvedPkgWriteCache.contains(app.id)
                    ) {
                        resolvedPkgWriteCache.add(app.id)
                        appsRepo.updatePackageName(app.id, resolvedPkg)
                    }
                    val installedInfo = PackageHelper.getInstalledInfo(context, resolvedPkg)
                    if (installedInfo != null) {
                        val storeCode = app.versionCode
                        val hasUpdate = storeCode > installedInfo.versionCode
                        pkgMap[app.id] = InstalledPackageSummary(
                            packageName = resolvedPkg,
                            versionCode = installedInfo.versionCode,
                            versionName = installedInfo.versionName
                        )
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
                _installedPkgByAppId.value = pkgMap
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
        val ctx = getApplication<Application>()
        val pending = _installedAppsState.value.filter { it.hasUpdate }
        if (pending.isEmpty()) {
            emitToast(ctx.getString(R.string.app_updates_all_up_to_date))
            return
        }
        emitToast(ctx.getString(R.string.toast_updating_apps_count, pending.size))
        pending.forEach {
            startDownload(it.app.id)
        }
    }

    fun uninstallPackage(packageName: String, context: Context) {
        val success = PackageHelper.uninstall(context, packageName)
        if (success) {
            emitToast(context.getString(R.string.toast_uninstalling_app))
            viewModelScope.launch {
                delay(3000)
                refreshInstalledApps()
            }
        } else {
            emitToast(context.getString(R.string.toast_uninstall_failed))
        }
    }

    fun openInstalledApp(packageName: String, context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            emitToast(context.getString(R.string.app_no_launch_screen))
        }
    }

    // =========================================================================
    // Pre-install & Signature Conflict Resolution
    // =========================================================================

    fun installDownloadedApk(appId: String, context: Context) {
        val file = ApkInstaller.findExistingPrimaryInstallFile(context, appId)
        if (file == null || !file.exists() || file.length() == 0L) {
            emitToast(context.getString(R.string.toast_apk_not_found))
            startDownload(appId)
            return
        }
        handlePreInstall(appId, file, context)
    }

    private fun handlePreInstall(appId: String, apkFile: File, context: Context) {
        val (_, result) = PackageHelper.checkInstallPreconditions(context, apkFile)

        when (result) {
            is InstallPrecheckResult.SignatureConflict -> {
                pendingConflictApkFile = apkFile
                pendingConflictPackageName = result.packageName
                pendingConflictAppId = appId
                _signatureConflictAppIds.value = _signatureConflictAppIds.value + appId
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
                pendingConflictAppId = appId
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

    fun confirmConflictUninstall(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        val data = _uiState.value.conflictDialogData
        _uiState.value = _uiState.value.copy(conflictDialogData = null)
        if (data != null) {
            pendingConflictPackageName = data.packageName
            if (pendingConflictApkFile == null && data.apkFilePath.isNotBlank()) {
                pendingConflictApkFile = File(data.apkFilePath)
            }
            PackageHelper.uninstall(context, data.packageName, launcher)
            if (launcher == null) {
                emitToast(context.getString(R.string.toast_please_uninstall_first))
            }
        }
    }

    fun onUninstallActivityResult(resultCode: Int, context: Context) {
        val pendingPkg = pendingConflictPackageName
        val pendingFile = pendingConflictApkFile
        val pendingId = pendingConflictAppId.orEmpty()

        if (!pendingPkg.isNullOrBlank() && pendingFile != null && pendingFile.exists()) {
            if (!PackageHelper.isInstalled(context, pendingPkg)) {
                pendingConflictPackageName = null
                pendingConflictApkFile = null
                pendingConflictAppId = null
                if (pendingId.isNotBlank()) {
                    _signatureConflictAppIds.value = _signatureConflictAppIds.value - pendingId
                }
                emitToast(context.getString(R.string.toast_starting_install))
                executeDirectInstall(pendingId, pendingFile, context)
                return
            } else {
                pendingConflictPackageName = null
                pendingConflictApkFile = null
                pendingConflictAppId = null
                emitToast(context.getString(R.string.uninstall_cancelled))
                return
            }
        }
        refreshInstalledApps()
        refreshDownloadedReadyIds()
    }

    fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(conflictDialogData = null)
        pendingConflictApkFile = null
        pendingConflictPackageName = null
        pendingConflictAppId = null
    }

    fun proceedWithDowngradeInstall(context: Context) {
        val data = _uiState.value.downgradeDialogData
        val resolvedAppId = pendingConflictAppId ?: apps.value.find {
            it.packageName.equals(data?.packageName, ignoreCase = true)
        }?.id.orEmpty()
        _uiState.value = _uiState.value.copy(downgradeDialogData = null)
        if (data != null) {
            val file = File(data.apkFilePath)
            if (file.exists()) {
                executeDirectInstall(resolvedAppId, file, context)
            }
        }
    }

    fun dismissDowngradeDialog() {
        _uiState.value = _uiState.value.copy(downgradeDialogData = null)
    }

    private fun executeDirectInstall(appId: String, file: File, context: Context) {
        val expectedPkg = apps.value.find { it.id == appId }?.packageName.orEmpty()
        val silentMode = settingsRepo.shizukuRootInstallEnabled.value
        if (PackageHelper.canRequestPackageInstalls(context) || silentMode) {
            if (appId.isNotBlank()) {
                _installingAppIds.value = _installingAppIds.value + appId
            }
            viewModelScope.launch {
                val bundleFiles = if (appId.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        ApkInstaller.getBundleApkFiles(context, appId).ifEmpty { listOf(file) }
                    }
                } else {
                    listOf(file)
                }
                val obbFile = if (appId.isNotBlank()) {
                    ApkInstaller.getObbFile(context, appId).takeIf { it.exists() && it.length() > 0L }
                } else {
                    null
                }
                val launched = PackageHelper.installPackageBundle(
                    context = context,
                    files = bundleFiles,
                    appId = appId,
                    expectedPackageName = expectedPkg,
                    obbFile = obbFile,
                    useSilentRootShizuku = silentMode
                ) { msg ->
                    emitToast(msg)
                }
                if (launched) {
                    if (appId.isNotBlank()) {
                        SupabaseClient.incrementDownloads(appId)
                    }
                    delay(3000)
                    refreshInstalledApps()
                    refreshDownloadedReadyIds()
                } else if (appId.isNotBlank()) {
                    _installingAppIds.value = _installingAppIds.value - appId
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
        val pendingId = pendingConflictAppId.orEmpty()

        if (!pendingPkg.isNullOrBlank() && pendingFile != null && pendingFile.exists()) {
            val isStillInstalled = PackageHelper.isInstalled(context, pendingPkg)
            if (!isStillInstalled) {
                // Successfully uninstalled! Now proceed with install
                pendingConflictPackageName = null
                pendingConflictApkFile = null
                pendingConflictAppId = null
                if (pendingId.isNotBlank()) {
                    _signatureConflictAppIds.value = _signatureConflictAppIds.value - pendingId
                }
                emitToast(context.getString(R.string.toast_starting_install))
                executeDirectInstall(pendingId, pendingFile, context)
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
        val app = apps.value.find { it.id == appId }
        if (app != null) {
            val freeBytes = getAvailableStorageBytes(context)
            val estimatedBytes = if (app.sizeBytes > 0L) app.sizeBytes else parseSizeBytes(app.size)
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
        val ctx = getApplication<Application>()
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
                emitToast(ctx.getString(R.string.toast_report_submitted))
            } catch (_: Exception) {
                emitToast(ctx.getString(R.string.toast_report_submitted))
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
            emitToast(context.getString(R.string.store_update_downloading))

            val result = StoreSelfUpdater.downloadStoreUpdateApk(context, release) { progress, _, _ ->
                _storeUpdateDownloadProgress.value = progress
            }

            _isDownloadingStoreUpdate.value = false
            result.onSuccess { apkFile ->
                emitToast(context.getString(R.string.store_update_ready_install))
                PackageHelper.installPackageBundle(
                    context = context,
                    files = listOf(apkFile),
                    expectedPackageName = context.packageName,
                    useSilentRootShizuku = settingsRepo.shizukuRootInstallEnabled.value
                ) { msg -> emitToast(msg) }
            }.onFailure { err ->
                emitToast(context.getString(R.string.store_update_download_failed, err.message ?: ""))
            }
        }
    }

    fun downloadAndInstallStoreUpdate(info: StoreUpdateInfo, context: Context) {
        if (_isDownloadingStoreUpdate.value) return
        val apkUrl = info.apkUrl.trim()
        if (apkUrl.isBlank() || !apkUrl.startsWith("http")) {
            emitToast(context.getString(R.string.toast_invalid_download_link))
            return
        }

        viewModelScope.launch {
            _isDownloadingStoreUpdate.value = true
            _storeUpdateDownloadProgress.value = 0f
            emitToast(context.getString(R.string.store_update_downloading))

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
                    var lastEmitTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 100L || progress >= 1f) {
                                lastEmitTime = now
                                _storeUpdateDownloadProgress.value = progress
                            }
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
                emitToast(context.getString(R.string.store_update_ready_install))
                PackageHelper.installPackageBundle(
                    context = context,
                    files = listOf(updateFile),
                    expectedPackageName = context.packageName,
                    useSilentRootShizuku = settingsRepo.shizukuRootInstallEnabled.value
                ) { msg -> emitToast(msg) }
            } else {
                emitToast(context.getString(R.string.store_update_download_failed, ""))
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
        val email = SupabaseClient.getSavedUserEmail()
        if (!email.isNullOrBlank() && SupabaseClient.hasStoredSession()) {
            val isUserAdmin = SupabaseClient.isAdmin(email)
            val adminName = getApplication<Application>().getString(R.string.admin_badge)
            _uiState.value = _uiState.value.copy(
                userProfile = UserProfile(
                    name = if (isUserAdmin) adminName else email.substringBefore("@"),
                    email = email,
                    isPrimeMember = isUserAdmin
                ),
                isAdmin = isUserAdmin
            )
            viewModelScope.launch {
                SupabaseClient.getValidToken(forceRefresh = false)
            }
        } else {
            authPrefs.edit()
                .remove("ps_email")
                .remove("ps_token")
                .remove("ps_rt")
                .apply()
        }
    }

    fun onSessionExpired() {
        GitHubTokenProvider.clear()
        SupabaseClient.clearSession()
        authPrefs.edit()
            .remove("ps_email")
            .remove("ps_token")
            .remove("ps_rt")
            .apply()
        if (_uiState.value.showAdminPanel) {
            pendingReturnToAdminAfterLogin = true
        }
        val msg = getApplication<Application>().getString(R.string.err_session_expired_relogin)
        _uiState.value = _uiState.value.copy(
            userProfile = null,
            isAdmin = false,
            showAdminPanel = false,
            currentTab = MainTab.SETTINGS,
            showLoginDialog = true,
            authErrorMessage = msg
        )
        emitToast(msg)
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
        val dest = when (category) {
            AppCategory.APPS -> CategoryDestination.AllAppsHub
            AppCategory.GAMES -> CategoryDestination.AllGamesHub
            else -> CategoryDestination.SingleCategory(category)
        }
        _selectedHubFilterKey.value = "all"
        _categoryDestination.value = dest
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            currentTab = MainTab.CATEGORIES,
            selectedAppId = null
        )
    }

    fun openCategoryItem(item: CategoryItem) {
        when (item.key.lowercase()) {
            "apps" -> openCategory(AppCategory.APPS)
            "games" -> openCategory(AppCategory.GAMES)
            "trending", "popular" -> openCategory(AppCategory.POPULAR)
            else -> {
                val mappedEnum = SupabaseClient.mapCategory(item.key)
                _selectedHubFilterKey.value = "all"
                _categoryDestination.value = CategoryDestination.SingleCategory(
                    category = mappedEnum,
                    categoryItem = item
                )
                _uiState.value = _uiState.value.copy(
                    selectedCategory = mappedEnum,
                    currentTab = MainTab.CATEGORIES,
                    selectedAppId = null
                )
            }
        }
    }

    fun selectHubFilter(key: String) {
        _selectedHubFilterKey.value = key
    }

    fun clearSelectedCategory() {
        _categoryDestination.value = null
        _selectedHubFilterKey.value = "all"
        _uiState.value = _uiState.value.copy(selectedCategory = null)
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(categorySortOrder = order)
    }

    fun setSearchAllTabs(all: Boolean) {
        _searchAllTabs.value = all
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
        val effective = settingsRepo.getEffectiveLanguageCode()
        val nextLang = if (effective == "ar") "en" else "ar"
        setLanguage(nextLang)
    }

    fun setLanguage(lang: String) {
        settingsRepo.setLanguage(lang)
        val savedTag = settingsRepo.languageTag.value
        val effectiveLang = settingsRepo.getEffectiveLanguageCode()
        _uiState.value = _uiState.value.copy(
            selectedLanguageTag = savedTag,
            currentLanguage = effectiveLang
        )
        authPrefs.edit().putString("ps_language", savedTag).apply()
        val ctx = getApplication<Application>()
        if (savedTag == "auto") {
            emitToast(ctx.getString(R.string.toast_lang_auto))
        } else {
            val langName = when (savedTag) {
                "ar" -> ctx.getString(R.string.lang_ar)
                "en" -> ctx.getString(R.string.lang_en)
                "tr" -> ctx.getString(R.string.lang_tr)
                "fr" -> ctx.getString(R.string.lang_fr)
                "es" -> ctx.getString(R.string.lang_es)
                "de" -> ctx.getString(R.string.lang_de)
                "ru" -> ctx.getString(R.string.lang_ru)
                "fa" -> ctx.getString(R.string.lang_fa)
                "ur" -> ctx.getString(R.string.lang_ur)
                "hi" -> ctx.getString(R.string.lang_hi)
                "id" -> ctx.getString(R.string.lang_id)
                "pt" -> ctx.getString(R.string.lang_pt)
                else -> savedTag
            }
            emitToast(ctx.getString(R.string.toast_lang_changed, langName))
        }
    }

    fun toggleDarkMode() {
        val newMode = if (_uiState.value.isDarkMode) "light" else "dark"
        setThemeMode(newMode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settingsRepo.setNotificationsEnabled(enabled)
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
    }

    fun toggleNotifications() {
        setNotificationsEnabled(!_uiState.value.notificationsEnabled)
    }

    fun toggleDeleteApkAfterInstall() {
        val next = !_uiState.value.deleteApkAfterInstall
        settingsRepo.setDeleteApkAfterInstall(next)
        _uiState.value = _uiState.value.copy(deleteApkAfterInstall = next)
    }

    fun toggleShizukuRootInstall() {
        val next = !settingsRepo.shizukuRootInstallEnabled.value
        settingsRepo.setShizukuRootInstallEnabled(next)
    }

    fun refreshStorageStats() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val cacheStr = settingsRepo.formatStorageSize(settingsRepo.getCacheSizeBytes(ctx))
            val apksStr = settingsRepo.formatStorageSize(settingsRepo.getApksSizeBytes(ctx))
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    cacheSize = cacheStr,
                    apksSize = apksStr
                )
            }
        }
    }

    fun clearCache() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val freed = settingsRepo.clearTemporaryCache(ctx)
            val cacheStr = settingsRepo.formatStorageSize(settingsRepo.getCacheSizeBytes(ctx))
            val apksStr = settingsRepo.formatStorageSize(settingsRepo.getApksSizeBytes(ctx))
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    cacheSize = cacheStr,
                    apksSize = apksStr
                )
                emitToast(ctx.getString(R.string.toast_cache_cleared, settingsRepo.formatStorageSize(freed)))
            }
        }
    }

    fun deleteAllDownloadedApks() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val freed = settingsRepo.deleteAllDownloadedApks(ctx)
            val cacheStr = settingsRepo.formatStorageSize(settingsRepo.getCacheSizeBytes(ctx))
            val apksStr = settingsRepo.formatStorageSize(settingsRepo.getApksSizeBytes(ctx))
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    cacheSize = cacheStr,
                    apksSize = apksStr
                )
                refreshDownloadedReadyIds()
                emitToast(ctx.getString(R.string.apks_deleted_toast, settingsRepo.formatStorageSize(freed)))
            }
        }
    }

    fun buildDiagnosticsReport(): String {
        val ctx = getApplication<Application>()
        val email = _uiState.value.userProfile?.email
        if (!SupabaseClient.isAdmin(email) && !SupabaseClient.isOwner(email)) {
            return ""
        }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val syncTs = lastSyncTimeMillis.value
        val syncTimeStr = if (syncTs > 0L) dateFmt.format(Date(syncTs)) else ctx.getString(R.string.diag_never)
        val tokenConfigured = if (githubTokenStatus.value.isConfigured == true || GitHubTokenProvider.hasCachedTokenInMemory()) {
            ctx.getString(R.string.diag_yes)
        } else {
            ctx.getString(R.string.diag_no)
        }
        val recentErrors = SecureLogger.getRecentErrors()
        val errorsText = if (recentErrors.isEmpty()) {
            ctx.getString(R.string.diag_none)
        } else {
            recentErrors.takeLast(12).joinToString("\n") { entry ->
                "[${dateFmt.format(Date(entry.timestampMillis))}] ${entry.level}/${entry.code}: ${SecureLogger.sanitize(entry.message)}"
            }
        }
        return buildString {
            appendLine("${ctx.getString(R.string.diag_store_version)}: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${ctx.getString(R.string.diag_android_version)}: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${ctx.getString(R.string.diag_device_lang)}: ${Locale.getDefault().toLanguageTag()}")
            appendLine("${ctx.getString(R.string.diag_app_lang)}: ${_uiState.value.currentLanguage} (${_uiState.value.selectedLanguageTag})")
            appendLine("${ctx.getString(R.string.diag_abis)}: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("${ctx.getString(R.string.diag_room_count)}: ${apps.value.size}")
            appendLine("${ctx.getString(R.string.diag_last_sync)}: $syncTimeStr (${SecureLogger.sanitize(lastSyncResult.value)})")
            appendLine("${ctx.getString(R.string.diag_gh_token)}: $tokenConfigured")
            appendLine()
            appendLine("${ctx.getString(R.string.diag_recent_errors)}:")
            append(errorsText)
        }
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
            val file = ApkInstaller.findExistingPrimaryInstallFile(context, appId)
            if (file != null && file.exists() && file.length() > 0L) {
                executeDirectInstall(appId, file, context)
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
        val ctx = getApplication<Application>()
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(authErrorMessage = ctx.getString(R.string.toast_enter_email_pass))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = SupabaseClient.signInWithPassword(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (result.success && !result.accessToken.isNullOrBlank()) {
                val userEmail = result.email ?: trimmedEmail
                val isUserAdmin = SupabaseClient.isAdmin(userEmail)
                authPrefs.edit()
                    .putString("ps_email", userEmail)
                    .remove("ps_token")
                    .remove("ps_rt")
                    .apply()

                val returnToAdmin = isUserAdmin && pendingReturnToAdminAfterLogin
                pendingReturnToAdminAfterLogin = false

                _uiState.value = _uiState.value.copy(
                    userProfile = UserProfile(
                        name = if (isUserAdmin) ctx.getString(R.string.admin_badge) else userEmail.substringBefore("@"),
                        email = userEmail,
                        isPrimeMember = isUserAdmin
                    ),
                    isAdmin = isUserAdmin,
                    showLoginDialog = false,
                    showAdminPanel = returnToAdmin || _uiState.value.showAdminPanel
                )
                emitToast(ctx.getString(if (isUserAdmin) R.string.toast_login_welcome_admin else R.string.toast_login_success))
            } else {
                _uiState.value = _uiState.value.copy(authErrorMessage = result.errorMessage ?: ctx.getString(R.string.toast_admin_wrong_creds))
            }
        }
    }

    fun signUp(email: String, pass: String) {
        val ctx = getApplication<Application>()
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(authErrorMessage = ctx.getString(R.string.toast_enter_email_pass))
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
                    .remove("ps_token")
                    .remove("ps_rt")
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
                emitToast(ctx.getString(R.string.toast_signup_success))
            } else {
                _uiState.value = _uiState.value.copy(authErrorMessage = result.errorMessage ?: ctx.getString(R.string.toast_admin_wrong_creds))
            }
        }
    }

    fun signOutAll() {
        GitHubTokenProvider.clear()
        SupabaseClient.clearSession()
        pendingReturnToAdminAfterLogin = false
        authPrefs.edit()
            .remove("ps_email")
            .remove("ps_token")
            .remove("ps_rt")
            .apply()
        _uiState.value = _uiState.value.copy(
            userProfile = null,
            isAdmin = false,
            showAdminPanel = false,
            showLoginDialog = false
        )
        emitToast(getApplication<Application>().getString(R.string.toast_logout_success))
    }

    fun loginAdmin(
        email: String,
        pass: String,
        onResult: (success: Boolean, errorMsg: String?) -> Unit = { _, _ -> }
    ) {
        val ctx = getApplication<Application>()
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            val msg = ctx.getString(R.string.toast_enter_email_pass)
            emitToast(msg)
            onResult(false, msg)
            return
        }
        if (!SupabaseClient.isAdmin(trimmedEmail)) {
            val msg = ctx.getString(R.string.toast_admin_not_authorized)
            emitToast(msg)
            onResult(false, msg)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val authRes = SupabaseClient.signInWithPassword(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (authRes.success && !authRes.accessToken.isNullOrBlank()) {
                val userEmail = authRes.email ?: trimmedEmail
                authPrefs.edit()
                    .putString("ps_email", userEmail)
                    .remove("ps_token")
                    .remove("ps_rt")
                    .apply()
                pendingReturnToAdminAfterLogin = false
                _uiState.value = _uiState.value.copy(
                    isAdmin = true,
                    showAdminLoginDialog = false,
                    userProfile = UserProfile(
                        name = ctx.getString(R.string.admin_badge),
                        email = userEmail,
                        isPrimeMember = true
                    )
                )
                emitToast(ctx.getString(R.string.toast_admin_login_success))
                onResult(true, null)
            } else {
                val msg = authRes.errorMessage ?: ctx.getString(R.string.toast_admin_wrong_creds)
                emitToast(msg)
                onResult(false, msg)
            }
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
        categoryKey: String = "tools"
    ) {
        viewModelScope.launch {
            syncManager.invalidateEtag()
            syncManager.syncNow(forceFull = false, suppressNewBanner = true)
            refreshInstalledApps()
            refreshDownloadedReadyIds()
        }
    }

    fun refreshCatalogAfterAdminChange() {
        viewModelScope.launch {
            syncManager.invalidateEtag()
            syncManager.syncNow(forceFull = false, suppressNewBanner = true)
            refreshInstalledApps()
            refreshDownloadedReadyIds()
        }
    }

    fun deleteAppFromAdmin(appId: String) {
        repository.deleteApp(appId, null)
        refreshInstalledApps()
        refreshDownloadedReadyIds()
    }

    fun addCustomCategory(nameEn: String, nameAr: String, emoji: String, type: String) {
        viewModelScope.launch {
            categoriesRepository.addCustomCategory(nameEn, nameAr, emoji, type)
        }
    }

    fun updateCategory(key: String, nameEn: String, nameAr: String, emoji: String) {
        viewModelScope.launch {
            categoriesRepository.updateCategory(key, nameEn, nameAr, emoji)
        }
    }

    fun setCategoryHidden(key: String, hidden: Boolean) {
        viewModelScope.launch {
            categoriesRepository.setCategoryHidden(key, hidden)
        }
    }

    fun moveCategory(key: String, type: String, moveUp: Boolean) {
        viewModelScope.launch {
            categoriesRepository.moveCategory(key, type, moveUp)
        }
    }
}

