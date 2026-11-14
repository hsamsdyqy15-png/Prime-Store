package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppCategory
import com.example.ui.MainTab
import com.example.ui.UserProfile
import com.example.ui.theme.PrimeTheme

@Composable
fun NavigationDrawerContent(
    userProfile: UserProfile?,
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onCategorySelected: (AppCategory) -> Unit,
    onCloseDrawer: () -> Unit,
    onLoginClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
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
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(id = R.string.close),
                    tint = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // User Profile Card
        val profileElevation = if (colors.isDark) 8.dp else 2.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(profileElevation, RoundedCornerShape(18.dp), ambientColor = colors.accentDark, spotColor = colors.accent)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .clickable {
                    if (userProfile == null) {
                        onCloseDrawer()
                        onLoginClick()
                    }
                }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.buttonGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (userProfile != null) (userProfile.name.firstOrNull()?.toString() ?: "U") else "👤",
                        color = colors.textOnAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userProfile?.name ?: stringResource(id = R.string.login),
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (userProfile != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "✓", color = colors.accent, fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = userProfile?.email ?: stringResource(id = R.string.login_sync_hint),
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(id = R.string.tab_home),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Main Navigation Links
        DrawerNavItem(
            title = stringResource(id = R.string.tab_home),
            icon = Icons.Filled.Home,
            selected = currentTab == MainTab.HOME,
            onClick = {
                onTabSelected(MainTab.HOME)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = stringResource(id = R.string.tab_categories),
            icon = Icons.Filled.Widgets,
            selected = currentTab == MainTab.CATEGORIES,
            onClick = {
                onTabSelected(MainTab.CATEGORIES)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = stringResource(id = R.string.tab_favorites),
            icon = Icons.Filled.Favorite,
            selected = currentTab == MainTab.FAVORITES,
            onClick = {
                onTabSelected(MainTab.FAVORITES)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = stringResource(id = R.string.tab_downloads),
            icon = Icons.Filled.Download,
            selected = currentTab == MainTab.DOWNLOADS,
            onClick = {
                onTabSelected(MainTab.DOWNLOADS)
                onCloseDrawer()
            }
        )

        DrawerNavItem(
            title = stringResource(id = R.string.tab_settings),
            icon = Icons.Filled.Settings,
            selected = currentTab == MainTab.SETTINGS,
            onClick = {
                onTabSelected(MainTab.SETTINGS)
                onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.border)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(id = R.string.store_sections),
            color = colors.textSecondary,
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
                    text = stringResource(id = category.titleRes),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Prime Store v${BuildConfig.VERSION_NAME}",
            color = colors.textSecondary,
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
    val colors = PrimeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.border else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (selected) colors.accent else colors.textPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
