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
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

data class CategoryQuickItem(
    val category: AppCategory,
    val title: String,
    val glowColor: Long
)

val QUICK_CATEGORIES = listOf(
    CategoryQuickItem(AppCategory.GAMES, "ألعاب", 0xFFA855F7),
    CategoryQuickItem(AppCategory.APPS, "تطبيقات", 0xFF22C55E),
    CategoryQuickItem(AppCategory.ROOT_TOOLS, "أدوات الروت", 0xFFDC2626),
    CategoryQuickItem(AppCategory.OPEN_SOURCE, "مفتوح المصدر", 0xFF3B82F6),
    CategoryQuickItem(AppCategory.POPULAR, "الأكثر شهرة", 0xFFFBBF24),
    CategoryQuickItem(AppCategory.NEW_GAMES, "الجديد", 0xFFF97316),
    CategoryQuickItem(AppCategory.TOOLS, "أدوات", 0xFF8B5CF6),
    CategoryQuickItem(AppCategory.MORE, "المزيد", 0xFFA855F7)
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
    val context = LocalContext.current

    // "أضيف حديثاً" top 6 newest apps
    val newlyAddedApps = remember(allApps) {
        if (allApps.isNotEmpty()) {
            allApps.take(6)
        } else {
            pagedApps.take(6)
        }
    }

    // Filtered paged apps
    val filteredPagedApps = remember(pagedApps, activeFilter) {
        when (activeFilter) {
            HomeFilterType.ALL -> pagedApps
            HomeFilterType.SMALL_SIZE -> pagedApps.filter {
                val sizeClean = it.size.trim().lowercase()
                sizeClean.contains("kb") || (sizeClean.contains("mb") && (sizeClean.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 100.0) < 35.0)
            }
            HomeFilterType.TOP_RATED -> pagedApps.sortedByDescending { it.rating }
            HomeFilterType.NEWEST -> pagedApps.sortedByDescending { it.versionCode }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // =====================================================================
        // 1. Top Bar
        // =====================================================================
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
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF3B0764), Color(0xFF6B21A8), Color(0xFFA855F7))
                            )
                        )
                        .border(1.dp, AccentGold, RoundedCornerShape(14.dp))
                        .clickable { onStoreUpdateClick() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🚀 ", fontSize = 20.sp)
                        Column {
                            Text("يتوفر تحديث لمتجر Prime Store!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("اضغط هنا للتحميل والتثبيت المباشر", color = Color(0xFFF3E8FF), fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGold)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("تحديث الآن", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // =====================================================================
        // 2. (A) Banner & Quick Categories Grid
        // =====================================================================
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

        // =====================================================================
        // 3. (B) قسم "أضيف حديثاً" فوق: عنوان بأيقونة، وتحته أحدث 6 تطبيقات كبطاقات عمودية
        // =====================================================================
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
                        text = "أضيف حديثاً",
                        color = Color.White,
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

        // =====================================================================
        // 4. (C) قسم "كل التطبيقات" تحته:
        // صف شرائح (chips) أفقي للأصناف يبدأ بشريحة "الكل"
        // =====================================================================
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
                        text = "كل التطبيقات",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Category chips row
                val categoryFilterItems = remember {
                    listOf(
                        CategoryItem("all", "الكل", "All", "🌐", "عام"),
                        CategoryItem("apps", "تطبيقات", "Apps", "🤖", "عام"),
                        CategoryItem("games", "ألعاب", "Games", "🎮", "عام"),
                        CategoryItem("trending", "الأكثر شعبية", "Trending", "🔥", "عام"),
                        CategoryItem("tools", "أدوات", "Tools", "🔧", "تطبيقات"),
                        CategoryItem("open_source", "مفتوح المصدر", "FOSS", "🔓", "تطبيقات"),
                        CategoryItem("entertainment", "ترفيه", "Entertainment", "▶️", "تطبيقات"),
                        CategoryItem("productivity", "إنتاجية", "Productivity", "📊", "تطبيقات"),
                        CategoryItem("social", "تواصل", "Social", "👥", "تطبيقات"),
                        CategoryItem("education", "تعليم", "Education", "🎓", "تطبيقات"),
                        CategoryItem("music_audio", "صوتيات", "Music", "🎵", "تطبيقات"),
                        CategoryItem("video_players_editors", "فيديو", "Video", "🎬", "تطبيقات"),
                        CategoryItem("root_tools", "أدوات الروت", "Root", "⚡", "تطبيقات")
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

                // Filter & Sort chips row
                val filterChips = remember {
                    listOf(
                        Pair(HomeFilterType.ALL, "الكل 🌐"),
                        Pair(HomeFilterType.SMALL_SIZE, "حجم خفيف (<35MB) ⚡"),
                        Pair(HomeFilterType.TOP_RATED, "الأعلى تقييماً ⭐"),
                        Pair(HomeFilterType.NEWEST, "الأحدث إصداراً 🆕")
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    items(filterChips) { (type, label) ->
                        val isSelected = activeFilter == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeonPurplePrimary else Color(0xFF1E142D))
                                .border(1.dp, if (isSelected) NeonBorderGlow else NeonBorder, RoundedCornerShape(12.dp))
                                .clickable { onFilterChange(type) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else PrimeTextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // =====================================================================
        // 5. Vertical List of Appteka Cards with Infinite Scrolling
        // =====================================================================
        if (isInitialLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = NeonPurplePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تحميل التطبيقات...",
                        color = PrimeTextSecondary,
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
                            text = if (pagedError != null) "تعذر جلب التطبيقات" else "لا توجد تطبيقات في هذا القسم حالياً",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pagedError ?: "اختر قسماً آخر أو حدّث البيانات",
                            color = PrimeTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        if (pagedError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimeGradientBrush)
                                    .clickable { onRefresh() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("إعادة المحاولة 🔄", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(filteredPagedApps, key = { _, app -> "paged_${app.id}" }) { index, app ->
                // Infinite scroll trigger: when reaching last 3 items
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

            // Bottom loading indicator or "لا يوجد المزيد"
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
                            color = NeonPurplePrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "جاري جلب المزيد...",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else if (!hasMoreApps && pagedApps.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا يوجد المزيد من التطبيقات",
                            color = PrimeTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Appteka Style Card Component
// =============================================================================
@Composable
fun ApptekaCard(
    app: AppItem,
    downloadItem: DownloadItem?,
    onAppClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Check if app is installed on phone
    val isInstalled = remember(app.packageName, app.name) {
        val candidatePkg = app.packageName ?: "com.primestore.${app.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
        ApkInstaller.isAppInstalled(context, candidatePkg)
    }

    // Check if APK was downloaded and ready to install
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
            // Square App Icon on Right (RTL layout)
            AppIconBadge(
                emoji = app.iconEmoji,
                accentColor = app.accentColor,
                size = 54.dp,
                shape = RoundedCornerShape(14.dp),
                imageUrl = app.iconUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Center Column: Title, Arabic Category, Subtitle (v{version} • {size} • ⭐ {rating})
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.nameAr.ifBlank { app.name },
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
                            Text("FOSS", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.displayCategoryTitle,
                    color = NeonPurplePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle line: v{version} • {size} • ⭐ {rating}
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", app.rating),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button on Left (RTL layout)
            // Deals with download/install/open inline without opening details
            val buttonBgModifier = when {
                isInstalled -> Modifier.background(Color(0xFF14532D))
                isDownloadedReady -> Modifier.background(Color(0xFF2E1065))
                isDownloading -> Modifier.background(Color(0xFF1F1430))
                else -> Modifier.background(PrimeGradientBrush)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .then(buttonBgModifier)
                    .border(
                        1.dp,
                        when {
                            isInstalled -> AccentGreen
                            isDownloadedReady -> NeonBorderGlow
                            else -> NeonBorderGlow
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        when {
                            isInstalled -> {
                                val candidatePkg = app.packageName ?: "com.primestore.${app.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(candidatePkg)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    onAppClick()
                                }
                            }
                            isDownloadedReady -> onInstallClick()
                            isDownloading -> { /* Click while downloading */ }
                            else -> onDownloadClick()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isInstalled -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "فتح",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isDownloadedReady -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = NeonPurplePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تثبيت",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isDownloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { progress },
                                color = NeonPurplePrimary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = NeonPurplePrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
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
    }
}

// =============================================================================
// Top Bar & Hero Banner
// =============================================================================
@Composable
fun HomeTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    contentDescription = "القائمة الجانبية",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "تحديث البيانات",
                    tint = NeonPurplePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.prime_store_icon),
                contentDescription = "Prime Store",
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
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Store",
                        color = NeonPurplePrimary,
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
                contentDescription = "بحث",
                tint = Color.White,
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
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2E1065), Color(0xFF180A2D), Color(0xFF0F071D))
                        )
                    )
            ) {
                if (featuredApp != null && !featuredApp.screenshotUrls.firstOrNull().isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = featuredApp.screenshotUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.22f
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xEE120824), Color(0xF80E061A))
                                )
                            )
                    )
                }

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
                                .background(PrimeGradientBrush)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (featuredApp?.isOpenSource == true) "🔓 مفتوح المصدر (FOSS)" else "👑 مميز في المتجر",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = featuredApp?.nameAr ?: "متجر Prime Store",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = featuredApp?.descriptionAr?.take(70) ?: "تصفح وحمل التطبيقات بروابط مباشرة وتثبيت سلس داخل التطبيق",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (featuredApp != null) {
                            Box(
                                modifier = Modifier
                                    .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PrimeGradientBrush)
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(14.dp))
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
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تحميل (${featuredApp.size})",
                                        color = Color.White,
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
                tint = NeonPurplePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "أقسام المتجر",
                color = Color.White,
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = NeonPurplePrimary)
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color(item.glowColor).copy(alpha = 0.3f),
                    spotColor = Color(item.glowColor)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF16101F))
                .border(1.dp, NeonBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = item.category.iconResId),
                contentDescription = item.title,
                tint = Color(item.glowColor),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            color = Color.White,
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NeonPurplePrimary else Color(0xFF1E142D))
            .border(1.dp, if (selected) NeonBorderGlow else NeonBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.emoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.titleAr,
                color = if (selected) Color.White else PrimeTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
