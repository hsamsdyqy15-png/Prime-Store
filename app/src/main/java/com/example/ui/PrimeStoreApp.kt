package com.example.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.installer.ApkInstaller
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PrimeBottomNavBar
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AppDetailsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeTextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Infinite scroll & Store update states
    val pagedApps by viewModel.pagedApps.collectAsState()
    val selectedCategoryKey by viewModel.selectedCategoryKey.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isLoadingNextPage by viewModel.isLoadingNextPage.collectAsState()
    val hasMoreApps by viewModel.hasMoreApps.collectAsState()
    val pagedError by viewModel.pagedError.collectAsState()

    val hasNewUpdateBadge by viewModel.hasNewUpdateBadge.collectAsState()
    val availableStoreUpdate by viewModel.availableStoreUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingStoreUpdate by viewModel.isDownloadingStoreUpdate.collectAsState()
    val storeUpdateDownloadProgress by viewModel.storeUpdateDownloadProgress.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var activeToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            activeToast = message
            delay(2800)
            if (activeToast == message) {
                activeToast = null
            }
        }
    }

    val layoutDirection = if (uiState.currentLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PrimeBackground)
        ) {
            if (uiState.showSplash) {
                SplashScreen(
                    onDismiss = { viewModel.dismissSplash() }
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !uiState.isSearching && uiState.selectedAppId == null,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFF120C1D),
                            drawerContentColor = Color.White
                        ) {
                            NavigationDrawerContent(
                                userProfile = uiState.userProfile,
                                currentTab = uiState.currentTab,
                                onTabSelected = { tab ->
                                    viewModel.selectTab(tab)
                                    scope.launch { drawerState.close() }
                                },
                                onCategorySelected = { category ->
                                    viewModel.openCategory(category)
                                    scope.launch { drawerState.close() }
                                },
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                },
                                onLoginClick = {
                                    viewModel.openLoginDialog()
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        bottomBar = {
                            if (uiState.selectedAppId == null && !uiState.isSearching && !uiState.showAdminPanel) {
                                PrimeBottomNavBar(
                                    currentTab = uiState.currentTab,
                                    onTabSelected = { tab -> viewModel.selectTab(tab) }
                                )
                            }
                        },
                        containerColor = PrimeBackground
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // Sub-screen 0: Admin Panel Screen
                            if (uiState.showAdminPanel) {
                                AdminPanelScreen(
                                    adminEmail = uiState.userProfile?.email ?: ADMIN_EMAIL,
                                    accessToken = context.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE).getString("ps_token", null),
                                    apps = apps,
                                    onSaveApp = { id, name, category, rating, size, description, features, iconUrl, fileUrl, screenshots, isOpenSource, sourceCodeUrl, supportedAbis, version, categoryKey ->
                                        viewModel.saveAppFromAdmin(
                                            id = id,
                                            name = name,
                                            category = category,
                                            rating = rating,
                                            size = size,
                                            description = description,
                                            features = features,
                                            iconUrl = iconUrl,
                                            fileUrl = fileUrl,
                                            screenshots = screenshots,
                                            isOpenSource = isOpenSource,
                                            sourceCodeUrl = sourceCodeUrl,
                                            supportedAbis = supportedAbis,
                                            version = version,
                                            categoryKey = categoryKey
                                        )
                                    },
                                    onDeleteApp = { appId ->
                                        viewModel.deleteAppFromAdmin(appId)
                                    },
                                    onBackClick = { viewModel.closeAdminPanel() }
                                )
                            }
                            // Sub-screen 1: App Details Screen
                            else if (uiState.selectedAppId != null) {
                                val selectedApp = apps.find { it.id == uiState.selectedAppId } ?: pagedApps.find { it.id == uiState.selectedAppId }
                                if (selectedApp != null) {
                                    AppDetailsScreen(
                                        app = selectedApp,
                                        isFavorite = favorites.contains(selectedApp.id),
                                        downloadItem = downloads.find { it.appId == selectedApp.id },
                                        reviews = reviews[selectedApp.id] ?: emptyList(),
                                        onBackClick = { viewModel.closeAppDetails() },
                                        onSearchClick = { viewModel.openSearch() },
                                        onToggleFavorite = { viewModel.toggleFavorite(selectedApp.id) },
                                        onDownloadClick = { viewModel.startDownload(selectedApp.id) },
                                        onCancelDownload = { viewModel.cancelDownload(selectedApp.id) },
                                        onInstallClick = { viewModel.installDownloadedApk(selectedApp.id, context) },
                                        onAddReview = { rating, comment ->
                                            viewModel.addReview(selectedApp.id, rating, comment)
                                        },
                                        onScreenshotClick = { /* preview */ },
                                        onShareClick = {
                                            viewModel.emitToast("تم نسخ رابط ${selectedApp.nameAr}")
                                        }
                                    )
                                } else {
                                    viewModel.closeAppDetails()
                                }
                            }
                            // Sub-screen 2: Search Screen
                            else if (uiState.isSearching) {
                                SearchScreen(
                                    query = uiState.searchQuery,
                                    searchResults = searchResults,
                                    searchHistory = searchHistory,
                                    onQueryChange = { q -> viewModel.updateSearchQuery(q) },
                                    onSearchTagClick = { tag -> viewModel.selectSearchQuery(tag) },
                                    onRemoveHistoryItem = { item -> viewModel.removeSearchHistory(item) },
                                    onClearHistory = { viewModel.clearSearchHistory() },
                                    onAppClick = { appId -> viewModel.openAppDetails(appId) },
                                    onCloseSearch = { viewModel.closeSearch() }
                                )
                            }
                            // Main Tabs
                            else {
                                Crossfade(
                                    targetState = uiState.currentTab,
                                    label = "TabCrossfade"
                                ) { tab ->
                                    when (tab) {
                                        MainTab.HOME -> {
                                            HomeScreen(
                                                allApps = apps,
                                                pagedApps = pagedApps,
                                                selectedCategoryKey = selectedCategoryKey,
                                                isInitialLoading = isInitialLoading,
                                                isLoadingNextPage = isLoadingNextPage,
                                                hasMoreApps = hasMoreApps,
                                                pagedError = pagedError,
                                                downloads = downloads,
                                                onAppClick = { appId -> viewModel.openAppDetails(appId) },
                                                onCategoryClick = { category -> viewModel.openCategory(category) },
                                                onCategorySelect = { key -> viewModel.selectHomeCategory(key) },
                                                onLoadNextPage = { viewModel.loadNextAppsPage() },
                                                onSearchClick = { viewModel.openSearch() },
                                                onMenuClick = {
                                                    scope.launch {
                                                        if (drawerState.isClosed) drawerState.open()
                                                        else drawerState.close()
                                                    }
                                                },
                                                onDownloadClick = { appId -> viewModel.startDownload(appId) },
                                                onInstallClick = { appId -> viewModel.installDownloadedApk(appId, context) },
                                                onRefresh = { viewModel.refreshData() }
                                            )
                                        }

                                        MainTab.CATEGORIES -> {
                                            CategoriesScreen(
                                                selectedCategory = uiState.selectedCategory,
                                                allApps = apps,
                                                sortOrder = uiState.categorySortOrder,
                                                onCategoryClick = { cat -> viewModel.openCategory(cat) },
                                                onBackClick = {
                                                    if (uiState.selectedCategory != null) {
                                                        viewModel.clearSelectedCategory()
                                                    } else {
                                                        viewModel.selectTab(MainTab.HOME)
                                                    }
                                                },
                                                onSearchClick = { viewModel.openSearch() },
                                                onAppClick = { appId -> viewModel.openAppDetails(appId) },
                                                onDownloadClick = { appId -> viewModel.startDownload(appId) },
                                                onSortChange = { order -> viewModel.setSortOrder(order) }
                                            )
                                        }

                                        MainTab.FAVORITES -> {
                                            FavoritesScreen(
                                                favoriteIds = favorites,
                                                allApps = apps,
                                                onAppClick = { appId -> viewModel.openAppDetails(appId) },
                                                onRemoveFavorite = { appId -> viewModel.toggleFavorite(appId) }
                                            )
                                        }

                                        MainTab.DOWNLOADS -> {
                                            DownloadsScreen(
                                                downloads = downloads,
                                                filterTab = uiState.downloadFilterTab,
                                                onFilterChange = { filter -> viewModel.setDownloadFilterTab(filter) },
                                                onPauseDownload = { id -> viewModel.pauseDownload(id) },
                                                onResumeDownload = { id -> viewModel.resumeDownload(id) },
                                                onCancelDownload = { id -> viewModel.cancelDownload(id) },
                                                onClearAll = { viewModel.clearDownloads() },
                                                onAppClick = { appId -> viewModel.openAppDetails(appId) },
                                                onInstall = { id -> viewModel.installDownloadedApk(id, context) }
                                            )
                                        }

                                        MainTab.SETTINGS -> {
                                            SettingsScreen(
                                                currentLanguage = uiState.currentLanguage,
                                                isDarkMode = uiState.isDarkMode,
                                                notificationsEnabled = uiState.notificationsEnabled,
                                                cacheSize = uiState.cacheSize,
                                                userProfile = uiState.userProfile,
                                                isAdmin = uiState.isAdmin,
                                                hasNewUpdateBadge = hasNewUpdateBadge,
                                                isCheckingUpdate = isCheckingUpdate,
                                                availableStoreUpdate = availableStoreUpdate,
                                                isDownloadingStoreUpdate = isDownloadingStoreUpdate,
                                                storeUpdateDownloadProgress = storeUpdateDownloadProgress,
                                                showLoginDialog = uiState.showLoginDialog,
                                                isAuthLoading = uiState.isAuthLoading,
                                                authErrorMessage = uiState.authErrorMessage,
                                                onOpenLogin = { viewModel.openLoginDialog() },
                                                onCloseLogin = { viewModel.closeLoginDialog() },
                                                onSignIn = { email, pass -> viewModel.signIn(email, pass) },
                                                onSignUp = { email, pass -> viewModel.signUp(email, pass) },
                                                onToggleLanguage = { viewModel.toggleLanguage() },
                                                onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                                onRefreshData = { viewModel.refreshData() },
                                                onCheckForUpdate = { onResult ->
                                                    viewModel.checkForStoreUpdate(manual = true, onResult = onResult)
                                                },
                                                onDownloadStoreUpdate = { info ->
                                                    viewModel.downloadAndInstallStoreUpdate(info, context)
                                                },
                                                onDismissUpdateBadge = { viewModel.dismissUpdateBadge() },
                                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                                onToggleNotifications = { viewModel.toggleNotifications() },
                                                onClearCache = { viewModel.clearCache() },
                                                onShareApp = {
                                                    viewModel.emitToast("تم نسخ رابط تطبيق Prime Store 🔗")
                                                },
                                                onRateApp = {
                                                    viewModel.emitToast("شكراً لدعمك وتقييمك 5 نجوم ⭐⭐⭐⭐⭐")
                                                },
                                                onLogoutClick = {
                                                    viewModel.logout()
                                                },
                                                onOpenAdminPanel = {
                                                    viewModel.openAdminPanel()
                                                },
                                                onAdminLogin = { email, pass ->
                                                    viewModel.loginAdmin(email, pass)
                                                },
                                                onToast = { msg ->
                                                    viewModel.emitToast(msg)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Unknown Sources Install Permission Dialog
            if (uiState.showInstallPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissInstallPermissionDialog() },
                    containerColor = Color(0xFF1E142D),
                    title = {
                        Text("تثبيت التطبيقات 📦", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            "يرجى السماح لـ Prime Store بتثبيت التطبيقات لمتابعة التثبيت",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.dismissInstallPermissionDialog()
                                ApkInstaller.openUnknownAppSourcesSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                        ) {
                            Text("السماح", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissInstallPermissionDialog() }) {
                            Text("إلغاء", color = PrimeTextMuted)
                        }
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
                        .background(Color(0xFF221538), RoundedCornerShape(16.dp))
                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = activeToast.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Auto resume install after permission is granted
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onPermissionGrantedResumeInstall(context)
    }

    // Comprehensive Back Button Handling
    BackHandler(enabled = true) {
        if (uiState.showSplash) {
            viewModel.dismissSplash()
        } else if (uiState.showAdminPanel) {
            viewModel.closeAdminPanel()
        } else if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (uiState.selectedAppId != null) {
            viewModel.closeAppDetails()
        } else if (uiState.isSearching) {
            viewModel.closeSearch()
        } else if (uiState.selectedCategory != null) {
            viewModel.clearSelectedCategory()
        } else if (uiState.currentTab != MainTab.HOME) {
            viewModel.selectTab(MainTab.HOME)
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }
}
