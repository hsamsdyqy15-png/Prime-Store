package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.installer.ApkInstaller
import com.example.ui.components.DowngradeOrSameVersionDialog
import com.example.ui.components.LowStorageDialog
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PrimeBottomNavBar
import com.example.ui.components.SignatureConflictDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AppDetailsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UpdatesAndInstalledScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object StoreRoutes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val DETAILS = "details/{appId}"
    const val UPDATES = "updates/{initialTab}"
    const val ADMIN = "admin"

    fun details(appId: String): String = "details/${Uri.encode(appId)}"
    fun updates(initialTab: Int): String = "updates/$initialTab"
    fun forTab(tab: MainTab): String = when (tab) {
        MainTab.HOME -> HOME
        MainTab.CATEGORIES -> CATEGORIES
        MainTab.FAVORITES -> FAVORITES
        MainTab.DOWNLOADS -> DOWNLOADS
        MainTab.SETTINGS -> SETTINGS
    }
}

private val TOP_LEVEL_ROUTES = setOf(
    StoreRoutes.HOME,
    StoreRoutes.CATEGORIES,
    StoreRoutes.FAVORITES,
    StoreRoutes.DOWNLOADS,
    StoreRoutes.SETTINGS
)

@Composable
fun PrimeStoreApp(
    viewModel: PrimeStoreViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    // Live feed, pagination & categories
    val pagedApps by viewModel.pagedApps.collectAsState()
    val selectedCategoryKey by viewModel.selectedCategoryKey.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isLoadingNextPage by viewModel.isLoadingNextPage.collectAsState()
    val hasMoreApps by viewModel.hasMoreApps.collectAsState()
    val pagedError by viewModel.pagedError.collectAsState()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsState()
    val newAppsAddedCount by viewModel.newAppsAddedCount.collectAsState()
    val categoryDestination by viewModel.categoryDestination.collectAsState()
    val selectedHubFilterKey by viewModel.selectedHubFilterKey.collectAsState()
    val homeSection by viewModel.homeSection.collectAsState()
    val searchAllTabs by viewModel.searchAllTabs.collectAsState()
    val shizukuRootInstallEnabled by viewModel.shizukuRootInstallEnabled.collectAsState()
    val visibleGameCategories by viewModel.visibleGameCategories.collectAsState()
    val visibleAppCategories by viewModel.visibleAppCategories.collectAsState()
    val allGameCategories by viewModel.allGameCategories.collectAsState()
    val allAppCategories by viewModel.allAppCategories.collectAsState()

    // Precomputed package / download / conflict states
    val installedApps by viewModel.installedAppsState.collectAsState()
    val installedPkgByAppId by viewModel.installedPkgByAppId.collectAsState()
    val downloadedReadyIds by viewModel.downloadedReadyIds.collectAsState()
    val installingAppIds by viewModel.installingAppIds.collectAsState()
    val signatureConflictAppIds by viewModel.signatureConflictAppIds.collectAsState()
    val isRefreshingInstalledApps by viewModel.isRefreshingInstalledApps.collectAsState()

    // Store self-update states
    val githubReleaseInfo by viewModel.githubReleaseInfo.collectAsState()
    val hasNewUpdateBadge by viewModel.hasNewUpdateBadge.collectAsState()
    val availableStoreUpdate by viewModel.availableStoreUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingStoreUpdate by viewModel.isDownloadingStoreUpdate.collectAsState()
    val storeUpdateDownloadProgress by viewModel.storeUpdateDownloadProgress.collectAsState()

    // Navigation, drawer, list states & uninstall activity result launcher
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute == null || currentRoute in TOP_LEVEL_ROUTES

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var activeToast by remember { mutableStateOf<String?>(null) }

    val homeListState = rememberLazyListState()
    val categoriesListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val downloadsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val updatesListState = rememberLazyListState()
    val installedListState = rememberLazyListState()

    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onUninstallActivityResult(result.resultCode, context)
    }

    val navigateToTab: (MainTab) -> Unit = remember(navController, viewModel) {
        { tab ->
            val targetRoute = StoreRoutes.forTab(tab)
            val activeRoute = navController.currentBackStackEntry?.destination?.route
            if (activeRoute == targetRoute) {
                if (tab == MainTab.CATEGORIES && (categoryDestination != null || uiState.selectedCategory != null)) {
                    viewModel.clearSelectedCategory()
                }
                scope.launch {
                    val targetListState = when (tab) {
                        MainTab.HOME -> homeListState
                        MainTab.CATEGORIES -> categoriesListState
                        MainTab.FAVORITES -> favoritesListState
                        MainTab.DOWNLOADS -> downloadsListState
                        MainTab.SETTINGS -> settingsListState
                    }
                    targetListState.animateScrollToItem(0)
                }
            } else {
                viewModel.selectTab(tab)
                navController.navigate(targetRoute) {
                    popUpTo(StoreRoutes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val navigateToDetails: (String) -> Unit = remember(navController, viewModel) {
        { appId ->
            viewModel.openAppDetails(appId)
            navController.navigate(StoreRoutes.details(appId)) {
                launchSingleTop = false
            }
        }
    }

    LaunchedEffect(currentRoute) {
        val mappedTab = when (currentRoute) {
            StoreRoutes.HOME -> MainTab.HOME
            StoreRoutes.CATEGORIES -> MainTab.CATEGORIES
            StoreRoutes.FAVORITES -> MainTab.FAVORITES
            StoreRoutes.DOWNLOADS -> MainTab.DOWNLOADS
            StoreRoutes.SETTINGS -> MainTab.SETTINGS
            else -> null
        }
        if (mappedTab != null && uiState.currentTab != mappedTab) {
            viewModel.selectTab(mappedTab)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            activeToast = message
            delay(2800)
            if (activeToast == message) {
                activeToast = null
            }
        }
    }

    val colors = com.example.ui.theme.PrimeTheme.colors
    val diagnosticsReport = remember(
        uiState.isAdmin,
        uiState.currentLanguage,
        uiState.selectedLanguageTag,
        apps.size
    ) {
        if (uiState.isAdmin) viewModel.buildDiagnosticsReport() else ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (uiState.showSplash) {
            SplashScreen(
                onDismiss = { viewModel.dismissSplash() }
            )
        } else if (uiState.showOnboarding) {
            OnboardingScreen(
                onFinishOnboarding = { viewModel.completeOnboarding() },
                onRequestNotificationPermission = { /* handled in OnboardingScreen */ }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = isTopLevelRoute,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = colors.surface,
                        drawerContentColor = colors.textPrimary
                    ) {
                        NavigationDrawerContent(
                            userProfile = uiState.userProfile,
                            currentTab = uiState.currentTab,
                            onTabSelected = { tab ->
                                navigateToTab(tab)
                                scope.launch { drawerState.close() }
                            },
                            onCategorySelected = { category ->
                                viewModel.openCategory(category)
                                navController.navigate(StoreRoutes.CATEGORIES) {
                                    popUpTo(StoreRoutes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            },
                            onLoginClick = {
                                viewModel.openLoginDialog()
                                navigateToTab(MainTab.SETTINGS)
                            }
                        )
                    }
                }
            ) {
                Scaffold(
                    bottomBar = {
                        if (isTopLevelRoute) {
                            PrimeBottomNavBar(
                                currentTab = uiState.currentTab,
                                onTabSelected = navigateToTab
                            )
                        }
                    },
                    containerColor = colors.background
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = StoreRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        enterTransition = {
                            if (targetState.destination.route in TOP_LEVEL_ROUTES) {
                                fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing))
                            } else {
                                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 10 }
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route in TOP_LEVEL_ROUTES) {
                                fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                            } else {
                                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                    slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { -it / 16 }
                            }
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            if (initialState.destination.route in TOP_LEVEL_ROUTES) {
                                fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                            } else {
                                fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                    slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { it / 10 }
                            }
                        }
                    ) {
                        composable(StoreRoutes.HOME) {
                            HomeScreen(
                                allApps = apps,
                                pagedApps = pagedApps,
                                homeSection = homeSection,
                                visibleCategories = if (homeSection == HomeSection.GAMES) visibleGameCategories else visibleAppCategories,
                                selectedCategoryKey = selectedCategoryKey,
                                isInitialLoading = isInitialLoading,
                                isLoadingNextPage = isLoadingNextPage,
                                hasMoreApps = hasMoreApps,
                                pagedError = pagedError,
                                downloads = downloads,
                                installedPkgByAppId = installedPkgByAppId,
                                downloadedReadyIds = downloadedReadyIds,
                                installingAppIds = installingAppIds,
                                signatureConflictAppIds = signatureConflictAppIds,
                                storeUpdateAvailable = hasNewUpdateBadge || availableStoreUpdate != null || githubReleaseInfo != null,
                                isPullRefreshing = isPullRefreshing,
                                newAppsAddedCount = newAppsAddedCount,
                                targetLanguageCode = uiState.currentLanguage,
                                autoTranslateEnabled = uiState.autoTranslateEnabled,
                                wifiOnlyTranslation = uiState.wifiOnlyTranslation,
                                activeFilter = uiState.homeFilterType,
                                listState = homeListState,
                                onHomeSectionChange = { section -> viewModel.setHomeSection(section) },
                                onStoreUpdateClick = { navigateToTab(MainTab.SETTINGS) },
                                onConsumeNewAppsBanner = { viewModel.consumeNewAppsBanner() },
                                onFilterChange = { filter -> viewModel.setHomeFilter(filter) },
                                onAppClick = navigateToDetails,
                                onCategoryClick = { category ->
                                    viewModel.openCategory(category)
                                    navController.navigate(StoreRoutes.CATEGORIES) {
                                        popUpTo(StoreRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onCategoryItemClick = { catItem ->
                                    viewModel.openCategoryItem(catItem)
                                    navController.navigate(StoreRoutes.CATEGORIES) {
                                        popUpTo(StoreRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onCategorySelect = { key -> viewModel.selectHomeCategory(key) },
                                onLoadNextPage = { viewModel.loadNextAppsPage() },
                                onSearchClick = {
                                    viewModel.openSearch()
                                    navController.navigate(StoreRoutes.SEARCH) {
                                        launchSingleTop = true
                                    }
                                },
                                onMenuClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open()
                                        else drawerState.close()
                                    }
                                },
                                onDownloadClick = { appId -> viewModel.checkStorageAndStartDownload(appId, context) },
                                onInstallClick = { appId -> viewModel.installDownloadedApk(appId, context) },
                                onOpenAppClick = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                onResumeDownload = { appId -> viewModel.resumeDownload(appId) },
                                onPauseDownload = { appId -> viewModel.pauseDownload(appId) },
                                onRefresh = { viewModel.pullToRefresh() }
                            )
                        }

                        composable(StoreRoutes.CATEGORIES) {
                            CategoriesScreen(
                                selectedCategory = uiState.selectedCategory,
                                categoryDestination = categoryDestination,
                                selectedHubFilterKey = selectedHubFilterKey,
                                allApps = apps,
                                visibleGameCategories = visibleGameCategories,
                                visibleAppCategories = visibleAppCategories,
                                downloads = downloads,
                                installedPkgByAppId = installedPkgByAppId,
                                downloadedReadyIds = downloadedReadyIds,
                                installingAppIds = installingAppIds,
                                signatureConflictAppIds = signatureConflictAppIds,
                                sortOrder = uiState.categorySortOrder,
                                isPullRefreshing = isPullRefreshing,
                                listState = categoriesListState,
                                onCategoryClick = { cat -> viewModel.openCategory(cat) },
                                onCategoryItemClick = { item -> viewModel.openCategoryItem(item) },
                                onSelectHubFilter = { key -> viewModel.selectHubFilter(key) },
                                onBackClick = {
                                    if (categoryDestination != null || uiState.selectedCategory != null) {
                                        viewModel.clearSelectedCategory()
                                    } else {
                                        navigateToTab(MainTab.HOME)
                                    }
                                },
                                onSearchClick = {
                                    viewModel.openSearch()
                                    navController.navigate(StoreRoutes.SEARCH) {
                                        launchSingleTop = true
                                    }
                                },
                                onAppClick = navigateToDetails,
                                onDownloadClick = { appId -> viewModel.checkStorageAndStartDownload(appId, context) },
                                onInstallClick = { appId -> viewModel.installDownloadedApk(appId, context) },
                                onOpenAppClick = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                onResumeDownload = { appId -> viewModel.resumeDownload(appId) },
                                onPauseDownload = { appId -> viewModel.pauseDownload(appId) },
                                onRefresh = { viewModel.pullToRefresh() },
                                onSortChange = { order -> viewModel.setSortOrder(order) }
                            )
                        }

                        composable(StoreRoutes.FAVORITES) {
                            FavoritesScreen(
                                favoriteIds = favorites,
                                allApps = apps,
                                listState = favoritesListState,
                                onAppClick = navigateToDetails,
                                onRemoveFavorite = { appId -> viewModel.toggleFavorite(appId) }
                            )
                        }

                        composable(StoreRoutes.DOWNLOADS) {
                            DownloadsScreen(
                                downloads = downloads,
                                filterTab = uiState.downloadFilterTab,
                                allApps = apps,
                                installedPkgByAppId = installedPkgByAppId,
                                downloadedReadyIds = downloadedReadyIds,
                                installingAppIds = installingAppIds,
                                signatureConflictAppIds = signatureConflictAppIds,
                                listState = downloadsListState,
                                onFilterChange = { filter -> viewModel.setDownloadFilterTab(filter) },
                                onPauseDownload = { id -> viewModel.pauseDownload(id) },
                                onResumeDownload = { id -> viewModel.resumeDownload(id) },
                                onCancelDownload = { id -> viewModel.cancelDownload(id) },
                                onClearAll = { viewModel.clearDownloads() },
                                onAppClick = navigateToDetails,
                                onInstall = { id -> viewModel.installDownloadedApk(id, context) },
                                onUninstall = { pkg -> viewModel.uninstallPackage(pkg, context) },
                                onOpenApp = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                onDeleteApkFile = { id -> viewModel.cancelDownload(id) }
                            )
                        }

                        composable(StoreRoutes.SETTINGS) {
                            SettingsScreen(
                                currentLanguage = uiState.selectedLanguageTag,
                                effectiveLanguage = uiState.currentLanguage,
                                isDarkMode = uiState.isDarkMode,
                                notificationsEnabled = uiState.notificationsEnabled,
                                cacheSize = uiState.cacheSize,
                                apksSize = uiState.apksSize,
                                deleteApkAfterInstall = uiState.deleteApkAfterInstall,
                                userProfile = uiState.userProfile,
                                isAdmin = uiState.isAdmin,
                                diagnosticsReport = diagnosticsReport,
                                hasNewUpdateBadge = hasNewUpdateBadge,
                                isCheckingUpdate = isCheckingUpdate,
                                availableStoreUpdate = availableStoreUpdate,
                                isDownloadingStoreUpdate = isDownloadingStoreUpdate,
                                storeUpdateDownloadProgress = storeUpdateDownloadProgress,
                                showLoginDialog = uiState.showLoginDialog,
                                isAuthLoading = uiState.isAuthLoading,
                                authErrorMessage = uiState.authErrorMessage,
                                listState = settingsListState,
                                onOpenInstalledApps = {
                                    viewModel.openInstalledAppsScreen()
                                    navController.navigate(StoreRoutes.updates(1)) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenUpdates = {
                                    viewModel.openUpdatesScreen()
                                    navController.navigate(StoreRoutes.updates(0)) {
                                        launchSingleTop = true
                                    }
                                },
                                updatesCount = installedApps.count { it.hasUpdate },
                                installedCount = installedApps.size,
                                themeMode = uiState.themeMode,
                                themeAccent = uiState.themeAccent,
                                onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
                                onSetThemeAccent = { color -> viewModel.setThemeAccent(color) },
                                autoTranslateEnabled = uiState.autoTranslateEnabled,
                                wifiOnlyTranslation = uiState.wifiOnlyTranslation,
                                onToggleAutoTranslate = { viewModel.toggleAutoTranslate() },
                                onToggleWifiOnlyTranslation = { viewModel.toggleWifiOnlyTranslation() },
                                githubReleaseInfo = githubReleaseInfo,
                                onDownloadGitHubRelease = { rel ->
                                    viewModel.downloadAndInstallGitHubStoreUpdate(context, rel)
                                },
                                onOpenLogin = { viewModel.openLoginDialog() },
                                onCloseLogin = { viewModel.closeLoginDialog() },
                                onSignIn = { email, pass -> viewModel.signIn(email, pass) },
                                onSignUp = { email, pass -> viewModel.signUp(email, pass) },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                onRefreshData = { viewModel.pullToRefresh() },
                                onCheckForUpdate = { onResult ->
                                    viewModel.checkForStoreUpdate(manual = true, onResult = onResult)
                                },
                                onDownloadStoreUpdate = { info ->
                                    viewModel.downloadAndInstallStoreUpdate(info, context)
                                },
                                onDismissUpdateBadge = { viewModel.dismissUpdateBadge() },
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                onToggleNotifications = { viewModel.toggleNotifications() },
                                onSetNotificationsEnabled = { enabled -> viewModel.setNotificationsEnabled(enabled) },
                                onToggleDeleteApkAfterInstall = { viewModel.toggleDeleteApkAfterInstall() },
                                shizukuRootInstallEnabled = shizukuRootInstallEnabled,
                                onToggleShizukuRootInstall = { viewModel.toggleShizukuRootInstall() },
                                onClearCache = { viewModel.clearCache() },
                                onDeleteAllDownloadedApks = { viewModel.deleteAllDownloadedApks() },
                                onRefreshStorageStats = { viewModel.refreshStorageStats() },
                                onShareApp = {
                                    shareText(
                                        context = context,
                                        text = context.getString(R.string.share_store_text, context.getString(R.string.app_name))
                                    )
                                },
                                onRateApp = {
                                    viewModel.emitToast(context.getString(R.string.rate_thanks_toast))
                                },
                                onLogoutClick = { viewModel.logout() },
                                onOpenAdminPanel = {
                                    viewModel.openAdminPanel()
                                    navController.navigate(StoreRoutes.ADMIN) {
                                        launchSingleTop = true
                                    }
                                },
                                onAdminLogin = { email, pass, onResult ->
                                    viewModel.loginAdmin(email, pass, onResult)
                                },
                                onToast = { msg -> viewModel.emitToast(msg) }
                            )
                        }

                        composable(StoreRoutes.SEARCH) {
                            SearchScreen(
                                query = uiState.searchQuery,
                                searchResults = searchResults,
                                searchHistory = searchHistory,
                                activeSection = homeSection,
                                searchAllTabs = searchAllTabs,
                                downloads = downloads,
                                installedPkgByAppId = installedPkgByAppId,
                                downloadedReadyIds = downloadedReadyIds,
                                installingAppIds = installingAppIds,
                                signatureConflictAppIds = signatureConflictAppIds,
                                isPullRefreshing = isPullRefreshing,
                                listState = searchListState,
                                onQueryChange = { q -> viewModel.updateSearchQuery(q) },
                                onToggleSearchAllTabs = { enabled -> viewModel.setSearchAllTabs(enabled) },
                                onSearchTagClick = { tag -> viewModel.selectSearchQuery(tag) },
                                onRemoveHistoryItem = { item -> viewModel.removeSearchHistory(item) },
                                onClearHistory = { viewModel.clearSearchHistory() },
                                onAppClick = navigateToDetails,
                                onDownloadClick = { appId -> viewModel.checkStorageAndStartDownload(appId, context) },
                                onInstallClick = { appId -> viewModel.installDownloadedApk(appId, context) },
                                onOpenAppClick = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                onPauseDownload = { appId -> viewModel.pauseDownload(appId) },
                                onResumeDownload = { appId -> viewModel.resumeDownload(appId) },
                                onRefresh = { viewModel.pullToRefresh(silent = true) },
                                onCloseSearch = {
                                    viewModel.closeSearch()
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = StoreRoutes.DETAILS,
                            arguments = listOf(navArgument("appId") { type = NavType.StringType })
                        ) { entry ->
                            val encodedId = entry.arguments?.getString("appId").orEmpty()
                            val appId = Uri.decode(encodedId)
                            val selectedApp = apps.find { it.id == appId } ?: pagedApps.find { it.id == appId }
                            if (selectedApp != null) {
                                AppDetailsScreen(
                                    app = selectedApp,
                                    isFavorite = favorites.contains(selectedApp.id),
                                    downloadItem = downloads.find { it.appId == selectedApp.id },
                                    reviews = reviews[selectedApp.id] ?: emptyList(),
                                    allApps = apps.ifEmpty { pagedApps },
                                    installedInfo = installedPkgByAppId[selectedApp.id],
                                    isApkReady = downloadedReadyIds.contains(selectedApp.id),
                                    isInstalling = installingAppIds.contains(selectedApp.id),
                                    hasSignatureConflict = signatureConflictAppIds.contains(selectedApp.id),
                                    targetLanguageCode = uiState.currentLanguage,
                                    autoTranslateEnabled = uiState.autoTranslateEnabled,
                                    wifiOnlyTranslation = uiState.wifiOnlyTranslation,
                                    onDisableWifiOnlyAndRetry = { viewModel.disableWifiOnlyTranslation() },
                                    onBackClick = {
                                        viewModel.closeAppDetails()
                                        navController.popBackStack()
                                    },
                                    onSearchClick = {
                                        viewModel.openSearch()
                                        navController.navigate(StoreRoutes.SEARCH) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(selectedApp.id) },
                                    onDownloadClick = { viewModel.checkStorageAndStartDownload(selectedApp.id, context) },
                                    onResumeDownload = { viewModel.resumeDownload(selectedApp.id) },
                                    onPauseDownload = { viewModel.pauseDownload(selectedApp.id) },
                                    onCancelDownload = { viewModel.cancelDownload(selectedApp.id) },
                                    onInstallClick = { viewModel.installDownloadedApk(selectedApp.id, context) },
                                    onForceReinstallClick = { viewModel.installDownloadedApk(selectedApp.id, context) },
                                    onUninstallClick = { pkg -> viewModel.uninstallPackage(pkg, context) },
                                    onOpenAppClick = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                    onReportProblem = { reason, details ->
                                        viewModel.submitProblemReport(
                                            selectedApp.id,
                                            selectedApp.nameAr.ifBlank { selectedApp.name },
                                            reason,
                                            details
                                        )
                                    },
                                    onAppClick = navigateToDetails,
                                    onAddReview = { rating, comment ->
                                        viewModel.addReview(selectedApp.id, rating, comment)
                                    },
                                    onScreenshotClick = { /* handled inside AppDetailsScreen viewer */ },
                                    onShareClick = {
                                        val displayName = selectedApp.nameAr.ifBlank { selectedApp.name }
                                        val safeVer = selectedApp.version
                                            .trim()
                                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                            ?: "1.0.0"
                                        shareText(
                                            context = context,
                                            text = context.getString(R.string.share_app_text, displayName, safeVer)
                                        )
                                    }
                                )
                            } else if (apps.isNotEmpty()) {
                                LaunchedEffect(appId) {
                                    viewModel.closeAppDetails()
                                    navController.popBackStack()
                                }
                            }
                        }

                        composable(
                            route = StoreRoutes.UPDATES,
                            arguments = listOf(navArgument("initialTab") { type = NavType.IntType })
                        ) { entry ->
                            val initialTab = entry.arguments?.getInt("initialTab") ?: 0
                            UpdatesAndInstalledScreen(
                                initialTab = initialTab,
                                installedApps = installedApps,
                                isRefreshing = isRefreshingInstalledApps,
                                updatesListState = updatesListState,
                                installedListState = installedListState,
                                onRefresh = { viewModel.refreshInstalledApps() },
                                onAppClick = navigateToDetails,
                                onUpdateApp = { appId -> viewModel.checkStorageAndStartDownload(appId, context) },
                                onUpdateAll = { viewModel.updateAllAppsSequentially() },
                                onUninstallApp = { pkg -> viewModel.uninstallPackage(pkg, context) },
                                onOpenApp = { pkg -> viewModel.openInstalledApp(pkg, context) },
                                onBackClick = {
                                    viewModel.closeUpdatesScreen()
                                    viewModel.closeInstalledAppsScreen()
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(StoreRoutes.ADMIN) {
                            if (!uiState.isAdmin) {
                                LaunchedEffect(Unit) {
                                    viewModel.closeAdminPanel()
                                    navController.navigate(StoreRoutes.SETTINGS) {
                                        popUpTo(StoreRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                AdminPanelScreen(
                                    adminEmail = uiState.userProfile?.email.orEmpty(),
                                    apps = apps,
                                    allGameCategories = allGameCategories,
                                    allAppCategories = allAppCategories,
                                    onAddCategory = { nEn, nAr, em, tp ->
                                        viewModel.addCustomCategory(nEn, nAr, em, tp)
                                    },
                                    onUpdateCategory = { k, nEn, nAr, em ->
                                        viewModel.updateCategory(k, nEn, nAr, em)
                                    },
                                    onToggleCategoryHidden = { k, h ->
                                        viewModel.setCategoryHidden(k, h)
                                    },
                                    onMoveCategory = { k, tp, up ->
                                        viewModel.moveCategory(k, tp, up)
                                    },
                                    onCatalogChanged = { viewModel.refreshCatalogAfterAdminChange() },
                                    onDeleteApp = { appId -> viewModel.deleteAppFromAdmin(appId) },
                                    onSessionExpired = {
                                        viewModel.onSessionExpired()
                                        navController.navigate(StoreRoutes.SETTINGS) {
                                            popUpTo(StoreRoutes.HOME) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onBackClick = {
                                        viewModel.closeAdminPanel()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Friendly Unknown Sources Install Permission Dialog (with auto-resume on ON_RESUME)
            if (uiState.showInstallPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissInstallPermissionDialog() },
                    containerColor = colors.card,
                    title = {
                        Text(
                            stringResource(R.string.install_perm_why_title),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.install_perm_why_1),
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.install_perm_why_2),
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.install_perm_why_3),
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.dismissInstallPermissionDialog()
                                ApkInstaller.openUnknownAppSourcesSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            Text(
                                stringResource(R.string.install_perm_open_settings),
                                color = colors.textOnAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissInstallPermissionDialog() }) {
                            Text(stringResource(R.string.cancel), color = colors.textMuted)
                        }
                    }
                )
            }

            // Signature Conflict Dialog (with ActivityResult uninstall continuation)
            val conflictData = uiState.conflictDialogData
            if (conflictData != null) {
                SignatureConflictDialog(
                    data = conflictData,
                    onConfirmUninstallAndInstall = {
                        viewModel.confirmConflictUninstall(context, uninstallLauncher)
                    },
                    onDismiss = {
                        viewModel.dismissConflictDialog()
                    }
                )
            }

            // Downgrade or Same Version Dialog
            val downgradeData = uiState.downgradeDialogData
            if (downgradeData != null) {
                DowngradeOrSameVersionDialog(
                    data = downgradeData,
                    onProceedAnyway = {
                        viewModel.proceedWithDowngradeInstall(context)
                    },
                    onDismiss = {
                        viewModel.dismissDowngradeDialog()
                    }
                )
            }

            // Low Storage Space Dialog
            val storageData = uiState.lowStorageDialogData
            if (storageData != null) {
                LowStorageDialog(
                    neededSize = storageData.first,
                    availableSize = storageData.second,
                    onProceedAnyway = {
                        viewModel.proceedWithLowStorageDownload()
                    },
                    onDismiss = {
                        viewModel.dismissLowStorageDialog()
                    }
                )
            }

            // Top Floating Toast Banner
            AnimatedVisibility(
                visible = activeToast != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(colors.card, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = activeToast.orEmpty(),
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Close Drawer cleanly on Back
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Pop Category Hub back to Categories grid before NavHost pops tab
    BackHandler(
        enabled = !drawerState.isOpen &&
            currentRoute == StoreRoutes.CATEGORIES &&
            (categoryDestination != null || uiState.selectedCategory != null)
    ) {
        viewModel.clearSelectedCategory()
    }

    // Refresh sync, language, storage, and pending installs on every ON_RESUME
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAppResumed(context)
    }
}

private fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}
