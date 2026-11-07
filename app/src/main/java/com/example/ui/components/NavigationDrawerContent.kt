package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCategory
import com.example.ui.MainTab
import com.example.ui.UserProfile
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun NavigationDrawerContent(
    userProfile: UserProfile,
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onCategorySelected: (AppCategory) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(Color(0xFF120C1D))
            .border(1.dp, NeonBorder, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .padding(vertical = 24.dp, horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Row with Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.prime_store_icon),
                    contentDescription = "Prime Store",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Prime Store",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // User Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
                .clip(RoundedCornerShape(18.dp))
                .background(PrimeCardBackground)
                .border(1.dp, NeonBorderGlow, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PrimeGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userProfile.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "✓", color = NeonPurplePrimary, fontSize = 12.sp)
                    }
                    Text(
                        text = userProfile.email,
                        color = PrimeTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "التنقل السريع",
            color = PrimeTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Main Navigation Links
        DrawerNavItem(
            title = "الرئيسية",
            icon = Icons.Filled.Home,
            selected = currentTab == MainTab.HOME,
            onClick = {
                onTabSelected(MainTab.HOME)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = "الأقسام",
            icon = Icons.Filled.Widgets,
            selected = currentTab == MainTab.CATEGORIES,
            onClick = {
                onTabSelected(MainTab.CATEGORIES)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = "المفضلة",
            icon = Icons.Filled.Favorite,
            selected = currentTab == MainTab.FAVORITES,
            onClick = {
                onTabSelected(MainTab.FAVORITES)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = "تحميلاتي",
            icon = Icons.Filled.Download,
            selected = currentTab == MainTab.DOWNLOADS,
            onClick = {
                onTabSelected(MainTab.DOWNLOADS)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = "الإعدادات",
            icon = Icons.Filled.Settings,
            selected = currentTab == MainTab.SETTINGS,
            onClick = {
                onTabSelected(MainTab.SETTINGS)
                onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = NeonBorder)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "أبرز الأقسام",
            color = PrimeTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        listOf(
            AppCategory.GAMES,
            AppCategory.APPS,
            AppCategory.POPULAR,
            AppCategory.ENTERTAINMENT
        ).forEach { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onCategorySelected(category)
                        onCloseDrawer()
                    }
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = category.iconEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = category.titleAr,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Prime Store v2.4.0 • أحدث إصدار",
            color = PrimeTextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun DrawerNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NeonPurplePrimary.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) NeonBorderGlow else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) NeonPurplePrimary else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (selected) NeonPurplePrimary else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
