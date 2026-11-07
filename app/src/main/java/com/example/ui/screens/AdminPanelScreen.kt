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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun AdminPanelScreen(
    adminEmail: String,
    apps: List<AppItem>,
    onSaveApp: (
        id: String?,
        name: String,
        category: AppCategory,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>
    ) -> Unit,
    onDeleteApp: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingAppId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.GAMES) }
    var ratingStr by remember { mutableStateOf("4.5") }
    var sizeStr by remember { mutableStateOf("120 MB") }
    var description by remember { mutableStateOf("") }
    var featuresStr by remember { mutableStateOf("") }
    var iconUrl by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }
    var screenshotsStr by remember { mutableStateOf("") }
    var appToDelete by remember { mutableStateOf<AppItem?>(null) }

    fun populateForEdit(app: AppItem) {
        editingAppId = app.id
        name = app.nameAr
        selectedCategory = app.category
        ratingStr = app.rating.toString()
        sizeStr = app.size
        description = app.descriptionAr
        featuresStr = app.featuresAr.joinToString("\n")
        iconUrl = app.iconEmoji
        fileUrl = ""
        screenshotsStr = ""
    }

    fun clearForm() {
        editingAppId = null
        name = ""
        selectedCategory = AppCategory.GAMES
        ratingStr = "4.5"
        sizeStr = "120 MB"
        description = ""
        featuresStr = ""
        iconUrl = ""
        fileUrl = ""
        screenshotsStr = ""
    }

    val categoriesList = listOf(
        AppCategory.GAMES,
        AppCategory.APPS,
        AppCategory.POPULAR,
        AppCategory.ENTERTAINMENT,
        AppCategory.SPORTS,
        AppCategory.BUSINESS,
        AppCategory.EDUCATION,
        AppCategory.MUSIC,
        AppCategory.VIDEO,
        AppCategory.TOOLS
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PrimeBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "لوحة تحكم الأدمن 🛠️",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = adminEmail,
                    color = NeonPurplePrimary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.size(48.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 40.dp)
        ) {
            // Form Card
            item {
                NeonCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingAppId != null) "تعديل التطبيق ✏️" else "إضافة تطبيق جديد ➕",
                                color = NeonPurplePrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (editingAppId != null) {
                                Text(
                                    text = "إلغاء التعديل",
                                    color = AccentRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { clearForm() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // App Name
                        AdminTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "اسم التطبيق"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category selector
                        Text(
                            text = "القسم (التصنيف):",
                            color = PrimeTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categoriesList) { cat ->
                                val isSelected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) NeonPurplePrimary else Color(0xFF221538),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonBorderGlow else NeonBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat.titleAr,
                                        color = if (isSelected) Color.White else PrimeTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Rating & Size
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AdminTextField(
                                value = ratingStr,
                                onValueChange = { ratingStr = it },
                                label = "التقييم (مثال: 4.5)",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AdminTextField(
                                value = sizeStr,
                                onValueChange = { sizeStr = it },
                                label = "الحجم (مثال: 120 MB)",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Description
                        AdminTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = "الوصف الكامل للتطبيق",
                            singleLine = false,
                            minLines = 3
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Features
                        AdminTextField(
                            value = featuresStr,
                            onValueChange = { featuresStr = it },
                            label = "المميزات (سطر لكل ميزة)",
                            singleLine = false,
                            minLines = 3
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Icon URL or Emoji
                        AdminTextField(
                            value = iconUrl,
                            onValueChange = { iconUrl = it },
                            label = "أيقونة التطبيق (رابط صورة أو Emoji مثل 🎮)"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // File URL / APK upload
                        AdminTextField(
                            value = fileUrl,
                            onValueChange = { fileUrl = it },
                            label = "رابط ملف APK المباشر للتنزيل"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Screenshots URLs
                        AdminTextField(
                            value = screenshotsStr,
                            onValueChange = { screenshotsStr = it },
                            label = "لقطات الشاشة (سطر لكل رابط)"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Button
                        NeonGradientButton(
                            text = if (editingAppId != null) "تحديث التطبيق في المتجر ✔" else "حفظ التطبيق في المتجر 💾",
                            icon = Icons.Filled.Add,
                            onClick = {
                                if (name.isBlank()) return@NeonGradientButton
                                val rating = ratingStr.toFloatOrNull() ?: 4.5f
                                val features = featuresStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                val screenshots = screenshotsStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                onSaveApp(
                                    editingAppId,
                                    name,
                                    selectedCategory,
                                    rating,
                                    sizeStr,
                                    description,
                                    features,
                                    iconUrl,
                                    fileUrl,
                                    screenshots
                                )
                                clearForm()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Header for App List
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التطبيقات المسجلة في المتجر (${apps.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Apps List
            items(apps) { app ->
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF281845), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = app.iconEmoji, fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = app.nameAr,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${app.category.titleAr} • ⭐ ${app.rating} • ${app.size}",
                                    color = PrimeTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { populateForEdit(app) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "تعديل",
                                    tint = NeonPurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { appToDelete = app }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "حذف",
                                    tint = AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (appToDelete != null) {
        AlertDialog(
            onDismissRequest = { appToDelete = null },
            containerColor = Color(0xFF16101F),
            title = {
                Text(text = "تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف تطبيق \"${appToDelete?.nameAr}\" من المتجر نهائياً؟",
                    color = PrimeTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appToDelete?.let { onDeleteApp(it.id) }
                        appToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { appToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281C38))
                ) {
                    Text("إلغاء", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = NeonBorderGlow,
            unfocusedBorderColor = NeonBorder,
            focusedLabelColor = NeonPurplePrimary,
            unfocusedLabelColor = PrimeTextMuted,
            focusedContainerColor = Color(0xFF140D24),
            unfocusedContainerColor = Color(0xFF140D24)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}
