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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.StoreCategories
import com.example.ui.SortOrder
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.PrimeTheme

@Composable
fun CategoriesScreen(
    selectedCategory: AppCategory?,
    allApps: List<AppItem>,
    sortOrder: SortOrder,
    onCategoryClick: (AppCategory) -> Unit,
    onCategoryItemClick: (CategoryItem) -> Unit = {},
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

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
                modifier = Modifier.testTag("category_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.close),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = selectedCategory?.let { stringResource(id = it.titleRes) } ?: stringResource(id = R.string.tab_categories),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("category_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(id = R.string.search),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (selectedCategory == null) {
            // Grouped Categories List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    CategoryGroupHeader(title = "🌐 " + stringResource(id = R.string.store_sections), count = StoreCategories.GENERAL.size - 1)
                }
                items(StoreCategories.GENERAL.filter { it.key != "all" }) { item ->
                    UnifiedCategoryCard(
                        item = item,
                        appCount = allApps.count {
                            if (item.key == "trending") it.isTrending
                            else it.categoryKey.lowercase() == item.key.lowercase() || it.category.name.lowercase() == item.key.lowercase()
                        },
                        onClick = {
                            val matchingEnum = when (item.key) {
                                "games" -> AppCategory.GAMES
                                "apps" -> AppCategory.APPS
                                "trending" -> AppCategory.POPULAR
                                else -> AppCategory.MORE
                            }
                            onCategoryClick(matchingEnum)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryGroupHeader(title = "🤖 " + stringResource(id = R.string.cat_apps) + " (${StoreCategories.APPS.size})", count = StoreCategories.APPS.size)
                }
                items(StoreCategories.APPS) { item ->
                    UnifiedCategoryCard(
                        item = item,
                        appCount = allApps.count {
                            it.categoryKey.lowercase() == item.key.lowercase() || it.category.name.lowercase() == item.key.lowercase()
                        },
                        onClick = {
                            val cat = when (item.key) {
                                "business" -> AppCategory.BUSINESS
                                "education" -> AppCategory.EDUCATION
                                "entertainment" -> AppCategory.ENTERTAINMENT
                                "music_audio" -> AppCategory.MUSIC
                                "personalization" -> AppCategory.PERSONALIZATION
                                "productivity" -> AppCategory.PRODUCTIVITY
                                "social" -> AppCategory.SOCIAL
                                "sports" -> AppCategory.SPORTS
                                "tools" -> AppCategory.TOOLS
                                "video_players_editors" -> AppCategory.VIDEO
                                else -> AppCategory.APPS
                            }
                            onCategoryClick(cat)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryGroupHeader(title = "🎮 " + stringResource(id = R.string.cat_games) + " (${StoreCategories.GAMES.size})", count = StoreCategories.GAMES.size)
                }
                items(StoreCategories.GAMES) { item ->
                    UnifiedCategoryCard(
                        item = item,
                        appCount = allApps.count {
                            it.categoryKey.lowercase() == item.key.lowercase() || it.category.name.lowercase() == item.key.lowercase()
                        },
                        onClick = {
                            val cat = when (item.key) {
                                "game_action" -> AppCategory.ACTION
                                "game_role_playing" -> AppCategory.RPG
                                "game_simulation" -> AppCategory.SIMULATION
                                "game_sports" -> AppCategory.SPORTS
                                else -> AppCategory.GAMES
                            }
                            onCategoryClick(cat)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        } else {
            CategoryAppsList(
                category = selectedCategory,
                allApps = allApps,
                sortOrder = sortOrder,
                onSortChange = onSortChange,
                onAppClick = onAppClick,
                onDownloadClick = onDownloadClick
            )
        }
    }
}

@Composable
fun CategoryGroupHeader(title: String, count: Int) {
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
fun UnifiedCategoryCard(
    item: CategoryItem,
    appCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
                    text = stringResource(id = item.titleRes),
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

@Composable
fun CategoryAppsList(
    category: AppCategory,
    allApps: List<AppItem>,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit
) {
    val colors = PrimeTheme.colors
    var openSourceOnly by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filteredApps = remember(category, allApps, sortOrder, openSourceOnly) {
        val catTitle = context.getString(category.titleRes)
        val baseList = when (category) {
            AppCategory.POPULAR -> allApps.sortedByDescending { it.rating }
            AppCategory.OPEN_SOURCE -> allApps.filter { it.isOpenSource }
            AppCategory.ROOT_TOOLS -> allApps.filter { it.category == AppCategory.ROOT_TOOLS || it.categoryKey == "root_tools" }
            else -> allApps.filter {
                it.category == category ||
                        it.categoryKey.lowercase() == category.name.lowercase() ||
                        it.tags.any { tag ->
                            tag.contains(catTitle, ignoreCase = true) ||
                                tag.contains(category.titleEn, ignoreCase = true)
                        }
            }
        }
        val sourceFiltered = if (openSourceOnly) {
            baseList.filter { it.isOpenSource }
        } else {
            baseList
        }
        when (sortOrder) {
            SortOrder.POPULAR -> sourceFiltered.sortedByDescending { it.ratingCount }
            SortOrder.RATING -> sourceFiltered.sortedByDescending { it.rating }
            SortOrder.NEWEST -> sourceFiltered.sortedByDescending { it.isNew }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortChipItem(
                label = stringResource(id = R.string.cat_popular),
                selected = sortOrder == SortOrder.POPULAR,
                onClick = { onSortChange(SortOrder.POPULAR) }
            )
            SortChipItem(
                label = stringResource(id = R.string.filter_top_rated),
                selected = sortOrder == SortOrder.RATING,
                onClick = { onSortChange(SortOrder.RATING) }
            )
            SortChipItem(
                label = stringResource(id = R.string.filter_newest),
                selected = sortOrder == SortOrder.NEWEST,
                onClick = { onSortChange(SortOrder.NEWEST) }
            )
            if (category != AppCategory.OPEN_SOURCE) {
                SortChipItem(
                    label = "🔓 FOSS",
                    selected = openSourceOnly,
                    onClick = { openSourceOnly = !openSourceOnly }
                )
            }
        }

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
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
                            painter = painterResource(id = category.iconResId),
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(id = category.titleRes),
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
                items(filteredApps, key = { it.id }) { app ->
                    NeonCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        onClick = { onAppClick(app.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                        text = app.nameAr,
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
                                            Text(
                                                text = "FOSS",
                                                color = colors.accent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
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
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f", app.rating),
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = app.localizedCategoryTitle(),
                                    color = colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { onDownloadClick(app.id) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = stringResource(id = R.string.download),
                                        tint = colors.accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.download),
                                        color = colors.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
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
            .defaultMinSize(minHeight = 36.dp)
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
