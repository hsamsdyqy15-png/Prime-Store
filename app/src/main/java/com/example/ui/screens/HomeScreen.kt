package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.theme.AccentGold
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
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    onCategoryClick: (AppCategory) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var filterOpenSourceOnly by remember { mutableStateOf(false) }

    val displayApps = remember(apps, filterOpenSourceOnly) {
        if (filterOpenSourceOnly) apps.filter { it.isOpenSource } else apps
    }
    val featuredApps = remember(displayApps) { displayApps.filter { it.isFeatured }.ifEmpty { displayApps.take(4) } }
    val trendingApps = remember(displayApps) { displayApps.filter { it.isTrending }.ifEmpty { displayApps } }
    val newApps = remember(displayApps) { displayApps.filter { it.isNew || it.rating >= 4.0f }.take(6) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Top Bar
        item {
            HomeTopBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onRefresh = onRefresh
            )
        }

        // Hero Banner: Features real published app or Prime Store
        item {
            StoreHeroBanner(
                featuredApp = displayApps.firstOrNull(),
                onDownloadClick = onDownloadClick,
                onDetailsClick = onAppClick
            )
        }

        // Filter Chips Row (Open Source Filter)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortChipItem(
                    label = "جميع التطبيقات (${apps.size})",
                    selected = !filterOpenSourceOnly,
                    onClick = { filterOpenSourceOnly = false }
                )
                SortChipItem(
                    label = "🔓 مفتوح المصدر (FOSS)",
                    selected = filterOpenSourceOnly,
                    onClick = { filterOpenSourceOnly = true }
                )
            }
        }

        // Section: أقسام المتجر (Store Categories Grid with sharp vector icons)
        item {
            StoreCategoriesSection(
                onCategoryClick = onCategoryClick
            )
        }

        if (displayApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_default),
                            contentDescription = null,
                            tint = NeonPurplePrimary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (filterOpenSourceOnly) "لا توجد تطبيقات مفتوحة المصدر حالياً" else "لا توجد تطبيقات منشورة حالياً",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "سجل دخولك كأدمن من القائمة الجانبية لنشر أول تطبيق أو لعبة حقيقية 🚀",
                            color = PrimeTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Section: ألعاب وتطبيقات مميزة (Featured Games & Apps)
            if (featuredApps.isNotEmpty()) {
                item {
                    FeaturedGamesSection(
                        games = featuredApps,
                        onAppClick = onAppClick
                    )
                }
            }

            // Section: تطبيقات رائجة (Trending Apps)
            if (trendingApps.isNotEmpty()) {
                item {
                    TrendingAppsSection(
                        apps = trendingApps,
                        onAppClick = onAppClick,
                        onDownloadClick = onDownloadClick
                    )
                }
            }

            // Section: أضيف حديثاً (Newly Added)
            if (newApps.isNotEmpty()) {
                item {
                    NewAppsSection(
                        apps = newApps,
                        onAppClick = onAppClick
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
                    // High-contrast gradient scrim to ensure text and buttons are 100% visible and readable
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
fun FeaturedGamesSection(
    games: List<AppItem>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = NeonPurplePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "تطبيقات وألعاب مميزة",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(games, key = { it.id }) { game ->
                FeaturedGameCard(game = game, onClick = { onAppClick(game.id) })
            }
        }
    }
}

@Composable
fun FeaturedGameCard(
    game: AppItem,
    onClick: () -> Unit
) {
    NeonCard(
        modifier = Modifier
            .width(140.dp)
            .height(185.dp),
        shape = RoundedCornerShape(20.dp),
        hasGlow = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AppIconBadge(
                emoji = game.iconEmoji,
                accentColor = game.accentColor,
                size = 64.dp,
                shape = RoundedCornerShape(16.dp),
                imageUrl = game.iconUrl
            )

            Text(
                text = game.nameAr,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (game.isOpenSource) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E3A8A))
                        .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("🔓 FOSS", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", game.rating),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " (${game.size})",
                    color = PrimeTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun TrendingAppsSection(
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit,
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
            Text(
                text = "🔥 تطبيقات رائجة",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        apps.take(6).forEach { app ->
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                shape = RoundedCornerShape(16.dp),
                hasGlow = false,
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
                        size = 48.dp,
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
                                overflow = TextOverflow.Ellipsis
                            )
                            if (app.isOpenSource) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E3A8A))
                                        .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text("FOSS", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${app.rating} • ${app.size}",
                                color = PrimeTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF281C38))
                            .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                            .clickable { onDownloadClick(app.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
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
                                color = NeonPurplePrimary,
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
fun NewAppsSection(
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "✨ أضيف حديثاً",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                FeaturedGameCard(game = app, onClick = { onAppClick(app.id) })
            }
        }
    }
}
