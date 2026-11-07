package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeTextMuted

data class NavTabItem(
    val tab: MainTab,
    val titleAr: String,
    val titleEn: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val testTag: String
)

val NAV_TABS = listOf(
    NavTabItem(MainTab.HOME, "الرئيسية", "Home", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    NavTabItem(MainTab.CATEGORIES, "الأقسام", "Categories", Icons.Filled.Widgets, Icons.Outlined.Widgets, "tab_categories"),
    NavTabItem(MainTab.FAVORITES, "المفضلة", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "tab_favorites"),
    NavTabItem(MainTab.DOWNLOADS, "تحميلاتي", "Downloads", Icons.Filled.Download, Icons.Outlined.Download, "tab_downloads"),
    NavTabItem(MainTab.SETTINGS, "الإعدادات", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
)

@Composable
fun PrimeBottomNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
            .background(Color(0xFF0F0A1A))
            .border(
                width = 1.dp,
                color = NeonBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NAV_TABS.forEach { item ->
                val isSelected = currentTab == item.tab
                val iconColor = if (isSelected) NeonPurplePrimary else PrimeTextMuted
                val textColor = if (isSelected) NeonPurplePrimary else PrimeTextMuted

                Column(
                    modifier = Modifier
                        .testTag(item.testTag)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, color = NeonPurplePrimary)
                        ) { onTabSelected(item.tab) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp), ambientColor = NeonPurpleGlow, spotColor = NeonPurplePrimary)
                            )
                        }
                        Icon(
                            imageVector = if (isSelected) item.filledIcon else item.outlinedIcon,
                            contentDescription = item.titleAr,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = item.titleAr,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
