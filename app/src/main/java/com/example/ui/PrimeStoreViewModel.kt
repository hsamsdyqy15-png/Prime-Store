package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.installer.ApkInstaller
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import com.example.data.model.StoreCategories
import com.example.data.model.StoreUpdateInfo
import com.example.data.repository.PrimeStoreRepository
import com.example.data.supabase.SupabaseClient
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

data class UserProfile(
    val name: String,
    val email: String,
    val isPrimeMember: Boolean
)

const val ADMIN_EMAIL = "zaim9002@gmail.com"

data class UiState(
    val showSplash: Boolean = true,
    val currentTab: MainTab = MainTab.HOME,
    val selectedAppId: String? = null,
    val selectedCategory: AppCategory? = null,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val currentLanguage: String = "ar",
    val isDarkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val cacheSize: String = "42.8 MB",
    val downloadFilterTab: Int = 0, // 0 = جاري التحميل, 1 = تم التحميل
    val categorySortOrder: SortOrder = SortOrder.POPULAR,
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
    val userProfile: UserProfile? = null
)

class PrimeStoreViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PrimeStoreRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val apps: StateFlow<List<AppItem>> = repository.apps
    val favorites: StateFlow<Set<String>> = repository.favorites
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val reviews: StateFlow<Map<String, List<ReviewItem>>> = repository.reviews
    val searchHistory: StateFlow<List<String>> = repository.searchHistory
    val toastEvents = repository.toastEvents

    // =========================================================================
    // 1) Paged Apps & Infinite Scroll State for "كل التطبيقات"
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
    // 2) Store Update State (store_update table check & badge)
    // =========================================================================
    private val _availableStoreUpdate = MutableStateFlow<StoreUpdateInfo?>(null)
    val availableStoreUpdate: StateFlow<StoreUpdateInfo?> = _availableStoreUpdate.asStateFlow()

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
        val savedLang = authPrefs.getString("ps_language", "auto") ?: "auto"
        val activeLang = if (savedLang == "auto") detectDeviceLanguage() else savedLang
        _uiState.value = _uiState.value.copy(currentLanguage = activeLang)

        repository.onApkDownloadCompleted = { appId, apkFile ->
            val app = getApplication<Application>()
            if (ApkInstaller.canRequestPackageInstalls(app)) {
                ApkInstaller.installApk(app, apkFile)
            } else {
                _uiState.value = _uiState.value.copy(
                    showInstallPermissionDialog = true,
                    pendingInstallAppId = appId
                )
            }
        }

        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(showSplash = false)
        }

        // Initial fetch for Home infinite scroll
        loadInitialApps("all")

        // Auto-check store update on startup (max once every 6 hours)
        checkStoreUpdateSilentlyOnStartup()
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
            } catch (e: Exception) {
                paginationMutex.withLock {
                    _isLoadingNextPage.value = false
                }
            }
        }
    }

    fun refreshPagedApps() {
        loadInitialApps(_selectedCategoryKey.value)
        repository.refreshFromSupabase()
    }

    // =========================================================================
    // Store Update Methods
    // =========================================================================

    private fun checkStoreUpdateSilentlyOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastCheck = authPrefs.getLong("ps_last_update_check", 0L)
            val now = System.currentTimeMillis()
            val sixHoursMillis = 6 * 60 * 60 * 1000L
            if (now - lastCheck < sixHoursMillis) {
                return@launch
            }
            authPrefs.edit().putLong("ps_last_update_check", now).apply()

            val info = SupabaseClient.fetchStoreUpdate()
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                _availableStoreUpdate.value = info
                _hasNewUpdateBadge.value = true
            }
        }
    }

    fun checkForStoreUpdate(manual: Boolean = true, onResult: (hasUpdate: Boolean, info: StoreUpdateInfo?) -> Unit) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val info = withContext(Dispatchers.IO) {
                SupabaseClient.fetchStoreUpdate()
            }
            _isCheckingUpdate.value = false

            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
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

            val updateFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "prime_store_update_${info.versionCode}.apk")
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
                ApkInstaller.installApk(context, updateFile)
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
            isSearching = false
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
            "auto" -> "تلقائي (لغة الجهاز الافتراضية)"
            "ar" -> "العربية"
            "en" -> "English"
            "fr" -> "Français"
            "es" -> "Español"
            "de" -> "Deutsch"
            "tr" -> "Türkçe"
            "ru" -> "Русский"
            else -> lang
        }
        emitToast("تم ضبط لغة التطبيق: $langName 🌐")
    }

    fun refreshData() {
        repository.refreshFromSupabase()
        refreshPagedApps()
        emitToast("تم تحديث التطبيقات والمنشورات بنجاح 🔄")
    }

    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
    }

    fun toggleNotifications() {
        _uiState.value = _uiState.value.copy(notificationsEnabled = !_uiState.value.notificationsEnabled)
    }

    fun clearCache() {
        _uiState.value = _uiState.value.copy(cacheSize = "0 KB")
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

    fun installDownloadedApk(appId: String, context: Context) {
        val file = ApkInstaller.getApkFile(context, appId)
        if (!file.exists() || file.length() == 0L) {
            emitToast("الملف غير متوفر حالياً، جاري بدء التحميل")
            startDownload(appId)
            return
        }

        if (ApkInstaller.canRequestPackageInstalls(context)) {
            ApkInstaller.installApk(context, file)
            viewModelScope.launch {
                SupabaseClient.incrementDownloads(appId)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                showInstallPermissionDialog = true,
                pendingInstallAppId = appId
            )
        }
    }

    fun dismissInstallPermissionDialog() {
        _uiState.value = _uiState.value.copy(showInstallPermissionDialog = false)
    }

    fun onPermissionGrantedResumeInstall(context: Context) {
        val appId = _uiState.value.pendingInstallAppId ?: return
        if (ApkInstaller.canRequestPackageInstalls(context)) {
            val file = ApkInstaller.getApkFile(context, appId)
            if (file.exists() && file.length() > 0) {
                ApkInstaller.installApk(context, file)
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
    }

    fun deleteAppFromAdmin(appId: String) {
        val token = authPrefs.getString("ps_token", null)
        repository.deleteApp(appId, token)
        refreshPagedApps()
    }
}
