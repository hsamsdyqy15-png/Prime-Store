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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.UserProfile
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun SettingsScreen(
    currentLanguage: String,
    isDarkMode: Boolean,
    notificationsEnabled: Boolean,
    cacheSize: String,
    userProfile: UserProfile,
    isAdmin: Boolean = false,
    onToggleLanguage: () -> Unit,
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

    // Secret 7-tap counter
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    // Admin login form state
    var adminEmailInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }

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
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
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
                                text = if (isAdmin) "👑" else userProfile.name.firstOrNull()?.toString() ?: "U",
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAdmin) "مدير النظام (Admin) 🛠️" else userProfile.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAdmin) "zaim9002@gmail.com" else userProfile.email,
                                color = if (isAdmin) NeonPurplePrimary else PrimeTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isAdmin || userProfile.isPrimeMember) {
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

            // 1. اللغة (Language) 🌐
            item {
                SettingsOptionItem(
                    title = "اللغة",
                    subtitle = if (currentLanguage == "ar") "العربية" else "English",
                    icon = Icons.Filled.Language,
                    onClick = onToggleLanguage
                )
            }

            // 2. المظهر (Theme) 🌙
            item {
                SettingsOptionItem(
                    title = "المظهر",
                    subtitle = if (isDarkMode) "الوضع الداكن" else "الوضع الفاتح",
                    icon = Icons.Filled.DarkMode,
                    onClick = onToggleDarkMode
                )
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
                    subtitle = "الإصدار 2.5.0 (Prime Store)",
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
        }

        // Bottom Action Button: "تسجيل الخروج" (Logout / Switch Account)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            NeonGradientButton(
                text = "تسجيل الخروج",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                testTag = "logout_button",
                modifier = Modifier.fillMaxWidth(),
                onClick = onLogoutClick
            )
        }

        Spacer(modifier = Modifier.height(70.dp))

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
                                text = "الإصدار: v2.5.0 • أحدث تحديث 2026\nجميع الحقوق محفوظة © Prime Store Team",
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
