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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

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
                    contentDescription = "رجوع",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = selectedCategory?.titleAr ?: "الأقسام الموحدة",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("category_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "بحث",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (selectedCategory == null) {
            // Grouped Categories List: عام, تطبيقات, ألعاب
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Group 1: عام
                item {
                    CategoryGroupHeader(title = "🌐 أقسام عامة", count = StoreCategories.GENERAL.size - 1)
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

                // Group 2: تطبيقات
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryGroupHeader(title = "🤖 أقسام التطبيقات (${StoreCategories.APPS.size})", count = StoreCategories.APPS.size)
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

                // Group 3: ألعاب
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoryGroupHeader(title = "🎮 أقسام الألعاب (${StoreCategories.GAMES.size})", count = StoreCategories.GAMES.size)
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
            // Filtered Category Screen with Apps & Sort Chips
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = NeonPurplePrimary,
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
                    .background(Color(0xFF221733))
                    .border(1.dp, NeonBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.titleAr,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.titleEn} • $appCount تطبيق متوفر",
                    color = PrimeTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = NeonPurplePrimary,
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
    var openSourceOnly by remember { mutableStateOf(false) }

    val filteredApps = remember(category, allApps, sortOrder, openSourceOnly) {
        val baseList = when (category) {
            AppCategory.POPULAR -> allApps.sortedByDescending { it.rating }
            AppCategory.OPEN_SOURCE -> allApps.filter { it.isOpenSource }
            AppCategory.ROOT_TOOLS -> allApps.filter { it.category == AppCategory.ROOT_TOOLS || it.categoryKey == "root_tools" }
            else -> allApps.filter {
                it.category == category ||
                        it.categoryKey.lowercase() == category.name.lowercase() ||
                        it.tags.any { tag -> tag.contains(category.titleAr) }
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
        // Sort & Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortChipItem(
                label = "الأكثر شعبية",
                selected = sortOrder == SortOrder.POPULAR,
                onClick = { onSortChange(SortOrder.POPULAR) }
            )
            SortChipItem(
                label = "الأعلى تقييماً",
                selected = sortOrder == SortOrder.RATING,
                onClick = { onSortChange(SortOrder.RATING) }
            )
            SortChipItem(
                label = "الأحدث",
                selected = sortOrder == SortOrder.NEWEST,
                onClick = { onSortChange(SortOrder.NEWEST) }
            )
            if (category != AppCategory.OPEN_SOURCE) {
                SortChipItem(
                    label = "🔓 FOSS فقط",
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
                            .background(Color(0xFF221733))
                            .border(1.dp, NeonBorder, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = category.iconResId),
                            contentDescription = null,
                            tint = Color(category.accentHex),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "لا توجد تطبيقات منشورة في ${category.titleAr} بعد",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تظهر هنا فقط التطبيقات التي ينشرها الأدمن مباشرة",
                        color = PrimeTextMuted,
                        fontSize = 12.sp
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
                                        color = Color.White,
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
                                                .background(Color(0xFF1E3A8A))
                                                .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "FOSS",
                                                color = Color(0xFF93C5FD),
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
                                        color = Color(0xFFD8B4FE),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = " • ${app.size} • ",
                                        color = PrimeTextMuted,
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
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = app.displayCategoryTitle,
                                    color = NeonPurplePrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF281C38))
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable { onDownloadClick(app.id) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "تثبيت",
                                        tint = NeonPurplePrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تحميل",
                                        color = Color.White,
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
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonPurplePrimary else Color(0xFF1E162A))
            .border(1.dp, if (selected) NeonBorderGlow else NeonBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else PrimeTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
