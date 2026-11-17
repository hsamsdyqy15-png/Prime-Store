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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppActionState
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.resolveAppActionState
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.components.pressScaleClickable
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimeTheme

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    filterTab: Int, // 0 = Active, 1 = Completed
    allApps: List<AppItem> = emptyList(),
    installedPkgByAppId: Map<String, InstalledPackageSummary> = emptyMap(),
    downloadedReadyIds: Set<String> = emptySet(),
    installingAppIds: Set<String> = emptySet(),
    signatureConflictAppIds: Set<String> = emptySet(),
    listState: LazyListState = rememberLazyListState(),
    onFilterChange: (Int) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClearAll: () -> Unit,
    onAppClick: (String) -> Unit,
    onInstall: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    onDeleteApkFile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val activeDownloads = remember(downloads) { downloads.filter { !it.isCompleted } }
    val completedDownloads = remember(downloads) { downloads.filter { it.isCompleted } }
    val appsById = remember(allApps) { allApps.associateBy { it.id } }

    val displayedList = if (filterTab == 0) activeDownloads else completedDownloads

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_screen")
    ) {
        // Title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.downloads_title),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Segmented Tabs
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
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (filterTab == 0) colors.accent else Color.Transparent)
                            .clickable { onFilterChange(0) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(R.string.downloading)} (${activeDownloads.size})",
                            color = if (filterTab == 0) colors.textOnAccent else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (filterTab == 1) colors.accent else Color.Transparent)
                            .clickable { onFilterChange(1) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(R.string.downloaded)} (${completedDownloads.size})",
                            color = if (filterTab == 1) colors.textOnAccent else colors.textSecondary,
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
                        text = if (filterTab == 0) {
                            stringResource(R.string.no_active_downloads)
                        } else {
                            stringResource(R.string.no_completed_downloads)
                        },
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val distinctDownloads = remember(displayedList) { displayedList.distinctBy { it.appId } }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = distinctDownloads,
                    key = { "dl_${it.appId}" },
                    contentType = { if (!it.isCompleted && it.status != DownloadStatus.COMPLETED) "active_dl" else "completed_dl" }
                ) { item ->
                    if (!item.isCompleted && item.status != DownloadStatus.COMPLETED) {
                        ActiveDownloadCard(
                            item = item,
                            onPause = { onPauseDownload(item.appId) },
                            onResume = { onResumeDownload(item.appId) },
                            onCancel = { onCancelDownload(item.appId) },
                            onClick = { onAppClick(item.appId) },
                            modifier = Modifier.animateItem()
                        )
                    } else {
                        val appItem = appsById[item.appId] ?: AppItem(
                            id = item.appId,
                            name = item.appName,
                            nameAr = item.appName,
                            category = AppCategory.TOOLS,
                            tags = emptyList(),
                            rating = 4.5f,
                            ratingCount = "0",
                            downloadCount = "0",
                            size = item.totalSize,
                            sizeBytes = 0L,
                            iconEmoji = item.appIcon,
                            accentColor = item.accentColor,
                            descriptionAr = "",
                            descriptionEn = "",
                            featuresAr = emptyList(),
                            screenshotsColors = emptyList()
                        )
                        val installedInfo = installedPkgByAppId[item.appId]
                        val actionState = resolveAppActionState(
                            app = appItem,
                            downloadItem = item,
                            isApkReady = downloadedReadyIds.contains(item.appId) || item.isCompleted,
                            isInstalling = installingAppIds.contains(item.appId),
                            hasSignatureConflict = signatureConflictAppIds.contains(item.appId),
                            installedInfo = installedInfo
                        )
                        CompletedDownloadCard(
                            item = item,
                            actionState = actionState,
                            installedPackageName = installedInfo?.packageName,
                            iconUrl = appItem.iconUrl,
                            onClick = { onAppClick(item.appId) },
                            onInstall = { onInstall(item.appId) },
                            onUninstall = onUninstall,
                            onOpenApp = onOpenApp,
                            onDeleteApk = { onDeleteApkFile(item.appId) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Bottom Action Button: Clear All
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            NeonGradientButton(
                text = stringResource(R.string.clear_all),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors

    NeonCard(
        modifier = modifier.fillMaxWidth(),
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
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.status == DownloadStatus.FAILED) {
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.accentGradient)
                                        .clickable { onResume() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.download_retry),
                                        color = colors.textOnAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                IconButton(
                                    onClick = if (item.isPaused) onResume else onPause,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (item.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (item.isPaused) {
                                            stringResource(R.string.download_resume)
                                        } else {
                                            stringResource(R.string.download_pause)
                                        },
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (item.status == DownloadStatus.FAILED) {
                        Text(
                            text = item.errorMessage ?: stringResource(R.string.download_failed_retry),
                            color = AccentRed,
                            fontSize = 11.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.border)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.progress.coerceIn(0.01f, 1.0f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.accent)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(item.progress * 100).toInt()}%",
                                color = colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.speed} • ${item.downloadedSize}",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    item: DownloadItem,
    actionState: AppActionState,
    installedPackageName: String?,
    iconUrl: String? = null,
    onClick: () -> Unit,
    onInstall: () -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    onDeleteApk: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    val isInstalled = !installedPackageName.isNullOrBlank()
    val isInstalling = actionState is AppActionState.Installing

    NeonCard(
        modifier = modifier.fillMaxWidth(),
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
                    size = 50.dp,
                    imageUrl = iconUrl
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = item.appName,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isInstalled) {
                            stringResource(R.string.installed_on_device_size, item.totalSize)
                        } else {
                            stringResource(R.string.download_completed_size, item.totalSize)
                        },
                        color = if (isInstalled) AccentGreen else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isInstalled) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    isInstalling -> {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardAlt)
                                .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.installing_dots),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    actionState is AppActionState.Open && !installedPackageName.isNullOrBlank() -> {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .pressScaleClickable(withHaptic = true) { onOpenApp(installedPackageName) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.open_app),
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.open_app),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    actionState is AppActionState.Update -> {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentGradient)
                                .pressScaleClickable(withHaptic = true) { onInstall() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = stringResource(R.string.update),
                                    tint = colors.textOnAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.update),
                                    color = colors.textOnAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentGradient)
                                .pressScaleClickable(withHaptic = true) { onInstall() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(R.string.install_app),
                                    tint = colors.textOnAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.install_app),
                                    color = colors.textOnAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // More Menu ⋮
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.options),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        if (isInstalled && !installedPackageName.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reinstall_apk), color = colors.textPrimary) },
                                onClick = {
                                    showMenu = false
                                    onInstall()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.uninstall_from_device), color = AccentRed) },
                                onClick = {
                                    showMenu = false
                                    showUninstallDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_apk_file), color = colors.textPrimary) },
                            onClick = {
                                showMenu = false
                                onDeleteApk()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showUninstallDialog && !installedPackageName.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            containerColor = colors.card,
            title = {
                Text(stringResource(R.string.confirm_uninstall_title), color = colors.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(stringResource(R.string.confirm_uninstall_msg, item.appName), color = colors.textSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallDialog = false
                        onUninstall(installedPackageName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text(stringResource(R.string.uninstall), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textMuted)
                }
            }
        )
    }
}
