package com.example.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.model.AppActionState
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.StoreCategories
import com.example.data.model.resolveAppActionState
import com.example.data.repository.CategoriesRepository
import com.example.data.translation.MlKitTranslatorHelper
import com.example.data.translation.TranslationOutcome
import com.example.ui.HomeFilterType
import com.example.ui.HomeSection
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.SkeletonAppCard
import com.example.ui.components.pressScaleClickable
import com.example.ui.components.rememberShimmerBrush
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimeTheme
import kotlinx.coroutines.launch

data class CategoryQuickItem(
    val category: AppCategory,
    @StringRes val titleRes: Int,
    val glowColor: Long
)

val QUICK_CATEGORIES = listOf(
    CategoryQuickItem(AppCategory.GAMES, R.string.cat_games, 0xFFA855F7),
    CategoryQuickItem(AppCategory.APPS, R.string.cat_apps, 0xFF22C55E),
    CategoryQuickItem(AppCategory.ROOT_TOOLS, R.string.cat_root_tools, 0xFFDC2626),
    CategoryQuickItem(AppCategory.OPEN_SOURCE, R.string.cat_open_source, 0xFF3B82F6),
    CategoryQuickItem(AppCategory.POPULAR, R.string.cat_popular, 0xFFFBBF24),
    CategoryQuickItem(AppCategory.NEW_GAMES, R.string.cat_new_games, 0xFFF97316),
    CategoryQuickItem(AppCategory.TOOLS, R.string.cat_tools, 0xFF8B5CF6),
    CategoryQuickItem(AppCategory.MORE, R.string.cat_more, 0xFFA855F7)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    allApps: List<AppItem>,
    pagedApps: List<AppItem>,
    homeSection: HomeSection = HomeSection.GAMES,
    visibleCategories: List<CategoryEntity> = emptyList(),
    selectedCategoryKey: String = "all",
    isInitialLoading: Boolean = false,
    isLoadingNextPage: Boolean = false,
    hasMoreApps: Boolean = true,
    pagedError: String? = null,
    downloads: List<DownloadItem> = emptyList(),
    installedPkgByAppId: Map<String, InstalledPackageSummary> = emptyMap(),
    downloadedReadyIds: Set<String> = emptySet(),
    installingAppIds: Set<String> = emptySet(),
    signatureConflictAppIds: Set<String> = emptySet(),
    storeUpdateAvailable: Boolean = false,
    isPullRefreshing: Boolean = false,
    newAppsAddedCount: Int = 0,
    targetLanguageCode: String = "en",
    autoTranslateEnabled: Boolean = true,
    wifiOnlyTranslation: Boolean = false,
    activeFilter: HomeFilterType = HomeFilterType.ALL,
    listState: LazyListState = rememberLazyListState(),
    onHomeSectionChange: (HomeSection) -> Unit = {},
    onStoreUpdateClick: () -> Unit = {},
    onConsumeNewAppsBanner: () -> Unit = {},
    onFilterChange: (HomeFilterType) -> Unit = {},
    onAppClick: (String) -> Unit,
    onCategoryClick: (AppCategory) -> Unit,
    onCategoryItemClick: (CategoryItem) -> Unit = {},
    onCategorySelect: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (String) -> Unit = onDownloadClick,
    onOpenAppClick: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shimmerBrush = rememberShimmerBrush()
    val categoriesRepo = remember(context) { CategoriesRepository.getInstance(context) }

    val sectionApps = remember(allApps, homeSection) {
        if (homeSection == HomeSection.GAMES) {
            allApps.filter { StoreCategories.isGameApp(it) }
        } else {
            allApps.filter { StoreCategories.isNonGameApp(it) }
        }
    }

    val sectionCategoryPairs = remember(visibleCategories, homeSection, targetLanguageCode) {
        if (visibleCategories.isNotEmpty()) {
            visibleCategories.map { entity ->
                categoriesRepo.toCategoryItem(entity) to CategoriesRepository.resolveDisplayTitle(context, entity)
            }
        } else {
            val fallback = if (homeSection == HomeSection.GAMES) StoreCategories.GAMES else StoreCategories.APPS
            fallback.map { item -> item to context.getString(item.titleRes) }
        }
    }

    val newlyAddedApps = remember(sectionApps, pagedApps) {
        val source = if (sectionApps.isNotEmpty()) sectionApps else pagedApps
        source.sortedByDescending { maxOf(it.updatedAtMillis, it.createdAtMillis) }.take(6)
    }

    val filteredPagedApps = remember(pagedApps, activeFilter) {
        when (activeFilter) {
            HomeFilterType.ALL -> pagedApps
            HomeFilterType.SMALL_SIZE -> pagedApps.filter {
                if (it.sizeBytes > 0L) {
                    it.sizeBytes < 35L * 1024L * 1024L
                } else {
                    val sizeClean = it.size.trim().lowercase()
                    sizeClean.contains("kb") || (sizeClean.contains("mb") && (sizeClean.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 100.0) < 35.0)
                }
            }
            HomeFilterType.TOP_RATED -> pagedApps.sortedByDescending { it.rating }
            HomeFilterType.NEWEST -> pagedApps.sortedByDescending { maxOf(it.updatedAtMillis, it.createdAtMillis) }
        }
    }

    val featuredApp = remember(sectionApps, pagedApps) {
        sectionApps.firstOrNull { it.isFeatured } ?: sectionApps.firstOrNull() ?: pagedApps.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) {
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // 1. Top Bar
                item(key = "home_top_bar", contentType = "top_bar") {
                    HomeTopBar(
                        onMenuClick = onMenuClick,
                        onSearchClick = onSearchClick,
                        onRefresh = onRefresh
                    )
                }

                // 1.5 Top Games / Apps Segmented Tabs (strict separation)
                item(key = "home_section_tabs", contentType = "section_tabs") {
                    HomeSectionTabsRow(
                        activeSection = homeSection,
                        onSelectSection = onHomeSectionChange
                    )
                }

                // Store update top banner
                if (storeUpdateAvailable) {
                    item(key = "store_update_banner", contentType = "banner") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.buttonGradient)
                                .border(1.dp, AccentGold, RoundedCornerShape(14.dp))
                                .pressScaleClickable(withHaptic = true) { onStoreUpdateClick() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("🚀 ", fontSize = 20.sp)
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.update_available_title),
                                        color = colors.textOnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(id = R.string.update_available_desc),
                                        color = colors.textOnAccent.copy(alpha = 0.9f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentGold)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.update),
                                    color = Color(0xFF11071F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 2. Hero Banner
                item(key = "hero_banner", contentType = "hero") {
                    val featuredDl = featuredApp?.let { f -> downloads.find { it.appId == f.id } }
                    val featuredInstalled = featuredApp?.let { installedPkgByAppId[it.id] }
                    val featuredAction = featuredApp?.let { f ->
                        resolveAppActionState(
                            app = f,
                            downloadItem = featuredDl,
                            isApkReady = downloadedReadyIds.contains(f.id),
                            isInstalling = installingAppIds.contains(f.id),
                            hasSignatureConflict = signatureConflictAppIds.contains(f.id),
                            installedInfo = featuredInstalled
                        )
                    }
                    StoreHeroBanner(
                        featuredApp = featuredApp,
                        actionState = featuredAction,
                        installedPackageName = featuredInstalled?.packageName,
                        targetLanguageCode = targetLanguageCode,
                        autoTranslateEnabled = autoTranslateEnabled,
                        wifiOnlyTranslation = wifiOnlyTranslation,
                        onDownloadClick = onDownloadClick,
                        onInstallClick = onInstallClick,
                        onOpenAppClick = onOpenAppClick,
                        onResumeDownload = onResumeDownload,
                        onPauseDownload = onPauseDownload,
                        onDetailsClick = onAppClick
                    )
                }

                // Quick Categories Grid (strictly scoped to active section)
                item(key = "quick_categories", contentType = "quick_categories") {
                    StoreCategoriesSection(
                        homeSection = homeSection,
                        categoryPairs = sectionCategoryPairs.take(7),
                        onCategoryClick = onCategoryClick,
                        onCategoryItemClick = onCategoryItemClick
                    )
                }

                // 3. Newly Added
                if (newlyAddedApps.isNotEmpty()) {
                    item(key = "newly_added_header", contentType = "section_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✨ ", fontSize = 18.sp)
                            Text(
                                text = stringResource(id = R.string.newly_added),
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(
                        items = newlyAddedApps,
                        key = { "new_${it.id}" },
                        contentType = { "app_card" }
                    ) { app ->
                        val dlItem = downloads.find { it.appId == app.id }
                        val installedInfo = installedPkgByAppId[app.id]
                        val actionState = resolveAppActionState(
                            app = app,
                            downloadItem = dlItem,
                            isApkReady = downloadedReadyIds.contains(app.id),
                            isInstalling = installingAppIds.contains(app.id),
                            hasSignatureConflict = signatureConflictAppIds.contains(app.id),
                            installedInfo = installedInfo
                        )
                        ApptekaCard(
                            app = app,
                            actionState = actionState,
                            installedPackageName = installedInfo?.packageName,
                            onAppClick = { onAppClick(app.id) },
                            onDownloadClick = { onDownloadClick(app.id) },
                            onInstallClick = { onInstallClick(app.id) },
                            onOpenAppClick = onOpenAppClick,
                            onPauseDownload = { onPauseDownload(app.id) },
                            onResumeDownload = { onResumeDownload(app.id) },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // 4. All Section Items Header + Filter Chips (strictly Games or Apps)
                item(key = "all_apps_header", contentType = "filter_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (homeSection == HomeSection.GAMES) "🎮 " else "📱 ",
                                fontSize = 18.sp
                            )
                            Text(
                                text = stringResource(
                                    id = if (homeSection == HomeSection.GAMES) {
                                        R.string.hub_all_games_title
                                    } else {
                                        R.string.hub_all_apps_title
                                    }
                                ),
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val allChipTitle = stringResource(id = R.string.cat_all)
                        val requiredType = if (homeSection == HomeSection.GAMES) {
                            StoreCategories.ITEM_TYPE_GAME
                        } else {
                            StoreCategories.ITEM_TYPE_APP
                        }
                        val allFilterPair = remember(homeSection, allChipTitle, requiredType) {
                            StoreCategories.find("all", requiredType) to allChipTitle
                        }
                        val categoryFilterPairs = remember(allFilterPair, sectionCategoryPairs) {
                            listOf(allFilterPair) + sectionCategoryPairs.take(15)
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = categoryFilterPairs,
                                key = { (item, _) -> "cat_chip_${homeSection.name}_${item.key}" },
                                contentType = { "chip" }
                            ) { (item, displayTitle) ->
                                val isSelected = selectedCategoryKey.lowercase() == item.key.lowercase()
                                HomeCategoryChip(
                                    item = item,
                                    displayTitle = displayTitle,
                                    selected = isSelected,
                                    onClick = { onCategorySelect(item.key) }
                                )
                            }
                        }

                        val filterChips = listOf(
                            Pair(HomeFilterType.ALL, stringResource(R.string.filter_all)),
                            Pair(HomeFilterType.SMALL_SIZE, stringResource(R.string.filter_lightweight)),
                            Pair(HomeFilterType.TOP_RATED, stringResource(R.string.filter_top_rated)),
                            Pair(HomeFilterType.NEWEST, stringResource(R.string.filter_newest))
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            items(
                                items = filterChips,
                                key = { "sort_chip_${it.first.name}" },
                                contentType = { "chip" }
                            ) { (type, label) ->
                                val isSelected = activeFilter == type
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) colors.accent else colors.cardAlt)
                                        .border(
                                            1.dp,
                                            if (isSelected) colors.accent else colors.border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onFilterChange(type) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) colors.textOnAccent else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 5. Vertical Catalog List
                if (isInitialLoading && pagedApps.isEmpty()) {
                    items(
                        count = 4,
                        key = { "skel_$it" },
                        contentType = { "skeleton" }
                    ) {
                        SkeletonAppCard(
                            brush = shimmerBrush,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                } else if (filteredPagedApps.isEmpty()) {
                    item(key = "home_empty_state", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🔍", fontSize = 42.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (pagedError != null) {
                                        stringResource(R.string.home_fetch_error)
                                    } else {
                                        stringResource(R.string.home_no_apps_in_section)
                                    },
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredPagedApps,
                        key = { _, app -> "paged_${app.id}" },
                        contentType = { _, _ -> "app_card" }
                    ) { index, app ->
                        if (index >= pagedApps.size - 3 && hasMoreApps && !isLoadingNextPage) {
                            LaunchedEffect(index) {
                                onLoadNextPage()
                            }
                        }

                        val dlItem = downloads.find { it.appId == app.id }
                        val installedInfo = installedPkgByAppId[app.id]
                        val actionState = resolveAppActionState(
                            app = app,
                            downloadItem = dlItem,
                            isApkReady = downloadedReadyIds.contains(app.id),
                            isInstalling = installingAppIds.contains(app.id),
                            hasSignatureConflict = signatureConflictAppIds.contains(app.id),
                            installedInfo = installedInfo
                        )
                        ApptekaCard(
                            app = app,
                            actionState = actionState,
                            installedPackageName = installedInfo?.packageName,
                            onAppClick = { onAppClick(app.id) },
                            onDownloadClick = { onDownloadClick(app.id) },
                            onInstallClick = { onInstallClick(app.id) },
                            onOpenAppClick = onOpenAppClick,
                            onPauseDownload = { onPauseDownload(app.id) },
                            onResumeDownload = { onResumeDownload(app.id) },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    if (isLoadingNextPage) {
                        item(key = "home_loading_more", contentType = "loading_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.loading),
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Non-blocking floating "New apps added" pill banner
        AnimatedVisibility(
            visible = newAppsAddedCount > 0,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.buttonGradient)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .pressScaleClickable(withHaptic = true) {
                        onConsumeNewAppsBanner()
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("new_apps_added_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "✨ ", fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.new_apps_added_count, newAppsAddedCount),
                    color = colors.textOnAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.new_apps_added_tap),
                    color = colors.textOnAccent.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ApptekaCard(
    app: AppItem,
    actionState: AppActionState,
    installedPackageName: String?,
    onAppClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onOpenAppClick: (String) -> Unit = {},
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

    NeonCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("appteka_card_${app.id}"),
        shape = RoundedCornerShape(18.dp),
        hasGlow = false,
        onClick = onAppClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                emoji = app.iconEmoji,
                accentColor = app.accentColor,
                size = 54.dp,
                shape = RoundedCornerShape(14.dp),
                imageUrl = app.iconUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.nameAr.ifBlank { app.name },
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!installedPackageName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentGreen.copy(alpha = 0.18f))
                                .border(1.dp, AccentGreen.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .testTag("installed_badge_${app.id}")
                        ) {
                            Text(
                                text = stringResource(id = R.string.badge_installed),
                                color = AccentGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (app.isOpenSource) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent.copy(alpha = 0.2f))
                                .border(1.dp, colors.accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("FOSS", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.localizedCategoryTitle(),
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.displayVersionBadge,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " • ${app.size} • ",
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", app.rating),
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            AppActionPillButton(
                actionState = actionState,
                installedPackageName = installedPackageName,
                onDownloadClick = onDownloadClick,
                onInstallClick = onInstallClick,
                onOpenAppClick = onOpenAppClick,
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload
            )
        }
    }
}

@Composable
fun AppActionPillButton(
    actionState: AppActionState,
    installedPackageName: String?,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onOpenAppClick: (String) -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    when (actionState) {
        is AppActionState.Downloading -> {
            val pct = (actionState.progress.coerceIn(0f, 1f) * 100).toInt()
            Row(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .clickable {
                        if (actionState.isPaused) onResumeDownload() else onPauseDownload()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { actionState.progress.coerceIn(0f, 1f) },
                        color = colors.accent,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Icon(
                        imageVector = if (actionState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (actionState.isPaused) {
                            stringResource(R.string.download_resume)
                        } else {
                            stringResource(R.string.download_pause)
                        },
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$pct%",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AppActionState.Installing -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.installing_dots),
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        is AppActionState.InstallReady -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGreen)
                    .pressScaleClickable(withHaptic = true) { onInstallClick() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.install),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AppActionState.Open -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .pressScaleClickable(withHaptic = true) {
                        if (!installedPackageName.isNullOrBlank()) {
                            onOpenAppClick(installedPackageName)
                        } else {
                            onInstallClick()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.open),
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        is AppActionState.Update -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.buttonGradient)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .pressScaleClickable(withHaptic = true) { onDownloadClick() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = colors.textOnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.update),
                        color = colors.textOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        is AppActionState.Retry -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentRed.copy(alpha = 0.16f))
                    .border(1.dp, AccentRed.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .pressScaleClickable(withHaptic = true) { onResumeDownload() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.retry),
                        color = AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AppActionState.Download -> {
            Box(
                modifier = modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.buttonGradient)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .pressScaleClickable(withHaptic = true) { onDownloadClick() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = colors.textOnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.download),
                        color = colors.textOnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(id = R.string.store_sections),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(id = R.string.refresh),
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.prime_store_icon),
                contentDescription = stringResource(id = R.string.app_name),
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Prime ",
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Store",
                        color = colors.accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("search_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(id = R.string.search),
                tint = colors.textPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun StoreHeroBanner(
    featuredApp: AppItem?,
    actionState: AppActionState? = null,
    installedPackageName: String? = null,
    targetLanguageCode: String = "en",
    autoTranslateEnabled: Boolean = true,
    wifiOnlyTranslation: Boolean = false,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (String) -> Unit = onDownloadClick,
    onOpenAppClick: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onDetailsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current

    val baseSnippet = remember(featuredApp?.id, targetLanguageCode) {
        if (featuredApp == null) {
            ""
        } else if (targetLanguageCode.equals("ar", ignoreCase = true)) {
            featuredApp.descriptionAr.ifBlank { featuredApp.descriptionEn }
        } else {
            featuredApp.descriptionEn.ifBlank { featuredApp.descriptionAr }
        }.take(120)
    }

    var translatedHeroDesc by remember(featuredApp?.id, targetLanguageCode, autoTranslateEnabled) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(featuredApp?.id, targetLanguageCode, autoTranslateEnabled, wifiOnlyTranslation) {
        if (featuredApp == null || !autoTranslateEnabled || baseSnippet.isBlank()) {
            translatedHeroDesc = null
            return@LaunchedEffect
        }
        val outcome = MlKitTranslatorHelper.translateField(
            context = context,
            appId = featuredApp.id,
            field = "hero_desc",
            originalText = baseSnippet,
            targetLanguageCode = targetLanguageCode,
            wifiOnly = wifiOnlyTranslation
        )
        translatedHeroDesc = if (outcome is TranslationOutcome.Success) {
            outcome.translatedText
        } else {
            null
        }
    }

    val shownDesc = translatedHeroDesc?.ifBlank { null }
        ?: baseSnippet.ifBlank { null }
        ?: stringResource(id = R.string.app_subtitle)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            hasGlow = true,
            onClick = {
                if (featuredApp != null) onDetailsClick(featuredApp.id)
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(colors.card)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.buttonGradient)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (featuredApp?.isOpenSource == true) {
                                        "🔓 FOSS"
                                    } else {
                                        "👑 " + stringResource(id = R.string.featured_app)
                                    },
                                    color = colors.textOnAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!installedPackageName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentGreen.copy(alpha = 0.2f))
                                        .border(1.dp, AccentGreen.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.badge_installed),
                                        color = AccentGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = featuredApp?.nameAr?.ifBlank { featuredApp.name }
                                ?: stringResource(id = R.string.app_name),
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = shownDesc,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (featuredApp != null) {
                            val effectiveState = actionState ?: AppActionState.Download
                            val ctaText = when (effectiveState) {
                                AppActionState.Open -> stringResource(id = R.string.open_app)
                                is AppActionState.Update -> "${stringResource(id = R.string.update)} (${featuredApp.size})"
                                is AppActionState.Downloading -> "${(effectiveState.progress * 100).toInt()}%"
                                AppActionState.Installing -> stringResource(id = R.string.installing_dots)
                                is AppActionState.InstallReady -> stringResource(id = R.string.install)
                                is AppActionState.Retry -> stringResource(id = R.string.retry)
                                AppActionState.Download -> "${stringResource(id = R.string.download)} (${featuredApp.size})"
                            }
                            val ctaIcon = when (effectiveState) {
                                AppActionState.Open, is AppActionState.InstallReady -> Icons.Filled.OpenInNew
                                is AppActionState.Update -> Icons.Filled.SystemUpdate
                                is AppActionState.Retry -> Icons.Filled.Refresh
                                is AppActionState.Downloading -> if (effectiveState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause
                                else -> Icons.Filled.Download
                            }

                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                    .pressScaleClickable(withHaptic = true) {
                                        when (effectiveState) {
                                            AppActionState.Open -> {
                                                if (!installedPackageName.isNullOrBlank()) {
                                                    onOpenAppClick(installedPackageName)
                                                } else {
                                                    onDetailsClick(featuredApp.id)
                                                }
                                            }
                                            is AppActionState.InstallReady -> onInstallClick(featuredApp.id)
                                            is AppActionState.Downloading -> {
                                                if (effectiveState.isPaused) {
                                                    onResumeDownload(featuredApp.id)
                                                } else {
                                                    onPauseDownload(featuredApp.id)
                                                }
                                            }
                                            AppActionState.Installing -> Unit
                                            is AppActionState.Retry -> onResumeDownload(featuredApp.id)
                                            is AppActionState.Update, AppActionState.Download -> onDownloadClick(featuredApp.id)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = ctaIcon,
                                        contentDescription = null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ctaText,
                                        color = colors.textOnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (featuredApp != null) {
                        Spacer(modifier = Modifier.width(14.dp))
                        AppIconBadge(
                            emoji = featuredApp.iconEmoji,
                            accentColor = featuredApp.accentColor,
                            size = 72.dp,
                            shape = RoundedCornerShape(18.dp),
                            imageUrl = featuredApp.iconUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSectionTabsRow(
    activeSection: HomeSection,
    onSelectSection: (HomeSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardAlt)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            Triple(HomeSection.GAMES, "🎮", stringResource(R.string.tab_games)) to "home_tab_games",
            Triple(HomeSection.APPS, "📱", stringResource(R.string.tab_apps_only)) to "home_tab_apps"
        ).forEach { (tabMeta, tag) ->
            val (section, emoji, label) = tabMeta
            val selected = activeSection == section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (selected) Modifier.background(colors.buttonGradient) else Modifier.background(Color.Transparent)
                    )
                    .clickable { onSelectSection(section) }
                    .padding(vertical = 10.dp)
                    .testTag(tag),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = emoji, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (selected) colors.textOnAccent else colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun StoreCategoriesSection(
    homeSection: HomeSection = HomeSection.GAMES,
    categoryPairs: List<Pair<CategoryItem, String>> = emptyList(),
    onCategoryClick: (AppCategory) -> Unit,
    onCategoryItemClick: (CategoryItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val allSectionLabel = stringResource(
        id = if (homeSection == HomeSection.GAMES) R.string.hub_all_games_title else R.string.hub_all_apps_title
    )
    val allEmoji = if (homeSection == HomeSection.GAMES) "🎮" else "📱"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(id = R.string.store_sections),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val tiles = categoryPairs.take(7)
        val row1 = tiles.take(4)
        val row2 = tiles.drop(4).take(3)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row1.forEach { (item, title) ->
                CategorySectionTile(
                    emoji = item.emoji,
                    title = title,
                    onClick = { onCategoryItemClick(item) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row2.forEach { (item, title) ->
                CategorySectionTile(
                    emoji = item.emoji,
                    title = title,
                    onClick = { onCategoryItemClick(item) }
                )
            }
            CategorySectionTile(
                emoji = allEmoji,
                title = allSectionLabel,
                onClick = {
                    onCategoryClick(
                        if (homeSection == HomeSection.GAMES) AppCategory.GAMES else AppCategory.APPS
                    )
                }
            )
        }
    }
}

@Composable
private fun CategorySectionTile(
    emoji: String,
    title: String,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.accent)
            ) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 26.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HomeCategoryChip(
    item: CategoryItem,
    displayTitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accent else colors.cardAlt)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.emoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = displayTitle?.takeIf { it.isNotBlank() } ?: stringResource(id = item.titleRes),
                color = if (selected) colors.textOnAccent else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
