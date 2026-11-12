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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import com.example.data.github.StoreReleaseInfo
import com.example.ui.theme.AccentGold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.StoreUpdateInfo
import com.example.ui.UserProfile
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun SettingsScreen(
    currentLanguage: String,
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
    themeAccent: Long = 0xFFA855F7,
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
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Store update dialog states
    var showUpdateFoundDialog by remember { mutableStateOf(false) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<StoreUpdateInfo?>(null) }

    // Secret 7-tap counter
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    // Admin login form state
    var adminEmailInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }

    // User login dialog state
    var userEmailInput by remember { mutableStateOf("") }
    var userPasswordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Title: الإعدادات
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "الإعدادات",
                color = Color.White,
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
            // User Card
            item {
                val isUserLoggedIn = userProfile != null
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
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
                                .background(NeonPurpleDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isUserLoggedIn) (if (isAdmin) "👑" else userProfile?.name?.firstOrNull()?.toString() ?: "U") else "👤",
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUserLoggedIn) (if (isAdmin) "مدير النظام (Admin) 🛠️" else userProfile?.name.orEmpty()) else "غير مسجل الدخول",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isUserLoggedIn) userProfile?.email.orEmpty() else "سجّل دخولك للمزامنة والميزات الكاملة",
                                color = if (isAdmin) NeonPurplePrimary else PrimeTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isUserLoggedIn) {
                            if (isAdmin || (userProfile?.isPrimeMember == true)) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF281347),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(1.dp, NeonPurplePrimary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isAdmin) "أدمن" else "PRIME",
                                        color = NeonPurplePrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "دخول ›",
                                color = NeonPurplePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Hidden Admin Control Panel row (ONLY visible if logged-in user is ADMIN)
            if (isAdmin) {
                item {
                    SettingsOptionItem(
                        title = "لوحة التحكم",
                        subtitle = "إدارة التطبيقات والرفع إلى المتجر (المشرف)",
                        icon = Icons.Filled.Build,
                        onClick = onOpenAdminPanel
                    )
                }
            }

            // Updates Row with Badge
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
                                    .background(Color(0xFF281C38)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = NeonPurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "تحديثات التطبيقات",
                                        color = Color.White,
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
                                                color = Color.Black,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (updatesCount > 0) "يتوفر $updatesCount تحديث جديد لتطبيقاتك" else "جميع تطبيقاتك محدثة لآخر إصدار",
                                    color = if (updatesCount > 0) AccentGold else PrimeTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = PrimeTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Installed Apps Row
            item {
                SettingsOptionItem(
                    title = "تطبيقاتي المثبتة",
                    subtitle = if (installedCount > 0) "$installedCount تطبيق مثبت من المتجر" else "عرض وإدارة التطبيقات المثبتة",
                    icon = Icons.Filled.Apps,
                    onClick = onOpenInstalledApps
                )
            }

            // GitHub Self-Update Banner
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
                                        text = "تحديث المتجر متاح عبر GitHub 🐙",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "الإصدار v${githubReleaseInfo.versionName} (${githubReleaseInfo.assetSizeFormatted})",
                                        color = NeonPurplePrimary,
                                        fontSize = 12.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PrimeGradientBrush)
                                        .clickable { onDownloadGitHubRelease(githubReleaseInfo) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("تحديث الآن ⚡", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (githubReleaseInfo.bodyNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = githubReleaseInfo.bodyNotes.take(150),
                                    color = PrimeTextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // Store Version & Update Check Row (مع نقطة حمراء وزر فحص التحديث)
            // =================================================================
            item {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
                                    text = "الإصدار",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasNewUpdateBadge) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AccentRed)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تحديث جديد متوفر!",
                                        color = AccentRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Prime Store v${BuildConfig.VERSION_NAME} (كود ${BuildConfig.VERSION_CODE})",
                                color = PrimeTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    color = NeonPurplePrimary,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(if (hasNewUpdateBadge) Modifier.background(PrimeGradientBrush) else Modifier.background(Color(0xFF281C38)))
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isCheckingUpdate) "فحص..." else "بحث عن تحديث",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = NeonPurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 1. اللغة (Language) 🌐
            item {
                SettingsOptionItem(
                    title = "اللغة",
                    subtitle = when (currentLanguage) {
                        "ar" -> "🇸🇦 العربية (Arabic)"
                        "en" -> "🇬🇧 English"
                        "fr" -> "🇫🇷 Français (French)"
                        "es" -> "🇪🇸 Español (Spanish)"
                        "de" -> "🇩🇪 Deutsch (German)"
                        "tr" -> "🇹🇷 Türkçe (Turkish)"
                        "ru" -> "🇷🇺 Русский (Russian)"
                        else -> currentLanguage
                    },
                    icon = Icons.Filled.Language,
                    onClick = { showLanguageDialog = true }
                )
            }

            // 1.1 تحديث البيانات والمنشورات فوراً 🔄
            item {
                SettingsOptionItem(
                    title = "تحديث المنشورات والتطبيقات",
                    subtitle = "إعادة جلب وتحديث كافة التطبيقات من السحابة",
                    icon = Icons.Filled.Refresh,
                    onClick = onRefreshData
                )
            }

            // 2. المظهر والألوان (Theme & Colors) 🌙🎨
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = null,
                                tint = NeonPurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "نمط المظهر",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                "dark" to "داكن",
                                "amoled" to "AMOLED",
                                "light" to "فاتح"
                            )
                            modes.forEach { (modeKey, label) ->
                                val isSelected = themeMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) NeonPurplePrimary else Color(0xFF1F152E)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.White else NeonBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSetThemeMode(modeKey) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else PrimeTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "لون التمييز (Accent Color)",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val colors = listOf(
                            0xFFA855F7L to "بنفسجي",
                            0xFF06B6D4L to "سماوي",
                            0xFF10B981L to "زمردي",
                            0xFFF43F5EL to "وردي",
                            0xFFF59E0BL to "عنبر"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colors.forEach { (colorVal, _) ->
                                val isChosen = themeAccent == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (isChosen) 3.dp else 1.dp,
                                            color = if (isChosen) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onSetThemeAccent(colorVal) }
                                )
                            }
                        }
                    }
                }
            }

            // 2.1 إعدادات الترجمة بالذكاء الاصطناعي (AI Translation) 🌐🤖
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
                                    text = "الترجمة الفورية بالذكاء الاصطناعي",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ترجمة تلقائية للوصف والميزات إلى لغة جهازك على الجهاز محلياً",
                                    color = PrimeTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = autoTranslateEnabled,
                                onCheckedChange = { onToggleAutoTranslate() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonPurplePrimary,
                                    uncheckedThumbColor = PrimeTextMuted,
                                    uncheckedTrackColor = Color(0xFF281C38)
                                )
                            )
                        }

                        if (autoTranslateEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(NeonBorder.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تنزيل نماذج الترجمة عبر Wi-Fi فقط",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "لتوفير باقة بيانات الجوال",
                                        color = PrimeTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = wifiOnlyTranslation,
                                    onCheckedChange = { onToggleWifiOnlyTranslation() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NeonPurplePrimary,
                                        uncheckedThumbColor = PrimeTextMuted,
                                        uncheckedTrackColor = Color(0xFF281C38)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. الإشعارات (Notifications) 🔔
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
                        Column {
                            Text(
                                text = "الإشعارات",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (notificationsEnabled) "مفعلة" else "معطلة",
                                color = PrimeTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { onToggleNotifications() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonPurplePrimary,
                                    uncheckedThumbColor = PrimeTextMuted,
                                    uncheckedTrackColor = Color(0xFF281C38)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = NeonPurplePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 4. إدارة التخزين (Storage Management) 🗄
            item {
                SettingsOptionItem(
                    title = "إدارة التخزين",
                    subtitle = "ذاكرة التخزين المؤقت: $cacheSize",
                    icon = Icons.Filled.Storage,
                    onClick = { showCacheDialog = true }
                )
            }

            // 5. حول التطبيق (About App) ℹ
            item {
                SettingsOptionItem(
                    title = "حول التطبيق",
                    subtitle = "الإصدار ${BuildConfig.VERSION_NAME} (Prime Store)",
                    icon = Icons.Filled.Info,
                    onClick = { showAboutDialog = true }
                )
            }

            // 6. مشاركة التطبيق (Share App) 🔗
            item {
                SettingsOptionItem(
                    title = "مشاركة التطبيق",
                    subtitle = "شارك المتجر مع أصدقائك",
                    icon = Icons.Filled.Share,
                    onClick = onShareApp
                )
            }

            // 7. تقييم التطبيق (Rate App) ⭐
            item {
                SettingsOptionItem(
                    title = "تقييم التطبيق",
                    subtitle = "ساعدنا بالتقييم 5 نجوم على المتجر",
                    icon = Icons.Filled.Star,
                    onClick = onRateApp
                )
            }

            // 8. زر الإجراء: تسجيل الدخول أو تسجيل الخروج
            item {
                Spacer(modifier = Modifier.height(14.dp))
                if (userProfile == null) {
                    NeonGradientButton(
                        text = "تسجيل الدخول",
                        icon = Icons.Default.Login,
                        testTag = "login_button",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenLogin
                    )
                } else {
                    NeonGradientButton(
                        text = "تسجيل الخروج",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        testTag = "logout_button",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLogoutClick
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // =====================================================================
        // Store Update Dialogs
        // =====================================================================

        // Update Found Dialog
        if (showUpdateFoundDialog && pendingUpdateInfo != null) {
            val update = pendingUpdateInfo!!
            AlertDialog(
                onDismissRequest = { showUpdateFoundDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚀", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحديث جديد لـ Prime Store!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "الإصدار الجديد: ${update.versionName} (كود ${update.versionCode})",
                            color = NeonPurplePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (update.notes.isNotBlank()) {
                            Text(
                                text = "ملاحظات التحديث:",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = update.notes,
                                color = PrimeTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        } else {
                            Text(
                                text = "يتضمن هذا التحديث تحسينات في الأداء وإصلاحات لمشاكل التثبيت والتنزيل.",
                                color = PrimeTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isDownloadingStoreUpdate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "جاري تحميل التحديث... ${(storeUpdateDownloadProgress * 100).toInt()}%",
                                color = NeonPurplePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { storeUpdateDownloadProgress },
                                color = NeonPurplePrimary,
                                trackColor = Color(0xFF281C38),
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحديث الآن", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateFoundDialog = false }) {
                        Text("لاحقاً", color = PrimeTextMuted)
                    }
                }
            )
        }

        // Already Up-To-Date Dialog
        if (showUpToDateDialog) {
            AlertDialog(
                onDismissRequest = { showUpToDateDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = NeonPurplePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "أنت على آخر إصدار", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        text = "أنت تستخدم أحدث نسخة من متجر Prime Store (${BuildConfig.VERSION_NAME}). لا توجد تحديثات جديدة حالياً.",
                        color = PrimeTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showUpToDateDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text("حسناً", color = Color.White)
                    }
                }
            )
        }

        // About Dialog with Secret 7-Tap on Version
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAboutDialog = false
                    versionTapCount = 0
                },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👑", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Prime Store", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "متجر التطبيقات والألعاب الرائد",
                            color = NeonPurplePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تطبيق Prime Store يوفر لك الوصول السريع والآمن إلى أفضل وأشهر الألعاب والتطبيقات العالمية بواجهة نيون عصرية، وإدارة متطورة للتنزيلات والمفضلة.",
                            color = PrimeTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Secret Version Row: Tap 7 times to reveal Admin Login!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F1530))
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
                                        onToast("متبقي ${7 - versionTapCount} نقرات لفتح دخول الأدمن")
                                    }
                                }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "الإصدار: v${BuildConfig.VERSION_NAME} (كود ${BuildConfig.VERSION_CODE})\nجميع الحقوق محفوظة © Prime Store Team",
                                color = PrimeTextMuted,
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text("حسناً", color = Color.White)
                    }
                }
            )
        }

        // Admin Login Dialog (Hidden Access)
        if (showAdminLoginDialog) {
            AlertDialog(
                onDismissRequest = { showAdminLoginDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛠️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "دخول الأدمن (Supabase Auth)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "أدخل بيانات حساب الأدمن المصرح له لإدارة متجر التطبيقات:",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = adminEmailInput,
                            onValueChange = { adminEmailInput = it },
                            label = { Text("البريد الإلكتروني", fontSize = 11.sp) },
                            placeholder = { Text("zaim9002@gmail.com", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurplePrimary,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = NeonPurplePrimary,
                                unfocusedLabelColor = PrimeTextMuted,
                                focusedContainerColor = Color(0xFF140D24),
                                unfocusedContainerColor = Color(0xFF140D24)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = adminPasswordInput,
                            onValueChange = { adminPasswordInput = it },
                            label = { Text("كلمة المرور", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurplePrimary,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = NeonPurplePrimary,
                                unfocusedLabelColor = PrimeTextMuted,
                                focusedContainerColor = Color(0xFF140D24),
                                unfocusedContainerColor = Color(0xFF140D24)
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text("تسجيل دخول الأدمن", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminLoginDialog = false }) {
                        Text("إلغاء", color = PrimeTextMuted)
                    }
                }
            )
        }

        // User Auth Dialog (Supabase Auth - Login / Sign Up)
        if (showLoginDialog) {
            AlertDialog(
                onDismissRequest = { onCloseLogin() },
                containerColor = Color(0xFF16101F),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "👑", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSignUpMode) "إنشاء حساب جديد" else "تسجيل الدخول",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = if (isSignUpMode) "أنشئ حسابك في Prime Store للوصول السحابي وحفظ مفضلاتك:" else "أدخل بريدك وكلمة المرور لتسجيل الدخول إلى حسابك:",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = userEmailInput,
                            onValueChange = { userEmailInput = it },
                            label = { Text("البريد الإلكتروني", fontSize = 11.sp) },
                            placeholder = { Text("user@example.com", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurplePrimary,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = NeonPurplePrimary,
                                unfocusedLabelColor = PrimeTextMuted,
                                focusedContainerColor = Color(0xFF140D24),
                                unfocusedContainerColor = Color(0xFF140D24)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = userPasswordInput,
                            onValueChange = { userPasswordInput = it },
                            label = { Text("كلمة المرور", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurplePrimary,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = NeonPurplePrimary,
                                unfocusedLabelColor = PrimeTextMuted,
                                focusedContainerColor = Color(0xFF140D24),
                                unfocusedContainerColor = Color(0xFF140D24)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!authErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authErrorMessage,
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isAuthLoading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonPurplePrimary, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                                Text(
                                    text = if (isSignUpMode) "لديك حساب بالفعل؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب جديد",
                                    color = NeonPurplePrimary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text(if (isSignUpMode) "إنشاء الحساب" else "تسجيل الدخول", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onCloseLogin() }) {
                        Text("إلغاء", color = PrimeTextMuted)
                    }
                }
            )
        }

        // Cache Clear Dialog
        if (showCacheDialog) {
            AlertDialog(
                onDismissRequest = { showCacheDialog = false },
                containerColor = Color(0xFF16101F),
                title = {
                    Text(text = "مسح ذاكرة التخزين المؤقت", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "المساحة الحالية المستخدمة هي $cacheSize. هل تريد تحرير هذه المساحة ومسح الملفات المؤقتة؟",
                        color = PrimeTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearCache()
                            showCacheDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                    ) {
                        Text("مسح الآن", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCacheDialog = false }) {
                        Text("إلغاء", color = PrimeTextMuted)
                    }
                }
            )
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = Color(0xFF16101F),
            title = {
                Text(text = "اختر لغة التطبيق 🌐", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    val langs = listOf(
                        "auto" to "🌐 لغة الجهاز الافتراضية (تلقائي)",
                        "ar" to "🇸🇦 العربية (Arabic)",
                        "en" to "🇬🇧 English",
                        "fr" to "🇫🇷 Français (French)",
                        "es" to "🇪🇸 Español (Spanish)",
                        "de" to "🇩🇪 Deutsch (German)",
                        "tr" to "🇹🇷 Türkçe (Turkish)",
                        "ru" to "🇷🇺 Русский (Russian)"
                    )
                    for ((code, label) in langs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSetLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = if (currentLanguage == code) NeonPurplePrimary else Color.White, fontWeight = if (currentLanguage == code) FontWeight.Bold else FontWeight.Normal)
                            if (currentLanguage == code) {
                                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = NeonPurplePrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLanguageDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                ) {
                    Text("إغلاق", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SettingsOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = PrimeTextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonPurplePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = PrimeTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
