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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.NeonPurpleSecondary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextPrimary
import com.example.ui.theme.PrimeTextSecondary

data class CategoryQuickItem(
    val category: AppCategory,
    val title: String,
    val iconEmoji: String,
    val glowColor: Long
)

val QUICK_CATEGORIES = listOf(
    CategoryQuickItem(AppCategory.GAMES, "ألعاب", "🎮", 0xFFA855F7),
    CategoryQuickItem(AppCategory.APPS, "تطبيقات", "🤖", 0xFF22C55E),
    CategoryQuickItem(AppCategory.POPULAR, "الأكثر شهرة", "⭐", 0xFFFBBF24),
    CategoryQuickItem(AppCategory.NEW_GAMES, "الألعاب الجديدة", "🔥", 0xFFF97316),
    CategoryQuickItem(AppCategory.MUSIC, "موسيقى", "🎵", 0xFF38BDF8),
    CategoryQuickItem(AppCategory.VIDEO, "فيديو", "▶️", 0xFFEC4899),
    CategoryQuickItem(AppCategory.TOOLS, "أدوات", "🔧", 0xFF8B5CF6),
    CategoryQuickItem(AppCategory.MORE, "المزيد", "⊞", 0xFFA855F7)
)

@Composable
fun HomeScreen(
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    onCategoryClick: (AppCategory) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDownloadClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val featuredApps = remember(apps) { apps.filter { it.isFeatured } }
    val trendingApps = remember(apps) { apps.filter { it.isTrending } }
    val newApps = remember(apps) { apps.filter { it.isNew || it.rating >= 4.5f }.take(6) }

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
                onSearchClick = onSearchClick
            )
        }

        // Hero Banner: GTA V Heist Banner with "تحميل الآن"
        item {
            GtaHeroBanner(
                onDownloadClick = { onDownloadClick("gta_v") },
                onDetailsClick = { onAppClick("gta_v") }
            )
        }

        // Section: أقسام المتجر (Store Categories Grid 4x2)
        item {
            StoreCategoriesSection(
                onCategoryClick = onCategoryClick
            )
        }

        // Section: ألعاب مميزة (Featured Games Horizontal List)
        item {
            FeaturedGamesSection(
                games = featuredApps,
                onAppClick = onAppClick
            )
        }

        // Section: تطبيقات رائجة (Trending Apps)
        item {
            TrendingAppsSection(
                apps = trendingApps,
                onAppClick = onAppClick,
                onDownloadClick = onDownloadClick
            )
        }

        // Section: أضيف حديثاً (Newly Added)
        item {
            NewAppsSection(
                apps = newApps,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
fun HomeTopBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Hamburger Menu (left in screenshot)
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

        // Title: Prime Store with Crown Icon (LTR strictly enforced)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Search Icon (right in screenshot)
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
fun GtaHeroBanner(
    onDownloadClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(205.dp),
            shape = RoundedCornerShape(22.dp),
            hasGlow = true,
            onClick = onDetailsClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Banner background image
                Image(
                    painter = painterResource(id = R.drawable.banner_gta5),
                    contentDescription = "Grand Theft Auto V Promo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x880B0614),
                                    Color(0xEE0B0614)
                                )
                            )
                        )
                )

                // GTA V stylized logo text overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "grand",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "theft",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "auto ",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "V",
                            color = AccentGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }

                // Download Button on bottom right (RTL: aligned prominently)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PrimeGradientBrush)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = Color.White)
                            ) { onDownloadClick() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تحميل الآن",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Carousel indicator dots (active white, 3 smaller gray)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4B4656))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4B4656))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4B4656))
            )
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
        // Header: أقسام المتجر
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

        // 4x2 Grid of categories
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
            Text(
                text = item.iconEmoji,
                fontSize = 28.sp
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
        // Section Header: ألعاب مميزة ⭐
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
                text = "ألعاب مميزة",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Horizontal scroll row of cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(games) { game ->
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
            .width(135.dp)
            .height(175.dp),
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
            // Icon
            AppIconBadge(
                emoji = game.iconEmoji,
                accentColor = game.accentColor,
                size = 64.dp,
                shape = RoundedCornerShape(16.dp),
                imageUrl = game.iconUrl
            )

            // Name
            Text(
                text = game.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Rating
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format("%.1f", game.rating),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " (${game.ratingCount})",
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

        apps.take(4).forEach { app ->
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
                        Text(
                            text = app.nameAr,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
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
            items(apps) { app ->
                FeaturedGameCard(game = app, onClick = { onAppClick(app.id) })
            }
        }
    }
}
