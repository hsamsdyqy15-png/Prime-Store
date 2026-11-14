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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.github.StoreReleaseInfo
import com.example.data.model.StoreUpdateInfo
import com.example.ui.UserProfile
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentOption
import com.example.ui.theme.PrimeTheme

@Composable
fun SettingsScreen(
    currentLanguage: String,
    effectiveLanguage: String = currentLanguage,
    isDarkMode: Boolean,
    notificationsEnabled: Boolean,
    cacheSize: String,
    userProfile: UserProfile?,
    isAdmin: Boolean = false,
    hasNewUpdateBadge: Boolean = false,
    isCheckingUpdate: Boolean = false,
    availableStoreUpdate: StoreUpdateInfo? = null,
    isDownloadingStoreUpdate: Boolean = false,
    storeUpdateDownloadProgress: Float = 0f,
    showLoginDialog: Boolean = false,
    isAuthLoading: Boolean = false,
    authErrorMessage: String? = null,
    onOpenInstalledApps: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    updatesCount: Int = 0,
    installedCount: Int = 0,
    themeMode: String = "dark",
    themeAccent: Long = AccentOption.PURPLE.hex,
    onSetThemeMode: (String) -> Unit = {},
    onSetThemeAccent: (Long) -> Unit = {},
    autoTranslateEnabled: Boolean = true,
    wifiOnlyTranslation: Boolean = false,
    onToggleAutoTranslate: () -> Unit = {},
    onToggleWifiOnlyTranslation: () -> Unit = {},
    githubReleaseInfo: StoreReleaseInfo? = null,
    onDownloadGitHubRelease: (StoreReleaseInfo) -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onCloseLogin: () -> Unit = {},
    onSignIn: (email: String, pass: String) -> Unit = { _, _ -> },
    onSignUp: (email: String, pass: String) -> Unit = { _, _ -> },
    onToggleLanguage: () -> Unit,
    onSetLanguage: (String) -> Unit = {},
    onRefreshData: () -> Unit = {},
    onCheckForUpdate: (onResult: (hasUpdate: Boolean, info: StoreUpdateInfo?) -> Unit) -> Unit = {},
    onDownloadStoreUpdate: (StoreUpdateInfo) -> Unit = {},
    onDismissUpdateBadge: () -> Unit = {},
    onToggleDarkMode: () -> Unit,
    onToggleNotifications: () -> Unit,
    onClearCache: () -> Unit,
    onShareApp: () -> Unit,
    onRateApp: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenAdminPanel: () -> Unit = {},
    onAdminLogin: (email: String, pass: String) -> Boolean = { _, _ -> false },
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = PrimeTheme.colors

    var showAboutDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var showUpdateFoundDialog by remember { mutableStateOf(false) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<StoreUpdateInfo?>(null) }

    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    var adminEmailInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }

    var userEmailInput by remember { mutableStateOf("") }
    var userPasswordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    val resolvedLanguageDisplay = languageDisplayName(effectiveLanguage)
    val languageRowSubtitle = if (currentLanguage.equals("auto", ignoreCase = true)) {
        "${stringResource(R.string.lang_auto)} ($resolvedLanguageDisplay)"
    } else {
        resolvedLanguageDisplay
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // 1. Account Card
            item {
                val isUserLoggedIn = userProfile != null
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("account_card")
                        .clickable {
                            if (!isUserLoggedIn) {
                                onOpenLogin()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(colors.surface)
                                .border(1.dp, colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isUserLoggedIn) {
                                    if (isAdmin) "👑" else userProfile?.name?.firstOrNull()?.toString() ?: "U"
                                } else "👤",
                                fontSize = 22.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    !isUserLoggedIn -> stringResource(R.string.not_logged_in)
                                    isAdmin -> "${stringResource(R.string.admin_badge)} 🛠️"
                                    else -> userProfile?.name.orEmpty().ifBlank { stringResource(R.string.account_profile) }
                                },
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isUserLoggedIn) userProfile?.email.orEmpty() else stringResource(R.string.login_sync_hint),
                                color = if (isAdmin) colors.accent else colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isUserLoggedIn) {
                            if (isAdmin || (userProfile?.isPrimeMember == true)) {
                                Box(
                                    modifier = Modifier
                                        .background(colors.surface, RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isAdmin) stringResource(R.string.admin_badge) else "PRIME",
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "${stringResource(R.string.login)} ›",
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Hidden Admin Control Panel Row
            if (isAdmin) {
                item {
                    SettingsOptionItem(
                        title = stringResource(R.string.admin_panel_title),
                        subtitle = stringResource(R.string.admin_panel_desc),
                        icon = Icons.Filled.Build,
                        testTag = "admin_panel_button",
                        onClick = onOpenAdminPanel
                    )
                }
            }

            // 3. App Updates Row
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("app_updates_row")
                        .clickable { onOpenUpdates() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.app_updates_title),
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (updatesCount > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(AccentGold)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$updatesCount",
                                                color = Color(0xFF11071F),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (updatesCount > 0) {
                                        stringResource(R.string.app_updates_available, updatesCount)
                                    } else {
                                        stringResource(R.string.app_updates_all_up_to_date)
                                    },
                                    color = if (updatesCount > 0) colors.warning else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 4. Installed Apps Row
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.installed_apps_title),
                    subtitle = stringResource(R.string.installed_apps_subtitle, installedCount),
                    icon = Icons.Filled.Apps,
                    testTag = "installed_apps_row",
                    onClick = onOpenInstalledApps
                )
            }

            // 5. GitHub Self-Update Banner
            if (githubReleaseInfo != null) {
                item {
                    NeonCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        hasGlow = true
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.github_store_update_title),
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.github_version_size,
                                            githubReleaseInfo.versionName,
                                            githubReleaseInfo.assetSizeFormatted
                                        ),
                                        color = colors.accent,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.buttonGradient)
                                        .clickable { onDownloadGitHubRelease(githubReleaseInfo) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.github_update_now),
                                        color = colors.textOnAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (githubReleaseInfo.bodyNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = githubReleaseInfo.bodyNotes.take(150),
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 6. Store Version & Check Updates Row
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("check_store_update_row")
                        .clickable {
                            onCheckForUpdate { hasUpdate, info ->
                                if (hasUpdate && info != null) {
                                    pendingUpdateInfo = info
                                    showUpdateFoundDialog = true
                                } else {
                                    showUpToDateDialog = true
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.store_version_title),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasNewUpdateBadge) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.danger)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.store_new_update_dot),
                                        color = colors.danger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = stringResource(
                                    R.string.store_version_code,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE
                                ),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (hasNewUpdateBadge) colors.accent else colors.cardAlt)
                                    .border(
                                        width = 1.dp,
                                        color = if (hasNewUpdateBadge) colors.accent else colors.border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isCheckingUpdate) {
                                        stringResource(R.string.checking_updates)
                                    } else {
                                        stringResource(R.string.check_for_updates)
                                    },
                                    color = if (hasNewUpdateBadge) colors.textOnAccent else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 7. Language Picker Row
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.store_language),
                    subtitle = languageRowSubtitle,
                    icon = Icons.Filled.Language,
                    testTag = "language_settings_row",
                    onClick = { showLanguageDialog = true }
                )
            }

            // 8. Refresh Catalog Row
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.refresh_apps_data),
                    subtitle = stringResource(R.string.refresh_apps_desc),
                    icon = Icons.Filled.Refresh,
                    testTag = "refresh_catalog_row",
                    onClick = onRefreshData
                )
            }

            // 9. Appearance Mode & Accent Color Card
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.theme_mode_title),
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                "system" to stringResource(R.string.theme_mode_system),
                                "dark" to stringResource(R.string.theme_mode_dark),
                                "amoled" to stringResource(R.string.theme_mode_amoled),
                                "light" to stringResource(R.string.theme_mode_light)
                            )
                            modes.forEach { (modeKey, label) ->
                                val isSelected = themeMode.equals(modeKey, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colors.accent else colors.surface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) colors.accent else colors.border,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .testTag("theme_mode_$modeKey")
                                        .clickable { onSetThemeMode(modeKey) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) colors.textOnAccent else colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.accent_color_title),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccentOption.entries.forEach { option ->
                                val isChosen = themeAccent == option.hex
                                val label = stringResource(option.labelRes)
                                val circleColor = Color(option.hex)
                                val checkTint = if (option == AccentOption.CYAN || option == AccentOption.ORANGE) {
                                    Color(0xFF11071F)
                                } else {
                                    Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .semantics { contentDescription = label }
                                        .testTag("accent_color_${option.name.lowercase()}")
                                        .clickable { onSetThemeAccent(option.hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(circleColor)
                                            .border(
                                                width = if (isChosen) 2.5.dp else 1.dp,
                                                color = if (isChosen) colors.textPrimary else colors.border,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChosen) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = checkTint,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 10. AI Instant Translation Card
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ai_translation_title),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.ai_translation_desc),
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = autoTranslateEnabled,
                                onCheckedChange = { onToggleAutoTranslate() },
                                modifier = Modifier.testTag("auto_translate_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.textOnAccent,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textMuted,
                                    uncheckedTrackColor = colors.surface,
                                    uncheckedBorderColor = colors.border
                                )
                            )
                        }

                        if (autoTranslateEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.border.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.translation_wifi_only),
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.translation_wifi_desc),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = wifiOnlyTranslation,
                                    onCheckedChange = { onToggleWifiOnlyTranslation() },
                                    modifier = Modifier.testTag("wifi_only_translate_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.textOnAccent,
                                        checkedTrackColor = colors.accent,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surface,
                                        uncheckedBorderColor = colors.border
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 11. Notifications Toggle Card
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notifications),
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (notificationsEnabled) {
                                    stringResource(R.string.notifications_enabled)
                                } else {
                                    stringResource(R.string.notifications_disabled)
                                },
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { onToggleNotifications() },
                                modifier = Modifier.testTag("notifications_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.textOnAccent,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textMuted,
                                    uncheckedTrackColor = colors.surface,
                                    uncheckedBorderColor = colors.border
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 12. Storage Management
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.storage_mgmt),
                    subtitle = stringResource(R.string.cache_size_used, cacheSize),
                    icon = Icons.Filled.Storage,
                    testTag = "storage_mgmt_row",
                    onClick = { showCacheDialog = true }
                )
            }

            // 13. About Store
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.about_store),
                    subtitle = "${BuildConfig.VERSION_NAME} (${stringResource(R.string.app_name)})",
                    icon = Icons.Filled.Info,
                    testTag = "about_store_row",
                    onClick = { showAboutDialog = true }
                )
            }

            // 14. Share App
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.share_app),
                    subtitle = stringResource(R.string.app_subtitle),
                    icon = Icons.Filled.Share,
                    testTag = "share_store_row",
                    onClick = onShareApp
                )
            }

            // 15. Rate App
            item {
                SettingsOptionItem(
                    title = stringResource(R.string.rate_app),
                    subtitle = stringResource(R.string.rate_thanks_toast),
                    icon = Icons.Filled.Star,
                    testTag = "rate_store_row",
                    onClick = onRateApp
                )
            }

            // 16. Login / Logout Action Button
            item {
                Spacer(modifier = Modifier.height(14.dp))
                if (userProfile == null) {
                    NeonGradientButton(
                        text = stringResource(R.string.login),
                        icon = Icons.Default.Login,
                        testTag = "login_button",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenLogin
                    )
                } else {
                    NeonGradientButton(
                        text = stringResource(R.string.logout),
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        testTag = "logout_button",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLogoutClick
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // Update Found Dialog
        if (showUpdateFoundDialog && pendingUpdateInfo != null) {
            val update = pendingUpdateInfo!!
            AlertDialog(
                onDismissRequest = { showUpdateFoundDialog = false },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚀", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.update_available_title),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(
                                R.string.store_version_code,
                                update.versionName,
                                update.versionCode
                            ),
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (update.notes.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.admin_update_notes),
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = update.notes,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.update_available_desc),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isDownloadingStoreUpdate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.downloading_update_percent,
                                    (storeUpdateDownloadProgress * 100).toInt()
                                ),
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { storeUpdateDownloadProgress },
                                color = colors.accent,
                                trackColor = colors.border,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDownloadStoreUpdate(update)
                            onDismissUpdateBadge()
                        },
                        enabled = !isDownloadingStoreUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = colors.textOnAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.github_update_now),
                                color = colors.textOnAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateFoundDialog = false }) {
                        Text(stringResource(R.string.cancel), color = colors.textMuted)
                    }
                }
            )
        }

        // Already Up-To-Date Dialog
        if (showUpToDateDialog) {
            AlertDialog(
                onDismissRequest = { showUpToDateDialog = false },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.check_for_updates),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = "${stringResource(R.string.latest_version_toast)}\n${stringResource(R.string.store_version_code, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)}",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showUpToDateDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.ok), color = colors.textOnAccent)
                    }
                }
            )
        }

        // About Dialog with Secret 7-Tap Admin Access
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAboutDialog = false
                    versionTapCount = 0
                },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👑", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.app_name), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.about_store_desc),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                .testTag("about_secret_version_box")
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if (now - lastVersionTapTime > 2000) {
                                        versionTapCount = 0
                                    }
                                    lastVersionTapTime = now
                                    versionTapCount++

                                    if (versionTapCount >= 7) {
                                        versionTapCount = 0
                                        showAboutDialog = false
                                        showAdminLoginDialog = true
                                    } else if (versionTapCount >= 3) {
                                        onToast(context.getString(R.string.admin_secret_login) + " (${7 - versionTapCount})")
                                    }
                                }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.store_version_code,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE
                                ),
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAboutDialog = false
                            versionTapCount = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.ok), color = colors.textOnAccent)
                    }
                }
            )
        }

        // Admin Login Dialog
        if (showAdminLoginDialog) {
            AlertDialog(
                onDismissRequest = { showAdminLoginDialog = false },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛠️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.admin_login_button),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.admin_panel_desc),
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = adminEmailInput,
                            onValueChange = { adminEmailInput = it },
                            label = { Text(stringResource(R.string.admin_email_label), fontSize = 11.sp) },
                            placeholder = { Text("admin@example.com", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textMuted,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = adminPasswordInput,
                            onValueChange = { adminPasswordInput = it },
                            label = { Text(stringResource(R.string.admin_password_label), fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textMuted,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val success = onAdminLogin(adminEmailInput, adminPasswordInput)
                            if (success) {
                                showAdminLoginDialog = false
                                adminEmailInput = ""
                                adminPasswordInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.admin_login_button), color = colors.textOnAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminLoginDialog = false }) {
                        Text(stringResource(R.string.cancel), color = colors.textMuted)
                    }
                }
            )
        }

        // User Auth Dialog
        if (showLoginDialog) {
            AlertDialog(
                onDismissRequest = { onCloseLogin() },
                containerColor = colors.card,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👑", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSignUpMode) stringResource(R.string.sign_up) else stringResource(R.string.login),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.login_sync_hint),
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = userEmailInput,
                            onValueChange = { userEmailInput = it },
                            label = { Text(stringResource(R.string.email), fontSize = 11.sp) },
                            placeholder = { Text("user@example.com", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textMuted,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = userPasswordInput,
                            onValueChange = { userPasswordInput = it },
                            label = { Text(stringResource(R.string.password), fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                focusedLabelColor = colors.accent,
                                unfocusedLabelColor = colors.textMuted,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!authErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authErrorMessage,
                                color = colors.danger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isAuthLoading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                                Text(
                                    text = if (isSignUpMode) {
                                        stringResource(R.string.have_account_login)
                                    } else {
                                        stringResource(R.string.no_account_create)
                                    },
                                    color = colors.accent,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                onSignUp(userEmailInput, userPasswordInput)
                            } else {
                                onSignIn(userEmailInput, userPasswordInput)
                            }
                        },
                        enabled = !isAuthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(
                            text = if (isSignUpMode) stringResource(R.string.sign_up) else stringResource(R.string.login),
                            color = colors.textOnAccent
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onCloseLogin() }) {
                        Text(stringResource(R.string.cancel), color = colors.textMuted)
                    }
                }
            )
        }

        // Cache Clear Dialog
        if (showCacheDialog) {
            AlertDialog(
                onDismissRequest = { showCacheDialog = false },
                containerColor = colors.card,
                title = {
                    Text(text = stringResource(R.string.clear_cache), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = stringResource(R.string.clear_cache_confirm, cacheSize),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearCache()
                            showCacheDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text(stringResource(R.string.confirm), color = colors.textOnAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCacheDialog = false }) {
                        Text(stringResource(R.string.cancel), color = colors.textMuted)
                    }
                }
            )
        }
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.choose_language),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .fillMaxWidth()
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        val langs = listOf(
                            "auto" to "🌐 " + context.getString(R.string.lang_auto),
                            "ar" to "🇸🇦 " + context.getString(R.string.lang_ar),
                            "en" to "🇬🇧 " + context.getString(R.string.lang_en),
                            "tr" to "🇹🇷 " + context.getString(R.string.lang_tr),
                            "fr" to "🇫🇷 " + context.getString(R.string.lang_fr),
                            "es" to "🇪🇸 " + context.getString(R.string.lang_es),
                            "de" to "🇩🇪 " + context.getString(R.string.lang_de),
                            "ru" to "🇷🇺 " + context.getString(R.string.lang_ru),
                            "fa" to "🇮🇷 " + context.getString(R.string.lang_fa),
                            "ur" to "🇵🇰 " + context.getString(R.string.lang_ur),
                            "hi" to "🇮🇳 " + context.getString(R.string.lang_hi),
                            "id" to "🇮🇩 " + context.getString(R.string.lang_id),
                            "pt" to "🇵🇹 " + context.getString(R.string.lang_pt)
                        )
                        items(langs.size) { index ->
                            val (code, label) = langs[index]
                            val isCurrent = currentLanguage.equals(code, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) colors.surface else Color.Transparent)
                                    .testTag("lang_option_$code")
                                    .clickable {
                                        onSetLanguage(code)
                                        showLanguageDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isCurrent) colors.accent else colors.textPrimary,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.accent
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLanguageDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text(
                        text = stringResource(R.string.close),
                        color = colors.textOnAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
private fun languageDisplayName(code: String): String {
    return when (code.lowercase()) {
        "auto" -> stringResource(R.string.lang_auto)
        "ar" -> stringResource(R.string.lang_ar)
        "en" -> stringResource(R.string.lang_en)
        "tr" -> stringResource(R.string.lang_tr)
        "fr" -> stringResource(R.string.lang_fr)
        "es" -> stringResource(R.string.lang_es)
        "de" -> stringResource(R.string.lang_de)
        "ru" -> stringResource(R.string.lang_ru)
        "fa" -> stringResource(R.string.lang_fa)
        "ur" -> stringResource(R.string.lang_ur)
        "hi" -> stringResource(R.string.lang_hi)
        "id", "in" -> stringResource(R.string.lang_id)
        "pt" -> stringResource(R.string.lang_pt)
        else -> code
    }
}

@Composable
private fun SettingsOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String = "settings_option_item",
    onClick: () -> Unit
) {
    val colors = PrimeTheme.colors
    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTag)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
