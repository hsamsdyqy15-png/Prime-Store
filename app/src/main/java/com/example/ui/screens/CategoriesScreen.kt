package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.StoreCategories
import com.example.data.model.resolveAppActionState
import com.example.data.repository.CategoriesRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.CategoryDestination
import com.example.ui.SortOrder
import com.example.ui.components.NeonCard
import com.example.ui.theme.PrimeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    selectedCategory: AppCategory?,
    categoryDestination: CategoryDestination? = null,
    selectedHubFilterKey: String = "all",
    allApps: List<AppItem>,
    visibleGameCategories: List<CategoryEntity> = emptyList(),
    visibleAppCategories: List<CategoryEntity> = emptyList(),
    downloads: List<DownloadItem> = emptyList(),
    installedPkgByAppId: Map<String, InstalledPackageSummary> = emptyMap(),
    downloadedReadyIds: Set<String> = emptySet(),
    installingAppIds: Set<String> = emptySet(),
    signatureConflictAppIds: Set<String> = emptySet(),
    sortOrder: SortOrder,
    isPullRefreshing: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    onCategoryClick: (AppCategory) -> Unit,
    onCategoryItemClick: (CategoryItem) -> Unit = {},
    onSelectHubFilter: (String) -> Unit = {},
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (String) -> Unit = onDownloadClick,
    onOpenAppClick: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onSortChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current
    val categoriesRepo = remember(context) { CategoriesRepository.getInstance(context) }
    var activeRootTab by remember { mutableIntStateOf(0) } // 0 = Games, 1 = Apps

    val activeDest = categoryDestination ?: selectedCategory?.let { cat ->
        when (cat) {
            AppCategory.APPS, AppCategory.MORE -> CategoryDestination.AllAppsHub
            AppCategory.GAMES -> CategoryDestination.AllGamesHub
            else -> CategoryDestination.SingleCategory(cat, null)
        }
    }

    val topBarTitle = when (activeDest) {
        null -> stringResource(id = R.string.tab_categories)
        CategoryDestination.AllAppsHub -> stringResource(id = R.string.hub_all_apps_title)
        CategoryDestination.AllGamesHub -> stringResource(id = R.string.hub_all_games_title)
        is CategoryDestination.SingleCategory -> activeDest.categoryItem?.let { item ->
            if (item.isCustomName) {
                val lang = SettingsRepository.getInstance(context).getEffectiveLanguageCode()
                if (lang == "ar" || lang == "fa" || lang == "ur") {
                    item.titleAr.ifBlank { item.titleEn }
                } else {
                    item.titleEn.ifBlank { item.titleAr }
                }
            } else {
                stringResource(id = item.titleRes)
            }
        } ?: stringResource(id = activeDest.category.titleRes)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("categories_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("category_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.close),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = topBarTitle,
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("category_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(id = R.string.search),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (activeDest == null) {
            val gameCatPairs = remember(visibleGameCategories) {
                if (visibleGameCategories.isNotEmpty()) {
                    visibleGameCategories.map { entity ->
                        categoriesRepo.toCategoryItem(entity) to CategoriesRepository.resolveDisplayTitle(context, entity)
                    }
                } else {
                    StoreCategories.GAMES.map { item -> item to context.getString(item.titleRes) }
                }
            }
            val appCatPairs = remember(visibleAppCategories) {
                if (visibleAppCategories.isNotEmpty()) {
                    visibleAppCategories.map { entity ->
                        categoriesRepo.toCategoryItem(entity) to CategoriesRepository.resolveDisplayTitle(context, entity)
                    }
                } else {
                    StoreCategories.APPS.map { item -> item to context.getString(item.titleRes) }
                }
            }

            // Segmented Games vs Apps Tabs (strict separation)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val gamesSelected = activeRootTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (gamesSelected) colors.accent else colors.cardAlt)
                        .clickable { activeRootTab = 0 }
                        .padding(vertical = 8.dp)
                        .testTag("categories_tab_games"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎮 ${stringResource(R.string.tab_games)} (${gameCatPairs.size})",
                        color = if (gamesSelected) colors.textOnAccent else colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val appsSelected = activeRootTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (appsSelected) colors.accent else colors.cardAlt)
                        .clickable { activeRootTab = 1 }
                        .padding(vertical = 8.dp)
                        .testTag("categories_tab_apps"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📱 ${stringResource(R.string.tab_apps_only)} (${appCatPairs.size})",
                        color = if (appsSelected) colors.textOnAccent else colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeRootTab == 0) {
                        item(key = "hub_all_games", contentType = "category_card") {
                            val allGamesCount = remember(allApps) {
                                allApps.count { StoreCategories.isGameApp(it) }
                            }
                            HubEntryCard(
                                emoji = "🎮",
                                title = stringResource(id = R.string.hub_all_games_title),
                                subtitle = "${stringResource(id = R.string.cat_group_games)} • ${stringResource(id = R.string.cat_app_count, allGamesCount)}",
                                onClick = { onCategoryClick(AppCategory.GAMES) }
                            )
                        }

                        items(
                            items = gameCatPairs,
                            key = { (item, _) -> "game_cat_${item.key}" },
                            contentType = { "category_card" }
                        ) { (item, displayTitle) ->
                            val count = remember(allApps, item.key) {
                                allApps.count { StoreCategories.matchesCategoryFilter(it, item.key, StoreCategories.ITEM_TYPE_GAME) }
                            }
                            UnifiedCategoryCard(
                                item = item,
                                displayTitle = displayTitle,
                                appCount = count,
                                onClick = { onCategoryItemClick(item) }
                            )
                        }
                    } else {
                        item(key = "hub_all_apps", contentType = "category_card") {
                            val allAppsCount = remember(allApps) {
                                allApps.count { StoreCategories.isNonGameApp(it) }
                            }
                            HubEntryCard(
                                emoji = "📱",
                                title = stringResource(id = R.string.hub_all_apps_title),
                                subtitle = "${stringResource(id = R.string.cat_group_apps)} • ${stringResource(id = R.string.cat_app_count, allAppsCount)}",
                                onClick = { onCategoryClick(AppCategory.APPS) }
                            )
                        }

                        items(
                            items = appCatPairs,
                            key = { (item, _) -> "app_cat_${item.key}" },
                            contentType = { "category_card" }
                        ) { (item, displayTitle) ->
                            val count = remember(allApps, item.key) {
                                allApps.count { StoreCategories.matchesCategoryFilter(it, item.key, StoreCategories.ITEM_TYPE_APP) }
                            }
                            UnifiedCategoryCard(
                                item = item,
                                displayTitle = displayTitle,
                                appCount = count,
                                onClick = { onCategoryItemClick(item) }
                            )
                        }
                    }

                    item(key = "cat_root_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        } else {
            CategoryAppsList(
                destination = activeDest,
                selectedHubFilterKey = selectedHubFilterKey,
                allApps = allApps,
                visibleGameCategories = visibleGameCategories,
                visibleAppCategories = visibleAppCategories,
                downloads = downloads,
                installedPkgByAppId = installedPkgByAppId,
                downloadedReadyIds = downloadedReadyIds,
                installingAppIds = installingAppIds,
                signatureConflictAppIds = signatureConflictAppIds,
                sortOrder = sortOrder,
                isPullRefreshing = isPullRefreshing,
                onSelectHubFilter = onSelectHubFilter,
                onRefresh = onRefresh,
                onSortChange = onSortChange,
                onAppClick = onAppClick,
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
fun CategoryGroupHeader(title: String) {
    val colors = PrimeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HubEntryCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    NeonCard(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        hasGlow = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun UnifiedCategoryCard(
    item: CategoryItem,
    appCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    displayTitle: String? = null
) {
    val colors = PrimeTheme.colors

    NeonCard(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        hasGlow = false,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle ?: stringResource(id = item.titleRes),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stringResource(id = item.groupRes)} • ${stringResource(id = R.string.cat_app_count, appCount)}",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAppsList(
    destination: CategoryDestination,
    selectedHubFilterKey: String,
    allApps: List<AppItem>,
    visibleGameCategories: List<CategoryEntity> = emptyList(),
    visibleAppCategories: List<CategoryEntity> = emptyList(),
    downloads: List<DownloadItem>,
    installedPkgByAppId: Map<String, InstalledPackageSummary>,
    downloadedReadyIds: Set<String>,
    installingAppIds: Set<String>,
    signatureConflictAppIds: Set<String>,
    sortOrder: SortOrder,
    isPullRefreshing: Boolean,
    onSelectHubFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (String) -> Unit,
    onOpenAppClick: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current
    val categoriesRepo = remember(context) { CategoriesRepository.getInstance(context) }
    var openSourceOnly by remember { mutableStateOf(false) }

    val hubBaseApps = remember(destination, allApps) {
        when (destination) {
            CategoryDestination.AllAppsHub -> allApps.filter { StoreCategories.isNonGameApp(it) }
            CategoryDestination.AllGamesHub -> allApps.filter { StoreCategories.isGameApp(it) }
            is CategoryDestination.SingleCategory -> {
                val itemKey = destination.categoryItem?.key
                val reqType = destination.categoryItem?.type
                    ?: if (StoreCategories.isGameCategory(destination.category.name, destination.category)) {
                        StoreCategories.ITEM_TYPE_GAME
                    } else null
                if (!itemKey.isNullOrBlank()) {
                    allApps.filter { StoreCategories.matchesCategoryFilter(it, itemKey, reqType) }
                } else {
                    val cat = destination.category
                    val catTitle = context.getString(cat.titleRes)
                    when (cat) {
                        AppCategory.POPULAR -> allApps.filter { StoreCategories.matchesCategoryFilter(it, "trending") }
                            .ifEmpty { allApps.sortedByDescending { it.downloadCountLong } }
                        AppCategory.OPEN_SOURCE -> allApps.filter { it.isOpenSource && StoreCategories.isNonGameApp(it) }
                        AppCategory.ROOT_TOOLS -> allApps.filter { StoreCategories.matchesCategoryFilter(it, "root_tools", StoreCategories.ITEM_TYPE_APP) }
                        AppCategory.NEW_GAMES -> allApps.filter { StoreCategories.isGameApp(it) && it.isNew }
                        else -> allApps.filter {
                            StoreCategories.matchesCategoryFilter(it, cat.name.lowercase(), reqType) ||
                                (it.category == cat && (reqType == null || (reqType == StoreCategories.ITEM_TYPE_GAME) == StoreCategories.isGameApp(it))) ||
                                ((reqType == null || (reqType == StoreCategories.ITEM_TYPE_GAME) == StoreCategories.isGameApp(it)) && it.tags.any { tag ->
                                    tag.contains(catTitle, ignoreCase = true) ||
                                        tag.contains(cat.titleEn, ignoreCase = true)
                                })
                        }
                    }
                }
            }
        }
    }

    val hubChipDefs = remember(destination, hubBaseApps, visibleGameCategories, visibleAppCategories) {
        val rawChips: List<Pair<CategoryItem, String>> = when (destination) {
            CategoryDestination.AllAppsHub -> if (visibleAppCategories.isNotEmpty()) {
                visibleAppCategories.map { categoriesRepo.toCategoryItem(it) to CategoriesRepository.resolveDisplayTitle(context, it) }
            } else {
                StoreCategories.APPS.map { it to context.getString(it.titleRes) }
            }
            CategoryDestination.AllGamesHub -> if (visibleGameCategories.isNotEmpty()) {
                visibleGameCategories.map { categoriesRepo.toCategoryItem(it) to CategoriesRepository.resolveDisplayTitle(context, it) }
            } else {
                StoreCategories.GAMES.map { it to context.getString(it.titleRes) }
            }
            is CategoryDestination.SingleCategory -> emptyList()
        }
        val requiredType = when (destination) {
            CategoryDestination.AllGamesHub -> StoreCategories.ITEM_TYPE_GAME
            CategoryDestination.AllAppsHub -> StoreCategories.ITEM_TYPE_APP
            is CategoryDestination.SingleCategory -> null
        }
        rawChips.map { (chip, label) ->
            val count = hubBaseApps.count { StoreCategories.matchesCategoryFilter(it, chip.key, requiredType) }
            Triple(chip, label, count)
        }.sortedByDescending { it.third > 0 }
    }

    val filteredApps = remember(destination, selectedHubFilterKey, hubBaseApps, sortOrder, openSourceOnly) {
        val hubFiltered = when (destination) {
            CategoryDestination.AllAppsHub -> {
                if (selectedHubFilterKey.equals("all", ignoreCase = true)) {
                    hubBaseApps
                } else {
                    hubBaseApps.filter { StoreCategories.matchesCategoryFilter(it, selectedHubFilterKey, StoreCategories.ITEM_TYPE_APP) }
                }
            }
            CategoryDestination.AllGamesHub -> {
                if (selectedHubFilterKey.equals("all", ignoreCase = true)) {
                    hubBaseApps
                } else {
                    hubBaseApps.filter { StoreCategories.matchesCategoryFilter(it, selectedHubFilterKey, StoreCategories.ITEM_TYPE_GAME) }
                }
            }
            is CategoryDestination.SingleCategory -> hubBaseApps
        }
        val sourceFiltered = if (openSourceOnly) {
            hubFiltered.filter { it.isOpenSource }
        } else {
            hubFiltered
        }
        when (sortOrder) {
            SortOrder.POPULAR -> sourceFiltered.sortedByDescending { it.downloadCountLong }
            SortOrder.RATING -> sourceFiltered.sortedByDescending { it.rating }
            SortOrder.NEWEST -> sourceFiltered.sortedByDescending { maxOf(it.updatedAtMillis, it.createdAtMillis) }
        }
    }

    var displayLimit by remember(destination, selectedHubFilterKey, sortOrder, openSourceOnly) {
        mutableIntStateOf(20)
    }
    val visibleApps = remember(filteredApps, displayLimit) {
        filteredApps.take(displayLimit)
    }

    val showFossToggle = (destination as? CategoryDestination.SingleCategory)?.category != AppCategory.OPEN_SOURCE

    Column(modifier = Modifier.fillMaxSize()) {
        // Hub horizontal sub-category chips
        if (hubChipDefs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "hub_chip_all", contentType = "chip") {
                    val isAllSelected = selectedHubFilterKey.equals("all", ignoreCase = true)
                    SortChipItem(
                        label = stringResource(R.string.chip_all_with_count, hubBaseApps.size),
                        selected = isAllSelected,
                        onClick = { onSelectHubFilter("all") }
                    )
                }
                items(
                    items = hubChipDefs,
                    key = { (chip, _, _) -> "hub_chip_${chip.key}" },
                    contentType = { "chip" }
                ) { (chip, chipName, count) ->
                    val isSelected = selectedHubFilterKey.equals(chip.key, ignoreCase = true)
                    SortChipItem(
                        label = "${chip.emoji} ${stringResource(R.string.category_chip_with_count, chipName, count)}",
                        selected = isSelected,
                        onClick = { onSelectHubFilter(chip.key) }
                    )
                }
            }
        }

        // Sort + FOSS row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "sort_pop") {
                SortChipItem(
                    label = stringResource(id = R.string.cat_popular),
                    selected = sortOrder == SortOrder.POPULAR,
                    onClick = { onSortChange(SortOrder.POPULAR) }
                )
            }
            item(key = "sort_rating") {
                SortChipItem(
                    label = stringResource(id = R.string.filter_top_rated),
                    selected = sortOrder == SortOrder.RATING,
                    onClick = { onSortChange(SortOrder.RATING) }
                )
            }
            item(key = "sort_newest") {
                SortChipItem(
                    label = stringResource(id = R.string.filter_newest),
                    selected = sortOrder == SortOrder.NEWEST,
                    onClick = { onSortChange(SortOrder.NEWEST) }
                )
            }
            if (showFossToggle) {
                item(key = "sort_foss") {
                    SortChipItem(
                        label = "🔓 FOSS",
                        selected = openSourceOnly,
                        onClick = { openSourceOnly = !openSourceOnly }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (visibleApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyCategory = (destination as? CategoryDestination.SingleCategory)?.category
                        ?: if (destination == CategoryDestination.AllGamesHub) AppCategory.GAMES else AppCategory.APPS
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.cardAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = emptyCategory.iconResId),
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(id = R.string.home_no_apps_in_section),
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = visibleApps,
                        key = { _, it -> it.id },
                        contentType = { _, _ -> "cat_app" }
                    ) { index, app ->
                        if (index >= visibleApps.lastIndex - 3 && visibleApps.size < filteredApps.size) {
                            LaunchedEffect(visibleApps.size) {
                                displayLimit += 20
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
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "cat_detail_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SortChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent else colors.cardAlt)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.textOnAccent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
