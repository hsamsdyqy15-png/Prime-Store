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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.ui.SortOrder
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

val CATEGORY_LIST = listOf(
    AppCategory.GAMES,
    AppCategory.APPS,
    AppCategory.POPULAR,
    AppCategory.ENTERTAINMENT,
    AppCategory.SPORTS,
    AppCategory.BUSINESS,
    AppCategory.EDUCATION
)

@Composable
fun CategoriesScreen(
    selectedCategory: AppCategory?,
    allApps: List<AppItem>,
    sortOrder: SortOrder,
    onCategoryClick: (AppCategory) -> Unit,
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
                text = selectedCategory?.titleAr ?: "الأقسام",
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
            // Main Categories List (matching Screenshot 3)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(CATEGORY_LIST) { category ->
                    CategoryLargeCard(
                        category = category,
                        onClick = { onCategoryClick(category) }
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
fun CategoryLargeCard(
    category: AppCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        shape = RoundedCornerShape(20.dp),
        hasGlow = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color(category.accentHex).copy(alpha = 0.35f),
                        spotColor = Color(category.accentHex)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF221733))
                    .border(1.dp, Color(category.accentHex).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.iconEmoji,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text: Title & Count/Subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.titleAr,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.countLabel,
                    color = PrimeTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Arrow >
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = NeonPurplePrimary,
                modifier = Modifier.size(16.dp)
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
    val filteredApps = remember(category, allApps, sortOrder) {
        val list = if (category == AppCategory.POPULAR) {
            allApps.sortedByDescending { it.rating }
        } else {
            allApps.filter { it.category == category }
        }
        when (sortOrder) {
            SortOrder.POPULAR -> list.sortedByDescending { it.ratingCount }
            SortOrder.RATING -> list.sortedByDescending { it.rating }
            SortOrder.NEWEST -> list.sortedByDescending { it.isNew }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sort Chips
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
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredApps) { app ->
                NeonCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    onClick = { onAppClick(app.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconBadge(
                            emoji = app.iconEmoji,
                            accentColor = app.accentColor,
                            size = 54.dp,
                            imageUrl = app.iconUrl
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.nameAr,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${app.rating}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " (${app.ratingCount}) • ${app.size}",
                                    color = PrimeTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = app.tags.joinToString(" • "),
                                color = NeonPurplePrimary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF281C38))
                                .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                .clickable { onDownloadClick(app.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "تثبيت",
                                tint = NeonPurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
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

@Composable
fun SortChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonPurplePrimary else Color(0xFF1E162A))
            .border(1.dp, if (selected) NeonBorderGlow else NeonBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else PrimeTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
