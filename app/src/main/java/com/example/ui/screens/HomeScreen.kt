package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.data.installer.ApkInstaller
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.StoreCategories
import com.example.ui.HomeFilterType
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimeTheme

import androidx.annotation.StringRes

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

@Composable
fun HomeScreen(
    allApps: List<AppItem>,
    pagedApps: List<AppItem>,
    selectedCategoryKey: String = "all",
    isInitialLoading: Boolean = false,
    isLoadingNextPage: Boolean = false,
    hasMoreApps: Boolean = true,
    pagedError: String? = null,
    downloads: List<DownloadItem> = emptyList(),
    storeUpdateAvailable: Boolean = false,
    activeFilter: HomeFilterType = HomeFilterType.ALL,
    onStoreUpdateClick: () -> Unit = {},
    onFilterChange: (HomeFilterType) -> Unit = {},
    onAppClick: (String) -> Unit,
    onCategoryClick: (AppCategory) -> Unit,
    onCategorySelect: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onInstallClick: (String) -> Unit = onDownloadClick,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current

    val newlyAddedApps = remember(allApps) {
        if (allApps.isNotEmpty()) {
            allApps.take(6)
        } else {
            pagedApps.take(6)
        }
    }

    val filteredPagedApps = remember(pagedApps, activeFilter) {
        when (activeFilter) {
            HomeFilterType.ALL -> pagedApps
            HomeFilterType.SMALL_SIZE -> pagedApps.filter {
                val sizeClean = it.size.trim().lowercase()
                sizeClean.contains("kb") || (sizeClean.contains("mb") && (sizeClean.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 100.0) < 35.0)
            }
            HomeFilterType.TOP_RATED -> pagedApps.sortedByDescending { it.rating }
            HomeFilterType.NEWEST -> pagedApps.sortedByDescending { it.isNew }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Top Bar
        item {
            HomeTopBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onRefresh = onRefresh
            )
        }

        // Store update top banner
        if (storeUpdateAvailable) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.buttonGradient)
                        .border(1.dp, AccentGold, RoundedCornerShape(14.dp))
                        .clickable { onStoreUpdateClick() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🚀 ", fontSize = 20.sp)
                        Column {
                            Text(stringResource(id = R.string.update_available_title), color = colors.textOnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(stringResource(id = R.string.update_available_desc), color = colors.textOnAccent.copy(alpha = 0.9f), fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGold)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(stringResource(id = R.string.update), color = Color(0xFF11071F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 2. Banner & Quick Categories Grid
        item {
            StoreHeroBanner(
                featuredApp = allApps.firstOrNull { it.isFeatured } ?: allApps.firstOrNull() ?: pagedApps.firstOrNull(),
                onDownloadClick = onDownloadClick,
                onDetailsClick = onAppClick
            )
        }

        item {
            StoreCategoriesSection(
                onCategoryClick = onCategoryClick
            )
        }

        // 3. "أضيف حديثاً" Newly Added
        if (newlyAddedApps.isNotEmpty()) {
            item {
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

            items(newlyAddedApps, key = { "new_${it.id}" }) { app ->
                val dlItem = downloads.find { it.appId == app.id }
                ApptekaCard(
                    app = app,
                    downloadItem = dlItem,
                    onAppClick = { onAppClick(app.id) },
                    onDownloadClick = { onDownloadClick(app.id) },
                    onInstallClick = { onInstallClick(app.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // 4. "كل التطبيقات" All Apps
        item {
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
                    Text(text = "📱 ", fontSize = 18.sp)
                    Text(
                        text = stringResource(id = R.string.all_apps),
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val categoryFilterItems = remember {
                    listOf(
                        StoreCategories.find("all"),
                        StoreCategories.find("apps"),
                        StoreCategories.find("games"),
                        StoreCategories.find("trending"),
                        StoreCategories.find("tools"),
                        StoreCategories.find("open_source"),
                        StoreCategories.find("entertainment"),
                        StoreCategories.find("productivity"),
                        StoreCategories.find("social"),
                        StoreCategories.find("education"),
                        StoreCategories.find("music_audio"),
                        StoreCategories.find("video_players_editors"),
                        StoreCategories.find("root_tools")
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categoryFilterItems, key = { it.key }) { item ->
                        val isSelected = selectedCategoryKey.lowercase() == item.key.lowercase()
                        HomeCategoryChip(
                            item = item,
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
                    items(filterChips) { (type, label) ->
                        val isSelected = activeFilter == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.accent else colors.cardAlt)
                                .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                                .clickable { onFilterChange(type) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) colors.textOnAccent else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 5. Vertical List
        if (isInitialLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.loading),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (pagedApps.isEmpty()) {
            item {
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
                            text = if (pagedError != null) stringResource(R.string.home_fetch_error) else stringResource(R.string.home_no_apps_in_section),
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            itemsIndexed(filteredPagedApps, key = { _, app -> "paged_${app.id}" }) { index, app ->
                if (index >= pagedApps.size - 3 && hasMoreApps && !isLoadingNextPage) {
                    LaunchedEffect(index) {
                        onLoadNextPage()
                    }
                }

                val dlItem = downloads.find { it.appId == app.id }
                ApptekaCard(
                    app = app,
                    downloadItem = dlItem,
                    onAppClick = { onAppClick(app.id) },
                    onDownloadClick = { onDownloadClick(app.id) },
                    onInstallClick = { onInstallClick(app.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                if (isLoadingNextPage) {
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

@Composable
fun ApptekaCard(
    app: AppItem,
    downloadItem: DownloadItem?,
    onAppClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val context = LocalContext.current

    val isDownloadedReady = remember(downloadItem, app.id) {
        if (downloadItem?.isCompleted == true) return@remember true
        val localApk = ApkInstaller.getApkFile(context, app.id)
        localApk.exists() && localApk.length() > 0
    }

    val isDownloading = downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING
    val progress = downloadItem?.progress ?: 0f

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

            Column(
                modifier = Modifier.weight(1f)
            ) {
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

            // Action Button
            when {
                isDownloading -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.cardAlt)
                            .border(1.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = colors.accent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = colors.accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                isDownloadedReady -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGreen)
                            .clickable { onInstallClick() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
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

                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.buttonGradient)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .clickable { onDownloadClick() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
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
                modifier = Modifier.testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_button")
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
            modifier = Modifier.testTag("search_button")
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
    onDownloadClick: (String) -> Unit,
    onDetailsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

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
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.buttonGradient)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (featuredApp?.isOpenSource == true) "🔓 FOSS" else "👑 " + stringResource(id = R.string.featured_app),
                                color = colors.textOnAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = featuredApp?.nameAr ?: stringResource(id = R.string.app_name),
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = featuredApp?.descriptionAr?.take(70) ?: stringResource(id = R.string.app_subtitle),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (featuredApp != null) {
                            Box(
                                modifier = Modifier
                                    .shadow(if (colors.isDark) 6.dp else 2.dp, RoundedCornerShape(14.dp), ambientColor = colors.accentDark, spotColor = colors.accent)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = Color.White)
                                    ) { onDownloadClick(featuredApp.id) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${stringResource(id = R.string.download)} (${featuredApp.size})",
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
fun StoreCategoriesSection(
    onCategoryClick: (AppCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

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

        val row1 = QUICK_CATEGORIES.take(4)
        val row2 = QUICK_CATEGORIES.drop(4)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row1.forEach { item ->
                CategoryGridItem(item = item, onClick = { onCategoryClick(item.category) })
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row2.forEach { item ->
                CategoryGridItem(item = item, onClick = { onCategoryClick(item.category) })
            }
        }
    }
}

@Composable
fun CategoryGridItem(
    item: CategoryQuickItem,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = colors.accent)
            ) { onClick() }
    ) {
        val itemTitle = stringResource(id = item.titleRes)
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = if (colors.isDark) 8.dp else 2.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = colors.accentDark,
                    spotColor = colors.accent
                )
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = item.category.iconResId),
                contentDescription = itemTitle,
                tint = colors.accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = itemTitle,
            color = colors.textPrimary,
            fontSize = 12.sp,
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
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accent else colors.cardAlt)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.emoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(id = item.titleRes),
                color = if (selected) colors.textOnAccent else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
