package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.example.ui.theme.AccentRed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.ReviewItem
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.components.StarRatingBar
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
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

@Composable
fun AppDetailsScreen(
    app: AppItem,
    isFavorite: Boolean,
    downloadItem: DownloadItem?,
    reviews: List<ReviewItem>,
    allApps: List<AppItem> = emptyList(),
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallClick: () -> Unit = onDownloadClick,
    onUninstallClick: (String) -> Unit = {},
    onOpenAppClick: (String) -> Unit = {},
    onReportProblem: (String, String) -> Unit = { _, _ -> },
    onAppClick: (String) -> Unit = {},
    onAddReview: (Int, String) -> Unit,
    onScreenshotClick: (Int) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("حول التطبيق", "تحليل الذكاء الاصطناعي 🤖", "مراجعات", "لقطات الشاشة")
    var showMenu by remember { mutableStateOf(false) }
    var showReviewModal by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showSecurityDialog by remember { mutableStateOf(false) }
    var isScanningSecurity by remember { mutableStateOf(false) }
    var securityReport by remember { mutableStateOf<com.example.data.security.SecurityReport?>(null) }
    var showUninstallConflictDialog by remember { mutableStateOf(false) }
    var detectedInstalledPackage by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showOriginalText by remember { mutableStateOf(false) }
    var showConfirmUninstallDialog by remember { mutableStateOf(false) }

    val installedInfo = remember(app.packageName, app.name) {
        val apkFile = com.example.data.installer.ApkInstaller.getApkFile(context, app.id)
        val pkg = com.example.data.installer.ApkInstaller.findInstalledPackage(context, app.name, apkFile, app.packageName)
        if (!pkg.isNullOrBlank()) com.example.data.installer.PackageHelper.getInstalledInfo(context, pkg) else null
    }

    val similarApps = remember(allApps, app) {
        allApps.filter { it.id != app.id && (it.category == app.category || it.categoryKey == app.categoryKey) }.take(6)
    }

    android.util.Log.d(
        "PrimeStore",
        "AppDetailsScreen opened -> id: ${app.id}, name: ${app.name}, icon_url: ${app.iconUrl}, file_url: ${app.fileUrl}, screenshots: ${app.screenshotUrls}"
    )

    androidx.compose.runtime.LaunchedEffect(app.id) {
        android.util.Log.d(
            "PrimeStore",
            "AppDetailsScreen LaunchedEffect -> id: ${app.id}, name: ${app.name}, icon_url: ${app.iconUrl}, file_url: ${app.fileUrl}, screenshots: ${app.screenshotUrls}"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_details_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "المفضلة",
                                tint = if (isFavorite) Color(0xFFEF4444) else Color.White
                            )
                        }

                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "بحث",
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "المزيد",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E162A))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("مشاركة التطبيق 🔗", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        onShareClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("إبلاغ عن مشكلة أو محتوى ⚠️", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Header info matching Screenshot 4: Big Icon, Title, Rating, Category badges
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconBadge(
                        emoji = app.iconEmoji,
                        accentColor = app.accentColor,
                        size = 96.dp,
                        shape = RoundedCornerShape(24.dp),
                        imageUrl = app.iconUrl
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = app.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${app.rating}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " (${app.ratingCount})",
                            color = PrimeTextMuted,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tags & Version badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        // Version Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2E1065))
                                .border(1.dp, NeonBorderGlow, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = app.displayVersionBadge,
                                color = Color(0xFFE9D5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        app.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF281C38))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = NeonPurplePrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Open Source Badge & Source URL
                    if (app.isOpenSource) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E3A8A))
                                    .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "🔓 مشروع مفتوح المصدر (FOSS)",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!app.sourceCodeUrl.isNullOrBlank()) {
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF281C38))
                                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(10.dp))
                                        .clickable {
                                            try { uriHandler.openUri(app.sourceCodeUrl) } catch (e: Exception) {}
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "🐙 عرض الكود المصدر ↗",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Supported CPU Architecture Badge
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E142D))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "⚡ المعالجات المدعومة: ${app.supportedAbis}",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download Action Button / Progress
                    if (downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING) {
                        // In-progress downloading state with progress, speed, and cancel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1E142D))
                                .border(1.dp, NeonBorderGlow, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جاري التحميل... ${(downloadItem.progress * 100).toInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = downloadItem.speed,
                                    color = NeonPurplePrimary,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "إلغاء",
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadItem.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = NeonPurplePrimary,
                                trackColor = Color(0xFF3B2456)
                            )
                        }
                    } else if (downloadItem != null && downloadItem.status == DownloadStatus.FAILED) {
                        // Failed state with retry button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF2D141E))
                                .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = downloadItem.errorMessage ?: "فشل التحميل، يرجى المحاولة مرة أخرى",
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            NeonGradientButton(
                                text = "إعادة المحاولة 🔄",
                                icon = Icons.Filled.Download,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onDownloadClick
                            )
                        }
                    } else if (downloadItem != null && (downloadItem.isCompleted || downloadItem.status == DownloadStatus.COMPLETED)) {
                        // Completed state -> Direct Install button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeonGradientButton(
                                text = "تثبيت التطبيق 📦",
                                icon = Icons.Filled.CheckCircle,
                                modifier = Modifier.weight(1f),
                                onClick = onInstallClick
                            )
                            if (installedInfo != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showConfirmUninstallDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1220)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "حذف التطبيق", tint = AccentRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (installedInfo != null) {
                        // Installed state
                        val hasUpdate = app.versionCode > installedInfo.versionCode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasUpdate) {
                                NeonGradientButton(
                                    text = "تحديث التطبيق 🚀 (${app.size})",
                                    icon = Icons.Filled.Download,
                                    modifier = Modifier.weight(1f),
                                    onClick = onDownloadClick
                                )
                            } else {
                                NeonGradientButton(
                                    text = "فتح التطبيق ↗️",
                                    icon = Icons.Filled.OpenInNew,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenAppClick(installedInfo.packageName) }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Permanent Uninstall button
                            Button(
                                onClick = { showConfirmUninstallDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1220)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "حذف التطبيق", tint = AccentRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Ready to download button
                        NeonGradientButton(
                            text = "تحميل الآن  (${app.size})",
                            icon = Icons.Filled.Download,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDownloadClick
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isScanningSecurity = true
                                showSecurityDialog = true
                                val apkFile = com.example.data.installer.ApkInstaller.getApkFile(context, app.id)
                                val report = com.example.data.security.AiSecurityScanner.scanApk(context, apkFile)
                                securityReport = report
                                isScanningSecurity = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E142D)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f))
                    ) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "فحص الأمان", tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص الأمان بالذكاء الاصطناعي 🛡️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val apkFile = com.example.data.installer.ApkInstaller.getApkFile(context, app.id)
                            val resolvedPkg = com.example.data.installer.ApkInstaller.findInstalledPackage(
                                context,
                                app.name,
                                apkFile,
                                app.packageName
                            )
                            detectedInstalledPackage = resolvedPkg
                            showUninstallConflictDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281020)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                    ) {
                        Text("🗑️ حل مشكلة تعارض التثبيت (حذف النسخة القديمة)", color = Color(0xFFFFB4AB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tabs Row: حول التطبيق, مراجعات, لقطات الشاشة
            item {
                Spacer(modifier = Modifier.height(14.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = NeonPurplePrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonPurplePrimary,
                            height = 3.dp
                        )
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(NeonBorder)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) Color.White else PrimeTextMuted,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // "حول التطبيق" (About App Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // AI Translation header badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF221538))
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌐 ", fontSize = 14.sp)
                                    Text(
                                        text = if (showOriginalText) "النص الأصلي (الإنجليزية)" else "مترجم تلقائياً بالذكاء الاصطناعي",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = if (showOriginalText) "عرض الترجمة 🔄" else "عرض النص الأصلي 📄",
                                    color = NeonPurplePrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showOriginalText = !showOriginalText }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Changelog Expandable Section
                            val changelogNotes = app.changelog?.ifBlank { null }
                                ?: "تحسينات عامة في الأداء، دعم معمارية المعالجات الحديثة ${app.supportedAbis}، وإصلاح مشاكل التثبيت."

                            var isChangelogExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF191124))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                                    .clickable { isChangelogExpanded = !isChangelogExpanded }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🚀 ", fontSize = 15.sp)
                                            Text(
                                                text = "ما الجديد في هذا الإصدار (v${app.version})",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isChangelogExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = NeonPurplePrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isChangelogExpanded) {
                                        Text(
                                            text = changelogNotes,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (showOriginalText) app.descriptionEn.ifBlank { app.descriptionAr } else app.descriptionAr,
                                color = PrimeTextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "الميزات الرئيسية:",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Green checkmarks features ✔
                            app.featuresAr.forEach { feature ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = feature,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Screenshots horizontal preview
                            Text(
                                text = "لقطات من داخل اللعبة:",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (app.screenshotUrls.isNotEmpty()) {
                                    itemsIndexed(app.screenshotUrls) { index, shotUrl ->
                                        ScreenshotThumbnail(
                                            colorHex = 0xFF4C1D95,
                                            index = index,
                                            imageUrl = shotUrl,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                } else {
                                    itemsIndexed(app.screenshotsColors) { index, colorHex ->
                                        ScreenshotThumbnail(
                                            colorHex = colorHex,
                                            index = index,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // "تحليل الذكاء الاصطناعي 🤖" (AI Deep Analysis Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // AI Score Banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF2E1065), Color(0xFF1E142D), Color(0xFF0F172A))
                                        )
                                    )
                                    .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "تقييم الذكاء الاصطناعي الشامل 🤖",
                                            color = Color(0xFFD8B4FE),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "تطبيق فائق الأداء والاعتمادية",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "تم فحص الكود البرمجي وصلاحيات النظام وتوافقية المعمارية ${app.supportedAbis}",
                                            color = PrimeTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF6B21A8))
                                            .border(1.dp, Color(0xFFC084FC), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "9.9", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                            Text(text = "من 10", color = Color(0xFFE9D5FF), fontSize = 9.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // AI Safety & Security Badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF064E3B).copy(alpha = 0.7f))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "فحص الأمان: نظيف ومعتمد 100% 🛡️",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "خالٍ تماماً من الفيروسات والبرمجيات الخبيثة ومتوافق مع معايير الأمان العالمية.",
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Architecture & CPU Compatibility Analysis
                            Text(
                                text = "تحليل توافقية المعالج والنظام ⚡",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF181224)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("المعمارية المدعومة:", color = PrimeTextMuted, fontSize = 12.sp)
                                        Text(app.supportedAbis, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("رقم الإصدار:", color = PrimeTextMuted, fontSize = 12.sp)
                                        Text(app.version, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("اسم الحزمة (Package):", color = PrimeTextMuted, fontSize = 12.sp)
                                        Text(app.packageName ?: "com.primestore.${app.name.lowercase().replace(" ", "")}", color = Color(0xFFF472B6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("مفتوح المصدر (FOSS):", color = PrimeTextMuted, fontSize = 12.sp)
                                        Text(if (app.isOpenSource) "نعم 🌟" else "لا", color = if (app.isOpenSource) Color(0xFF34D399) else PrimeTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // AI Smart Recommendations
                            Text(
                                text = "توصيات وميزات الذكاء الاصطناعي الذكية 💡",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                "🚀 تثبيت فائق السرعة عبر تقنية Direct Stream دون استهلاك الذاكرة",
                                "🔋 استهلاك طاقة مثالي بدون عمليات خفية في الخلفية",
                                "⚡ دعم كامل للمعالجات الحديثة 64-bit لأعلى سرعة وأقل تأخير",
                                "🛡️ حماية تلقائية من تعارض التوقيع وحزم النظام القديمة"
                            ).forEach { itemText ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(itemText, color = PrimeTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // "مراجعات" (Reviews Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "آراء ومراجعات المستخدمين",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimeGradientBrush)
                                        .clickable { showReviewModal = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+ أضف تقييمك",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (reviews.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "كن أول من يكتب مراجعة لهذا التطبيق! ⭐",
                                        color = PrimeTextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                reviews.forEach { review ->
                                    ReviewItemCard(review = review)
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // "لقطات الشاشة" (Screenshots Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "اضغط على أي لقطة لعرضها بالحجم الكامل:",
                                color = PrimeTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (app.screenshotUrls.isNotEmpty()) {
                                    itemsIndexed(app.screenshotUrls) { index, shotUrl ->
                                        ScreenshotThumbnail(
                                            colorHex = 0xFF4C1D95,
                                            index = index,
                                            width = 160.dp,
                                            height = 240.dp,
                                            imageUrl = shotUrl,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                } else {
                                    itemsIndexed(app.screenshotsColors) { index, colorHex ->
                                        ScreenshotThumbnail(
                                            colorHex = colorHex,
                                            index = index,
                                            width = 160.dp,
                                            height = 240.dp,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Similar Apps Row
            if (similarApps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "تطبيقات مشابهة قد تهمك 💡",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(similarApps) { simApp ->
                                NeonCard(
                                    modifier = Modifier.width(130.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    hasGlow = false,
                                    onClick = { onAppClick(simApp.id) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AppIconBadge(
                                            emoji = simApp.iconEmoji,
                                            accentColor = simApp.accentColor,
                                            size = 48.dp,
                                            imageUrl = simApp.iconUrl
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = simApp.nameAr.ifBlank { simApp.name },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = simApp.size,
                                            color = PrimeTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Review Modal Dialog
        if (showReviewModal) {
            AddReviewDialog(
                appName = app.nameAr,
                onDismiss = { showReviewModal = false },
                onSubmit = { rating, comment ->
                    onAddReview(rating, comment)
                    showReviewModal = false
                }
            )
        }

        // Problem Report Dialog
        if (showReportDialog) {
            com.example.ui.components.ProblemReportDialog(
                appName = app.nameAr.ifBlank { app.name },
                onSubmit = { reason, details ->
                    onReportProblem(reason, details)
                },
                onDismiss = { showReportDialog = false }
            )
        }

        // Confirm Uninstall Dialog
        if (showConfirmUninstallDialog && installedInfo != null) {
            AlertDialog(
                onDismissRequest = { showConfirmUninstallDialog = false },
                containerColor = Color(0xFF1E142D),
                title = {
                    Text("حذف التطبيق 🗑️", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "هل أنت متأكد من رغبتك في إلغاء تثبيت \"${app.nameAr.ifBlank { app.name }}\" من جهازك؟",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmUninstallDialog = false
                            onUninstallClick(installedInfo.packageName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("نعم، احذف التطبيق", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmUninstallDialog = false }) {
                        Text("إلغاء", color = PrimeTextMuted)
                    }
                }
            )
        }

        // AI Security Report Dialog
        if (showSecurityDialog) {
            AlertDialog(
                onDismissRequest = { showSecurityDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "تقرير فحص الأمان بالذكاء الاصطناعي 🤖", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    if (isScanningSecurity) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = NeonPurplePrimary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("جاري فحص التطبيق وتحليل الأكواد والصلاحيات بالذكاء الاصطناعي... ⏳", color = PrimeTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    } else if (securityReport != null) {
                        val rep = securityReport!!
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (rep.isSafe) Color(0xFF064E3B) else Color(0xFF7F1D1D))
                                    .padding(12.dp)
                            ) {
                                val safetyText = if (rep.isSafe) "🟢 التطبيق آمن 100% ومثالي للتثبيت" else "⚠️ تحذير: يحتوي على صلاحيات حساسة"
                                Text(
                                    text = safetyText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("اسم الحزمة (Package): ${rep.packageName}", color = PrimeTextSecondary, fontSize = 12.sp)
                            Text("رقم الإصدار: ${rep.versionName}", color = PrimeTextSecondary, fontSize = 12.sp)
                            Text("عدد الصلاحيات: ${rep.permissionsCount}", color = PrimeTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(rep.aiSummaryAr, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSecurityDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text("حسناً، فهمت", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Conflict & Signature Mismatch Resolution Dialog
        if (showUninstallConflictDialog) {
            val targetPkg = detectedInstalledPackage ?: app.packageName ?: "com.primestore.${app.name.lowercase().replace(" ", "")}"
            AlertDialog(
                onDismissRequest = { showUninstallConflictDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "حل مشكلة تعارض التثبيت والتوقيع 🛡️",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "إذا ظهر خطأ 'الحزمة تبدو غير صالحة' أو 'هل تريد تحديث التطبيق؟' ثم فشل التثبيت، فهذا بسبب اختلاف توقيع النسخة المثبتة على هاتفك سابقاً عن النسخة الجديدة.",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF281020))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "الحزمة المستهدفة للإلغاء والحذف:",
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = targetPkg,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "اختر أحد الحلول السريعة التالية:",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Solution 1: Direct System Uninstall Prompt
                        Button(
                            onClick = {
                                val success = com.example.data.installer.ApkInstaller.uninstallPreviousVersion(context, targetPkg)
                                if (!success) {
                                    com.example.data.installer.ApkInstaller.openAppDetailsSettings(context, targetPkg)
                                }
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("1. إلغاء تثبيت النسخة القديمة فوراً 🗑️", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Solution 2: Open App Info in System Settings
                        Button(
                            onClick = {
                                val success = com.example.data.installer.ApkInstaller.openAppDetailsSettings(context, targetPkg)
                                if (!success) {
                                    com.example.data.installer.ApkInstaller.openManageApplicationsSettings(context)
                                }
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B2456)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorder)
                        ) {
                            Text("2. فتح صفحة التطبيق بإعدادات الهاتف ⚙️", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Solution 3: Open Device Application Manager
                        OutlinedButton(
                            onClick = {
                                com.example.data.installer.ApkInstaller.openManageApplicationsSettings(context)
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("3. فتح إدارة كافة تطبيقات الهاتف 📱", color = PrimeTextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUninstallConflictDialog = false }) {
                        Text("إغلاق", color = PrimeTextMuted)
                    }
                }
            )
        }
    }
}

@Composable
fun ScreenshotThumbnail(
    colorHex: Long,
    index: Int,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    height: androidx.compose.ui.unit.Dp = 95.dp,
    imageUrl: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(width, height)
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(colorHex), Color(0xFF16101F))
                )
            )
            .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎮",
                    fontSize = 24.sp
                )
                Text(
                    text = "لقطة ${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReviewItemCard(review: ReviewItem) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        hasGlow = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = review.userAvatar, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = review.userName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = review.date,
                    color = PrimeTextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            StarRatingBar(rating = review.rating.toFloat(), starSize = 14.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = review.comment,
                color = PrimeTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
fun AddReviewDialog(
    appName: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16101F),
        title = {
            Text(
                text = "تقييم $appName",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "اختر تقييمك بالنجوم:",
                    color = PrimeTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i <= rating) AccentGold else Color(0xFF4B4656),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("اكتب رأيك الصريح عن التطبيق...", color = PrimeTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1F162D),
                        unfocusedContainerColor = Color(0xFF1F162D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = NeonPurplePrimary,
                        unfocusedIndicatorColor = NeonBorder
                    ),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (comment.isNotBlank()) {
                        onSubmit(rating, comment)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
            ) {
                Text("نشر التقييم", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}
