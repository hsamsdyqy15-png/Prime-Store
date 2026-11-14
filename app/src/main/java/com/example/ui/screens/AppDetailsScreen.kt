package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.installer.ApkInstaller
import com.example.data.installer.PackageHelper
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.ReviewItem
import com.example.data.security.AiSecurityScanner
import com.example.data.security.SecurityReport
import com.example.data.translation.MlKitTranslatorHelper
import com.example.data.translation.TranslationOutcome
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.components.ProblemReportDialog
import com.example.ui.components.StarRatingBar
import com.example.ui.theme.AccentGold
import com.example.ui.theme.PrimeTheme
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailsScreen(
    app: AppItem,
    isFavorite: Boolean,
    downloadItem: DownloadItem?,
    reviews: List<ReviewItem>,
    allApps: List<AppItem> = emptyList(),
    targetLanguageCode: String = "en",
    autoTranslateEnabled: Boolean = true,
    wifiOnlyTranslation: Boolean = false,
    onDisableWifiOnlyAndRetry: () -> Unit = {},
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
    val colors = PrimeTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_ai_analysis),
        stringResource(R.string.tab_reviews),
        stringResource(R.string.tab_screenshots)
    )

    var showMenu by remember { mutableStateOf(false) }
    var showReviewModal by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var isScanningSecurity by remember { mutableStateOf(false) }
    var securityReport by remember { mutableStateOf<SecurityReport?>(null) }
    var showUninstallConflictDialog by remember { mutableStateOf(false) }
    var detectedInstalledPackage by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showConfirmUninstallDialog by remember { mutableStateOf(false) }

    // On-device ML Kit Translation state
    var showOriginalText by remember(app.id, targetLanguageCode) { mutableStateOf(false) }
    var translationRetryCount by remember { mutableIntStateOf(0) }
    var isTranslating by remember(app.id, targetLanguageCode) { mutableStateOf(false) }
    var translatedDesc by remember(app.id, targetLanguageCode) { mutableStateOf<String?>(null) }
    var translatedFeatures by remember(app.id, targetLanguageCode) { mutableStateOf<List<String>?>(null) }
    var translatedChangelog by remember(app.id, targetLanguageCode) { mutableStateOf<String?>(null) }
    var detectedSourceLang by remember(app.id, targetLanguageCode) { mutableStateOf<String?>(null) }
    var translationError by remember(app.id, targetLanguageCode) { mutableStateOf<String?>(null) }
    var isTranslationWifiBlocked by remember(app.id, targetLanguageCode) { mutableStateOf(false) }

    val baseDescription = remember(app, targetLanguageCode) {
        if (targetLanguageCode.equals("ar", ignoreCase = true)) {
            app.descriptionAr.ifBlank { app.descriptionEn }
        } else {
            app.descriptionEn.ifBlank { app.descriptionAr }
        }
    }

    LaunchedEffect(app.id, targetLanguageCode, autoTranslateEnabled, wifiOnlyTranslation, translationRetryCount) {
        if (!autoTranslateEnabled) {
            isTranslating = false
            translatedDesc = null
            translatedFeatures = null
            translatedChangelog = null
            detectedSourceLang = null
            translationError = null
            isTranslationWifiBlocked = false
            return@LaunchedEffect
        }

        isTranslating = true
        translationError = null
        isTranslationWifiBlocked = false
        translatedDesc = null
        translatedFeatures = null
        translatedChangelog = null
        detectedSourceLang = null

        val descOutcome = MlKitTranslatorHelper.translateField(
            context = context,
            appId = app.id,
            field = "description",
            originalText = baseDescription,
            targetLanguageCode = targetLanguageCode,
            wifiOnly = wifiOnlyTranslation
        )
        when (descOutcome) {
            is TranslationOutcome.Success -> {
                translatedDesc = descOutcome.translatedText
                detectedSourceLang = descOutcome.sourceLangTag
            }
            is TranslationOutcome.AlreadyInTargetLanguage -> {
                translatedDesc = null
            }
            is TranslationOutcome.Error -> {
                translationError = descOutcome.reason
                isTranslationWifiBlocked = descOutcome.isWifiBlocked
            }
        }

        if (translationError == null && app.featuresAr.isNotEmpty()) {
            val featOutcome = MlKitTranslatorHelper.translateField(
                context = context,
                appId = app.id,
                field = "features",
                originalText = app.featuresAr.joinToString("\n"),
                targetLanguageCode = targetLanguageCode,
                wifiOnly = wifiOnlyTranslation
            )
            when (featOutcome) {
                is TranslationOutcome.Success -> {
                    translatedFeatures = featOutcome.translatedText
                        .split("\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (detectedSourceLang == null) {
                        detectedSourceLang = featOutcome.sourceLangTag
                    }
                }
                is TranslationOutcome.AlreadyInTargetLanguage -> Unit
                is TranslationOutcome.Error -> {
                    if (translatedDesc == null) {
                        translationError = featOutcome.reason
                        isTranslationWifiBlocked = featOutcome.isWifiBlocked
                    }
                }
            }
        }

        val rawChangelog = app.changelog?.trim().orEmpty()
        if (translationError == null && rawChangelog.isNotBlank()) {
            val chgOutcome = MlKitTranslatorHelper.translateField(
                context = context,
                appId = app.id,
                field = "changelog",
                originalText = rawChangelog,
                targetLanguageCode = targetLanguageCode,
                wifiOnly = wifiOnlyTranslation
            )
            if (chgOutcome is TranslationOutcome.Success) {
                translatedChangelog = chgOutcome.translatedText
                if (detectedSourceLang == null) {
                    detectedSourceLang = chgOutcome.sourceLangTag
                }
            }
        }

        isTranslating = false
    }

    val installedInfo = remember(app.packageName, app.name) {
        val apkFile = ApkInstaller.getApkFile(context, app.id)
        val pkg = ApkInstaller.findInstalledPackage(context, app.name, apkFile, app.packageName)
        if (!pkg.isNullOrBlank()) PackageHelper.getInstalledInfo(context, pkg) else null
    }

    val similarApps = remember(allApps, app) {
        allApps.filter {
            it.id != app.id && (it.category == app.category || it.categoryKey == app.categoryKey)
        }.take(6)
    }

    val displayedDescription = if (!showOriginalText && !translatedDesc.isNullOrBlank()) {
        translatedDesc!!
    } else {
        baseDescription
    }
    val displayedFeatures = if (!showOriginalText && !translatedFeatures.isNullOrEmpty()) {
        translatedFeatures!!
    } else {
        app.featuresAr
    }
    val defaultWhatsNew = stringResource(R.string.whats_new_default)
    val displayedChangelog = if (!showOriginalText && !translatedChangelog.isNullOrBlank()) {
        translatedChangelog!!
    } else {
        app.changelog?.ifBlank { null } ?: defaultWhatsNew
    }
    val hasAnyTranslation = !translatedDesc.isNullOrBlank() ||
        !translatedFeatures.isNullOrEmpty() ||
        !translatedChangelog.isNullOrBlank()

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
                            contentDescription = stringResource(R.string.back),
                            tint = colors.textPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.testTag("details_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.tab_favorites),
                                tint = if (isFavorite) colors.danger else colors.textPrimary
                            )
                        }

                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.testTag("details_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = colors.textPrimary
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("details_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.options),
                                    tint = colors.textPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(colors.card)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share), color = colors.textPrimary) },
                                    onClick = {
                                        showMenu = false
                                        onShareClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.report_problem), color = colors.textPrimary) },
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

            // Header Info
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
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${app.rating}",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " (${app.ratingCount})",
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Version & Category Tags
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
                                .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = app.displayVersionBadge,
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = app.localizedCategoryTitle(),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        app.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = colors.textSecondary,
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
                                    .background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
                                    .border(1.dp, colors.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "🔓 ${stringResource(R.string.foss_badge)}",
                                    color = colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!app.sourceCodeUrl.isNullOrBlank()) {
                                val uriHandler = LocalUriHandler.current
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.cardAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .clickable {
                                            try {
                                                uriHandler.openUri(app.sourceCodeUrl)
                                            } catch (_: Exception) {
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "🐙 ${stringResource(R.string.source_code_button)} ↗",
                                        color = colors.textPrimary,
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
                            .background(colors.success.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                            .border(1.dp, colors.success.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "⚡ ${stringResource(R.string.architecture)}: ${app.supportedAbis}",
                            color = colors.success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download / Install / Update / Open Action Section
                    if (downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(if (colors.isDark) 8.dp else 2.dp, RoundedCornerShape(18.dp), ambientColor = colors.accentGlow, spotColor = colors.accent)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.downloading_percent, (downloadItem.progress * 100).toInt()),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = downloadItem.speed,
                                    color = colors.accent,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                        tint = colors.textPrimary
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
                                color = colors.accent,
                                trackColor = colors.surface
                            )
                        }
                    } else if (downloadItem != null && downloadItem.status == DownloadStatus.FAILED) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.danger.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                                .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = downloadItem.errorMessage ?: stringResource(R.string.download_failed_retry),
                                color = colors.danger,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            NeonGradientButton(
                                text = stringResource(R.string.download_retry),
                                icon = Icons.Filled.Download,
                                testTag = "details_retry_download_button",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onDownloadClick
                            )
                        }
                    } else if (downloadItem != null && (downloadItem.isCompleted || downloadItem.status == DownloadStatus.COMPLETED)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeonGradientButton(
                                text = stringResource(R.string.ready_to_install),
                                icon = Icons.Filled.CheckCircle,
                                testTag = "details_install_button",
                                modifier = Modifier.weight(1f),
                                onClick = onInstallClick
                            )
                            if (installedInfo != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showConfirmUninstallDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.danger.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.uninstall), tint = colors.danger)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.uninstall), color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (installedInfo != null) {
                        val hasUpdate = app.versionCode > installedInfo.versionCode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasUpdate) {
                                NeonGradientButton(
                                    text = "${stringResource(R.string.update_to_version, app.version)} (${app.size})",
                                    icon = Icons.Filled.Download,
                                    testTag = "details_update_button",
                                    modifier = Modifier.weight(1f),
                                    onClick = onDownloadClick
                                )
                            } else {
                                NeonGradientButton(
                                    text = stringResource(R.string.open_app),
                                    icon = Icons.Filled.OpenInNew,
                                    testTag = "details_open_button",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenAppClick(installedInfo.packageName) }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { showConfirmUninstallDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.danger.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.uninstall), tint = colors.danger)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.uninstall), color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        NeonGradientButton(
                            text = "${stringResource(R.string.download)} (${app.size})",
                            icon = Icons.Filled.Download,
                            testTag = "details_download_button",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDownloadClick
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // AI Security Scan Button
                    Button(
                        onClick = {
                            scope.launch {
                                isScanningSecurity = true
                                showSecurityDialog = true
                                val apkFile = ApkInstaller.getApkFile(context, app.id)
                                val report = AiSecurityScanner.scanApk(context, apkFile)
                                securityReport = report
                                isScanningSecurity = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("details_security_scan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.card),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.success.copy(alpha = 0.6f))
                    ) {
                        Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = colors.success)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.security_analysis),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Resolve Signature / Install Conflict Button
                    Button(
                        onClick = {
                            val apkFile = ApkInstaller.getApkFile(context, app.id)
                            val resolvedPkg = ApkInstaller.findInstalledPackage(
                                context,
                                app.name,
                                apkFile,
                                app.packageName
                            )
                            detectedInstalledPackage = resolvedPkg
                            showUninstallConflictDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("details_conflict_fix_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.danger.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "🛡️ ${stringResource(R.string.uninstall_and_install)}",
                            color = colors.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tabs Row
            item {
                Spacer(modifier = Modifier.height(14.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = colors.accent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = colors.accent,
                            height = 3.dp
                        )
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.border)
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
                                    color = if (selectedTab == index) colors.accent else colors.textMuted,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // ML Kit Translation Status Banner
                            if (isTranslating) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.cardAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.translating_text),
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            } else if (translationError != null && !hasAnyTranslation) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.danger.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                                        .border(1.dp, colors.danger.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (isTranslationWifiBlocked) {
                                            stringResource(R.string.translation_wifi_blocked_msg)
                                        } else {
                                            translationError ?: stringResource(R.string.translation_error_msg)
                                        },
                                        color = colors.danger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(
                                        onClick = {
                                            if (isTranslationWifiBlocked) {
                                                onDisableWifiOnlyAndRetry()
                                            }
                                            translationRetryCount++
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isTranslationWifiBlocked) {
                                                stringResource(R.string.disable_wifi_and_retry)
                                            } else {
                                                stringResource(R.string.retry)
                                            },
                                            color = colors.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            } else if (hasAnyTranslation) {
                                val srcDisplay = remember(detectedSourceLang) {
                                    MlKitTranslatorHelper.getLanguageDisplayName(detectedSourceLang ?: "auto")
                                        .ifBlank { detectedSourceLang?.uppercase() ?: "Auto" }
                                }
                                val tgtDisplay = remember(targetLanguageCode) {
                                    MlKitTranslatorHelper.getLanguageDisplayName(targetLanguageCode)
                                        .ifBlank { targetLanguageCode.uppercase() }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.cardAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (showOriginalText) {
                                            stringResource(R.string.original_text_badge, srcDisplay)
                                        } else {
                                            stringResource(R.string.auto_translated_badge, srcDisplay, tgtDisplay)
                                        },
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (showOriginalText) {
                                            stringResource(R.string.show_translation)
                                        } else {
                                            stringResource(R.string.show_original)
                                        },
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .testTag("toggle_translation_button")
                                            .clickable { showOriginalText = !showOriginalText }
                                            .padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Changelog Expandable Section
                            var isChangelogExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.card)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { isChangelogExpanded = !isChangelogExpanded }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text("🚀 ", fontSize = 15.sp)
                                            Text(
                                                text = "${stringResource(R.string.whats_new)} (v${app.version})",
                                                color = colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isChangelogExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isChangelogExpanded) {
                                        Text(
                                            text = displayedChangelog,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = displayedDescription,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )

                            if (displayedFeatures.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = stringResource(R.string.features),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                displayedFeatures.forEach { feature ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = feature,
                                            color = colors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = stringResource(R.string.screenshots),
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.security_analysis),
                                            color = colors.accent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.ai_safe_status),
                                            color = colors.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${stringResource(R.string.architecture)}: ${app.supportedAbis}",
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.accent)
                                            .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "9.9", color = colors.textOnAccent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                            Text(text = "/ 10", color = colors.textOnAccent.copy(alpha = 0.85f), fontSize = 9.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // AI Safety Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.success.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                                    .border(1.dp, colors.success.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.success,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.ai_safe_status),
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.ai_summary_safe),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Technical Specifications Card
                            Text(
                                text = stringResource(R.string.package_architecture_info),
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = colors.card),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SpecRow(
                                        label = stringResource(R.string.architecture),
                                        value = app.supportedAbis,
                                        valueColor = colors.accent
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SpecRow(
                                        label = stringResource(R.string.version),
                                        value = app.version,
                                        valueColor = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SpecRow(
                                        label = stringResource(R.string.package_name),
                                        value = app.packageName ?: "com.primestore.${app.name.lowercase().replace(" ", "")}",
                                        valueColor = colors.accent
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SpecRow(
                                        label = stringResource(R.string.foss_badge),
                                        value = if (app.isOpenSource) stringResource(R.string.onboarding_perm_enabled) else "—",
                                        valueColor = if (app.isOpenSource) colors.success else colors.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Core Highlights
                            listOf(
                                stringResource(R.string.onboarding_feat_1),
                                stringResource(R.string.onboarding_feat_2),
                                stringResource(R.string.onboarding_feat_3),
                                stringResource(R.string.onboarding_feat_4)
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
                                    Text(itemText, color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
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
                                    text = stringResource(R.string.reviews),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.buttonGradient)
                                        .testTag("add_review_button")
                                        .clickable { showReviewModal = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "+ ${stringResource(R.string.add_review_title)}",
                                        color = colors.textOnAccent,
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
                                        text = stringResource(R.string.no_reviews_yet),
                                        color = colors.textMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
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
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            if (app.screenshotUrls.isEmpty() && app.screenshotsColors.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.screenshots_empty),
                                        color = colors.textMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            text = stringResource(R.string.similar_apps),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = simApp.size,
                                            color = colors.textMuted,
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
                appName = app.nameAr.ifBlank { app.name },
                onDismiss = { showReviewModal = false },
                onSubmit = { rating, comment ->
                    onAddReview(rating, comment)
                    showReviewModal = false
                }
            )
        }

        // Problem Report Dialog
        if (showReportDialog) {
            ProblemReportDialog(
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
                containerColor = colors.card,
                title = {
                    Text(
                        text = stringResource(R.string.confirm_uninstall_title),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.confirm_uninstall_msg, app.nameAr.ifBlank { app.name }),
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmUninstallDialog = false
                            onUninstallClick(installedInfo.packageName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                    ) {
                        Text(stringResource(R.string.yes_uninstall), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmUninstallDialog = false }) {
                        Text(stringResource(R.string.cancel), color = colors.textMuted)
                    }
                }
            )
        }

        // AI Security Report Dialog
        if (showSecurityDialog) {
            AlertDialog(
                onDismissRequest = { showSecurityDialog = false },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.security_analysis),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    if (isScanningSecurity) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = colors.accent)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.ai_scan_loading),
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (securityReport != null) {
                        val rep = securityReport!!
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (rep.isSafe) {
                                            colors.success.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
                                        } else {
                                            colors.danger.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (rep.isSafe) colors.success else colors.danger,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (rep.isSafe) stringResource(R.string.ai_safe_status) else stringResource(R.string.ai_caution_status),
                                    color = if (rep.isSafe) colors.success else colors.danger,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("${stringResource(R.string.package_name)}: ${rep.packageName}", color = colors.textSecondary, fontSize = 12.sp)
                            Text("${stringResource(R.string.version)}: ${rep.versionName}", color = colors.textSecondary, fontSize = 12.sp)
                            Text(stringResource(R.string.risk_score_label, rep.riskScore), color = colors.textSecondary, fontSize = 12.sp)
                            Text(stringResource(R.string.permissions_count_label, rep.permissionsCount), color = colors.textSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (rep.dangerousPermissions.isEmpty()) {
                                Text(stringResource(R.string.no_dangerous_permissions), color = colors.success, fontSize = 12.sp)
                            } else {
                                Text(
                                    text = stringResource(R.string.sensitive_permissions_label, rep.dangerousPermissions.size),
                                    color = colors.danger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(rep.aiSummaryAr, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSecurityDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.ok), color = colors.textOnAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Conflict & Signature Mismatch Resolution Dialog
        if (showUninstallConflictDialog) {
            val targetPkg = detectedInstalledPackage ?: app.packageName ?: "com.primestore.${app.name.lowercase().replace(" ", "")}"
            AlertDialog(
                onDismissRequest = { showUninstallConflictDialog = false },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = colors.danger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.signature_conflict_title),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.android_sig_security_note),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.danger.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
                                .border(1.dp, colors.danger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.package_name),
                                    color = colors.danger,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = targetPkg,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val success = ApkInstaller.uninstallPreviousVersion(context, targetPkg)
                                if (!success) {
                                    ApkInstaller.openAppDetailsSettings(context, targetPkg)
                                }
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.uninstall_from_device),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val success = ApkInstaller.openAppDetailsSettings(context, targetPkg)
                                if (!success) {
                                    ApkInstaller.openManageApplicationsSettings(context)
                                }
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.cardAlt),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = stringResource(R.string.details),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                ApkInstaller.openManageApplicationsSettings(context)
                                showUninstallConflictDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.installed_apps_title),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUninstallConflictDialog = false }) {
                        Text(stringResource(R.string.close), color = colors.textMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun SpecRow(
    label: String,
    value: String,
    valueColor: Color
) {
    val colors = PrimeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ScreenshotThumbnail(
    colorHex: Long,
    index: Int,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    height: Dp = 95.dp,
    imageUrl: String? = null,
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors
    Box(
        modifier = modifier
            .size(width, height)
            .shadow(if (colors.isDark) 6.dp else 2.dp, RoundedCornerShape(14.dp), ambientColor = colors.accentGlow, spotColor = colors.accent)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(colorHex), colors.cardAlt)
                )
            )
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.screenshots),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🎮", fontSize = 24.sp)
                Text(
                    text = "${stringResource(R.string.screenshots)} ${index + 1}",
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
    val colors = PrimeTheme.colors
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
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = review.date,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            StarRatingBar(rating = review.rating.toFloat(), starSize = 14.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = review.comment,
                color = colors.textSecondary,
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
    val colors = PrimeTheme.colors
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = "${stringResource(R.string.add_review_title)} — $appName",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.rating),
                    color = colors.textSecondary,
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
                                tint = if (i <= rating) AccentGold else colors.textMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text(stringResource(R.string.review_comment_hint), color = colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(12.dp),
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
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text(stringResource(R.string.submit_review), color = colors.textOnAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = colors.textMuted)
            }
        }
    )
}
