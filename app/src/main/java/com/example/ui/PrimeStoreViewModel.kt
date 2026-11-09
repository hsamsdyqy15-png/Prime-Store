package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import com.example.data.repository.PrimeStoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                        it.tags.any { tag -> tag.contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val authPrefs = application.getSharedPreferences("prime_store_auth", android.content.Context.MODE_PRIVATE)

    init {
        restoreSession()
        repository.onApkDownloadCompleted = { appId, apkFile ->
            val app = getApplication<Application>()
            if (com.example.data.installer.ApkInstaller.canRequestPackageInstalls(app)) {
                com.example.data.installer.ApkInstaller.installApk(app, apkFile)
            } else {
                _uiState.value = _uiState.value.copy(
                    showInstallPermissionDialog = true,
                    pendingInstallAppId = appId
                )
            }
        }
        viewModelScope.launch {
            delay(2500)
            _uiState.value = _uiState.value.copy(showSplash = false)
        }
    }

    fun restoreSession() {
        val email = authPrefs.getString("ps_email", null)
        if (!email.isNullOrBlank()) {
            val isUserAdmin = com.example.data.supabase.SupabaseClient.isAdmin(email)
            _uiState.value = _uiState.value.copy(
                userProfile = UserProfile(
                    name = if (isUserAdmin) "مدير المتجر (Admin)" else email.substringBefore("@"),
                    email = email,
                    isPrimeMember = isUserAdmin
                ),
                isAdmin = isUserAdmin
            )
        }
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

    fun toggleLanguage() {
        val nextLang = if (_uiState.value.currentLanguage == "ar") "en" else "ar"
        setLanguage(nextLang)
    }

    fun setLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(currentLanguage = lang)
        val langName = when (lang) {
            "ar" -> "العربية"
            "en" -> "English"
            "fr" -> "Français"
            "es" -> "Español"
            "de" -> "Deutsch"
            "tr" -> "Türkçe"
            "ru" -> "Русский"
            else -> lang
        }
        emitToast("تم تغيير لغة التطبيق إلى $langName 🌐")
    }

    fun refreshData() {
        repository.refreshFromSupabase()
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

    fun installDownloadedApk(appId: String, context: android.content.Context) {
        val file = com.example.data.installer.ApkInstaller.getApkFile(context, appId)
        if (!file.exists() || file.length() == 0L) {
            emitToast("الملف غير متوفر حالياً، جاري بدء التحميل")
            startDownload(appId)
            return
        }

        if (com.example.data.installer.ApkInstaller.canRequestPackageInstalls(context)) {
            com.example.data.installer.ApkInstaller.installApk(context, file)
            viewModelScope.launch {
                com.example.data.supabase.SupabaseClient.incrementDownloads(appId)
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

    fun onPermissionGrantedResumeInstall(context: android.content.Context) {
        val appId = _uiState.value.pendingInstallAppId ?: return
        if (com.example.data.installer.ApkInstaller.canRequestPackageInstalls(context)) {
            val file = com.example.data.installer.ApkInstaller.getApkFile(context, appId)
            if (file.exists() && file.length() > 0) {
                com.example.data.installer.ApkInstaller.installApk(context, file)
                viewModelScope.launch {
                    com.example.data.supabase.SupabaseClient.incrementDownloads(appId)
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
            val result = com.example.data.supabase.SupabaseClient.signInWithPassword(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (result.success || com.example.data.supabase.SupabaseClient.isAdmin(trimmedEmail)) {
                val userEmail = result.email ?: trimmedEmail
                val isUserAdmin = com.example.data.supabase.SupabaseClient.isAdmin(userEmail)
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
            val result = com.example.data.supabase.SupabaseClient.signUp(trimmedEmail, pass)
            _uiState.value = _uiState.value.copy(isAuthLoading = false)

            if (result.success) {
                val userEmail = result.email ?: trimmedEmail
                val isUserAdmin = com.example.data.supabase.SupabaseClient.isAdmin(userEmail)
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
        if (com.example.data.supabase.SupabaseClient.isAdmin(email)) {
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
        supportedAbis: String = "Universal (All Architectures)"
    ) {
        val appId = id ?: "app_${System.currentTimeMillis()}"
        val tags = mutableListOf(category.titleAr)
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
            supportedAbis = supportedAbis
        )
        // The app is saved/updated in Supabase directly by AdminPanelScreen with strict payload;
        // Refresh catalog from Supabase to immediately load the newly inserted row
        repository.refreshFromSupabase()
    }

    fun deleteAppFromAdmin(appId: String) {
        val token = authPrefs.getString("ps_token", null)
        repository.deleteApp(appId, token)
    }
}
