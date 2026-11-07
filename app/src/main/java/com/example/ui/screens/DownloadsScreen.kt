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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadItem
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.NeonPurpleSecondary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    filterTab: Int, // 0 = جاري التحميل, 1 = تم التحميل
    onFilterChange: (Int) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClearAll: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDownloads = remember(downloads) { downloads.filter { !it.isCompleted } }
    val completedDownloads = remember(downloads) { downloads.filter { it.isCompleted } }

    val displayedList = if (filterTab == 0) activeDownloads else completedDownloads

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_screen")
    ) {
        // Title: تحميلاتي
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "تحميلاتي",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Segmented Tabs: "تم التحميل" (Completed) and "جاري التحميل" (Downloading)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
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
                    // "جاري التحميل" (Tab 0)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (filterTab == 0) NeonPurplePrimary else Color.Transparent)
                            .clickable { onFilterChange(0) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "جاري التحميل (${activeDownloads.size})",
                            color = if (filterTab == 0) Color.White else PrimeTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // "تم التحميل" (Tab 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (filterTab == 1) NeonPurplePrimary else Color.Transparent)
                            .clickable { onFilterChange(1) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تم التحميل (${completedDownloads.size})",
                            color = if (filterTab == 1) Color.White else PrimeTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content List
        if (displayedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if (filterTab == 0) "📥" else "🎉", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (filterTab == 0) "لا توجد تنزيلات قيد المعالجة" else "لم تقم بتنزيل أي تطبيقات بعد",
                        color = PrimeTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedList) { item ->
                    if (!item.isCompleted) {
                        // In Progress Item (e.g. Clash of Clans 72%)
                        ActiveDownloadCard(
                            item = item,
                            onPause = { onPauseDownload(item.appId) },
                            onResume = { onResumeDownload(item.appId) },
                            onCancel = { onCancelDownload(item.appId) },
                            onClick = { onAppClick(item.appId) }
                        )
                    } else {
                        // Completed Item (e.g. Subway Surfers, WhatsApp)
                        CompletedDownloadCard(
                            item = item,
                            onClick = { onAppClick(item.appId) }
                        )
                    }
                }
            }
        }

        // Bottom Action Button: "مسح الكل" (Clear All)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            NeonGradientButton(
                text = "مسح الكل",
                icon = Icons.Filled.Delete,
                testTag = "clear_all_downloads_button",
                modifier = Modifier.fillMaxWidth(),
                onClick = onClearAll
            )
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        hasGlow = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconBadge(
                    emoji = item.appIcon,
                    accentColor = item.accentColor,
                    size = 52.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.appName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = if (item.isPaused) onResume else onPause,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (item.isPaused) "استئناف" else "إيقاف مؤقت",
                                    tint = NeonPurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "إلغاء",
                                    tint = PrimeTextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF281C38))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.progress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeonPurplePrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            color = NeonPurplePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.speed} • ${item.downloadedSize}",
                            color = PrimeTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    item: DownloadItem,
    onClick: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        hasGlow = false,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIconBadge(
                    emoji = item.appIcon,
                    accentColor = item.accentColor,
                    size = 50.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = item.appName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "مكتمل",
                        color = PrimeTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Green Checkmark icon matching Screenshot 5
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "مكتمل",
                tint = AccentGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
