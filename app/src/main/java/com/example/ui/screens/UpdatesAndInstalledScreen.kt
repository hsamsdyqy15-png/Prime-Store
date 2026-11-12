package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.installer.PackageHelper
import com.example.data.model.AppItem
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

data class InstalledAppItemState(
    val app: AppItem,
    val installedPackageName: String,
    val installedVersionCode: Long,
    val installedVersionName: String,
    val storeVersionCode: Long,
    val hasUpdate: Boolean
)

@Composable
fun UpdatesAndInstalledScreen(
    initialTab: Int = 0, // 0 = التحديثات, 1 = تطبيقاتي المثبتة
    installedApps: List<InstalledAppItemState>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
    onAppClick: (String) -> Unit,
    onUpdateApp: (String) -> Unit,
    onUpdateAll: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var appToUninstall by remember { mutableStateOf<InstalledAppItemState?>(null) }
    val updatesList = remember(installedApps) { installedApps.filter { it.hasUpdate } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("updates_and_installed_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedTab == 0) "تحديثات التطبيقات 🚀" else "تطبيقاتي المثبتة 📱",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "تحديث القائمة",
                    tint = NeonPurplePrimary
                )
            }
        }

        // Segmented Tabs: "التحديثات" (0) and "المثبتة" (1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16101F))
                    .border(1.dp, NeonBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Updates Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == 0) NeonPurplePrimary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "التحديثات",
                                color = if (selectedTab == 0) Color.White else PrimeTextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (updatesList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(AccentGold)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${updatesList.size}",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Installed Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == 1) NeonPurplePrimary else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "المثبتة (${installedApps.size})",
                            color = if (selectedTab == 1) Color.White else PrimeTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // TAB 0: Updates
        if (selectedTab == 0) {
            if (updatesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "✨", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "جميع تطبيقاتك محدثة لآخر إصدار!",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "لا توجد تحديثات جديدة متوفرة للتطبيقات المثبتة حالياً",
                            color = PrimeTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Update All Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF221538))
                                .border(1.dp, NeonBorderGlow, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "يتوفر ${updatesList.size} تحديث جديد",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "قم بتحديث كل التطبيقات دفعة واحدة",
                                    color = PrimeTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            NeonGradientButton(
                                text = "تحديث الكل ⚡",
                                onClick = onUpdateAll,
                                modifier = Modifier.height(38.dp)
                            )
                        }
                    }

                    items(updatesList, key = { it.app.id }) { item ->
                        UpdateAppCard(
                            item = item,
                            onClick = { onAppClick(item.app.id) },
                            onUpdate = { onUpdateApp(item.app.id) },
                            onUninstall = { appToUninstall = item }
                        )
                    }
                }
            }
        }
        // TAB 1: Installed Apps
        else {
            if (installedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📦", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لم يتم تثبيت تطبيقات من المتجر بعد",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تصفح الأقسام وحمّل تطبيقاتك وألعابك المفضلة",
                            color = PrimeTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(installedApps, key = { it.app.id }) { item ->
                        InstalledAppCard(
                            item = item,
                            onClick = { onAppClick(item.app.id) },
                            onOpen = { onOpenApp(item.installedPackageName) },
                            onUninstall = { appToUninstall = item }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Uninstall
    if (appToUninstall != null) {
        val app = appToUninstall!!
        AlertDialog(
            onDismissRequest = { appToUninstall = null },
            containerColor = Color(0xFF1E142D),
            title = {
                Text(
                    text = "حذف التطبيق 🗑️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في إلغاء تثبيت \"${app.app.nameAr.ifBlank { app.app.name }}\" من جهازك؟\n(ملاحظة: سيتم حذف بيانات التطبيق)",
                    color = Color.White,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = app.installedPackageName
                        appToUninstall = null
                        onUninstallApp(pkg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("نعم، احذف التطبيق", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { appToUninstall = null }) {
                    Text("إلغاء", color = PrimeTextMuted)
                }
            }
        )
    }
}

@Composable
fun UpdateAppCard(
    item: InstalledAppItemState,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit
) {
    var expandedChangelog by remember { mutableStateOf(false) }

    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        hasGlow = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .animateContentSize(tween(250))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconBadge(
                        emoji = item.app.iconEmoji,
                        accentColor = item.app.accentColor,
                        size = 50.dp,
                        imageUrl = item.app.iconUrl
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.app.nameAr.ifBlank { item.app.name },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v${item.installedVersionName}",
                                color = PrimeTextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = " ➔ ",
                                color = NeonPurplePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "v${item.app.version}",
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onUninstall,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "حذف التطبيق",
                            tint = AccentRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimeGradientBrush)
                            .clickable { onUpdate() }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "تحديث",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تحديث",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Changelog toggle
            val changelogText = item.app.changelog?.ifBlank { null }
                ?: "تحسينات عامة في الأداء وإصلاح الأخطاء السابقة وتحديث التوافق."

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedChangelog = !expandedChangelog }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ما الجديد في هذا التحديث؟",
                    color = NeonPurplePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expandedChangelog) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NeonPurplePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expandedChangelog) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF191124))
                        .padding(10.dp)
                ) {
                    Text(
                        text = changelogText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InstalledAppCard(
    item: InstalledAppItemState,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        hasGlow = false,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIconBadge(
                    emoji = item.app.iconEmoji,
                    accentColor = item.app.accentColor,
                    size = 46.dp,
                    imageUrl = item.app.iconUrl
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.app.nameAr.ifBlank { item.app.name },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "الإصدار المثبت: v${item.installedVersionName}",
                        color = PrimeTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Uninstall button
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "إلغاء التثبيت",
                        tint = AccentRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Open App button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2E1065))
                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(10.dp))
                        .clickable { onOpen() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = "فتح",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "فتح",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
