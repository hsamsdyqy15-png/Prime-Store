package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

@Composable
fun AdminPanelScreen(
    adminEmail: String,
    apps: List<AppItem>,
    accessToken: String? = null,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val resolvedToken = accessToken ?: remember(context) {
        context.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)
            .getString("ps_token", null)
    }

    var editingAppId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.GAMES) }
    var ratingStr by remember { mutableStateOf("4.5") }
    var sizeStr by remember { mutableStateOf("120 MB") }
    var description by remember { mutableStateOf("") }
    var featuresStr by remember { mutableStateOf("") }
    var directFileUrl by remember { mutableStateOf("") }

    // Uploaded / Existing URLs
    var storedIconUrl by remember { mutableStateOf("") }
    val storedScreenshotUrls = remember { mutableStateListOf<String>() }

    // Picked local files
    var pickedIconUri by remember { mutableStateOf<Uri?>(null) }
    var apkExtractedIconBytes by remember { mutableStateOf<ByteArray?>(null) }
    val pickedShotUris = remember { mutableStateListOf<Uri>() }
    var pickedApkUri by remember { mutableStateOf<Uri?>(null) }
    var pickedApkFileName by remember { mutableStateOf("") }
    var pickedApkSizeBytes by remember { mutableLongStateOf(0L) }

    // Upload Progress States
    var isUploading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var uploadStepLabel by remember { mutableStateOf("") }
    var uploadCurrentStep by remember { mutableIntStateOf(0) }
    var uploadTotalSteps by remember { mutableIntStateOf(0) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }

    // Alerts and full previews
    var showOver50MbAlert by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppItem?>(null) }
    var fullscreenPreviewUrl by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(adminEmail) {
        if (!SupabaseClient.isAdmin(adminEmail)) {
            onBackClick()
        }
    }

    // 1) File Pickers
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedIconUri = uri
            apkExtractedIconBytes = null
        }
    }

    val shotsPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            pickedShotUris.addAll(uris)
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (fName, fSize) = getFileNameAndSize(context, uri)
            if (!fName.endsWith(".apk", ignoreCase = true)) {
                scope.launch {
                    snackbarHostState.showSnackbar("اختر ملف APK فقط")
                }
            } else {
                pickedApkUri = uri
                pickedApkFileName = fName
                pickedApkSizeBytes = fSize

                val sizeInMB = fSize / (1024 * 1024)
                if (sizeInMB > 50) {
                    showOver50MbAlert = true
                }

                // Automatic APK archive reading
                scope.launch(Dispatchers.IO) {
                    val (extractedName, _, iconBytes) = extractApkMetadata(context, uri)
                    withContext(Dispatchers.Main) {
                        if (name.isBlank() && !extractedName.isNullOrBlank()) {
                            name = extractedName
                        }
                        if (sizeStr.isBlank() || sizeStr == "120 MB") {
                            sizeStr = formatSize(fSize)
                        }
                        if (iconBytes != null && pickedIconUri == null && storedIconUrl.isBlank()) {
                            apkExtractedIconBytes = iconBytes
                        }
                    }
                }
            }
        }
    }

    fun populateForEdit(app: AppItem) {
        editingAppId = app.id
        name = app.nameAr
        selectedCategory = app.category
        ratingStr = app.rating.toString()
        sizeStr = app.size
        description = app.descriptionAr
        featuresStr = app.featuresAr.joinToString("\n")
        storedIconUrl = app.iconUrl ?: (if (app.iconEmoji.length <= 4 && !app.iconEmoji.startsWith("http")) app.iconEmoji else "")
        directFileUrl = app.fileUrl.orEmpty()
        storedScreenshotUrls.clear()
        storedScreenshotUrls.addAll(app.screenshotUrls)

        pickedIconUri = null
        apkExtractedIconBytes = null
        pickedShotUris.clear()
        pickedApkUri = null
        pickedApkFileName = ""
        pickedApkSizeBytes = 0L
    }

    fun clearForm() {
        editingAppId = null
        name = ""
        selectedCategory = AppCategory.GAMES
        ratingStr = "4.5"
        sizeStr = "120 MB"
        description = ""
        featuresStr = ""
        storedIconUrl = ""
        directFileUrl = ""
        storedScreenshotUrls.clear()

        pickedIconUri = null
        apkExtractedIconBytes = null
        pickedShotUris.clear()
        pickedApkUri = null
        pickedApkFileName = ""
        pickedApkSizeBytes = 0L
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

    fun startSaveProcess() {
        if (name.isBlank() || isSaving || isUploading) return

        // Calculate total files to upload
        var total = 0
        if (pickedIconUri != null || apkExtractedIconBytes != null) total++
        total += pickedShotUris.size
        val canUploadApk = pickedApkUri != null && (pickedApkSizeBytes / (1024 * 1024) <= 50)
        if (canUploadApk) total++

        isSaving = true

        if (total == 0) {
            // No uploads required, save directly
            val rating = ratingStr.toFloatOrNull() ?: 4.5f
            val features = featuresStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            val finalIcon = storedIconUrl.ifBlank { "📱" }
            val finalSize = if (sizeStr.isNotBlank()) sizeStr else "25 MB"
            onSaveApp(
                editingAppId,
                name,
                selectedCategory,
                rating,
                finalSize,
                description,
                features,
                finalIcon,
                directFileUrl,
                storedScreenshotUrls.toList()
            )
            scope.launch {
                snackbarHostState.showSnackbar(if (editingAppId != null) "تم تحديث التطبيق بنجاح! ✔" else "تم حفظ التطبيق في المتجر! 🎉")
            }
            clearForm()
            isSaving = false
            return
        }

        // Upload files with progress
        isUploading = true
        uploadTotalSteps = total
        uploadCurrentStep = 0
        uploadProgress = 0f

        uploadJob = scope.launch {
            try {
                var finalIconUrl = storedIconUrl

                // 1. Upload Icon
                if (pickedIconUri != null) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع الأيقونة ($uploadCurrentStep/$uploadTotalSteps)"
                    val ext = getFileExtension(context, pickedIconUri!!, "png")
                    val path = "icons/${UUID.randomUUID()}.$ext"
                    val mime = context.contentResolver.getType(pickedIconUri!!) ?: "image/png"
                    val res = SupabaseClient.uploadStorageFile(
                        context = context,
                        uri = pickedIconUri!!,
                        path = path,
                        mimeType = mime,
                        accessToken = resolvedToken
                    ) { written, tot ->
                        if (tot > 0) uploadProgress = (written.toFloat() / tot).coerceIn(0f, 1f)
                    }
                    if (res != null) {
                        finalIconUrl = res
                    } else {
                        throw Exception("تعذر رفع الأيقونة إلى السحابة")
                    }
                } else if (apkExtractedIconBytes != null && finalIconUrl.isBlank()) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع الأيقونة المستخرجة ($uploadCurrentStep/$uploadTotalSteps)"
                    val path = "icons/${UUID.randomUUID()}.png"
                    val res = SupabaseClient.uploadStorageBytes(
                        bytes = apkExtractedIconBytes!!,
                        path = path,
                        mimeType = "image/png",
                        accessToken = resolvedToken
                    )
                    if (res != null) {
                        finalIconUrl = res
                    }
                }

                // 2. Upload Screenshots
                val finalScreenshots = storedScreenshotUrls.toMutableList()
                for ((idx, sUri) in pickedShotUris.withIndex()) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع لقطة الشاشة ${idx + 1}/${pickedShotUris.size} ($uploadCurrentStep/$uploadTotalSteps)"
                    val ext = getFileExtension(context, sUri, "jpg")
                    val path = "shots/${UUID.randomUUID()}.$ext"
                    val mime = context.contentResolver.getType(sUri) ?: "image/jpeg"
                    val res = SupabaseClient.uploadStorageFile(
                        context = context,
                        uri = sUri,
                        path = path,
                        mimeType = mime,
                        accessToken = resolvedToken
                    ) { written, tot ->
                        if (tot > 0) uploadProgress = (written.toFloat() / tot).coerceIn(0f, 1f)
                    }
                    if (res != null) {
                        finalScreenshots.add(res)
                    } else {
                        throw Exception("تعذر رفع إحدى لقطات الشاشة")
                    }
                }

                // 3. Upload APK
                var finalFileUrl = directFileUrl
                if (canUploadApk) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع ملف التطبيق APK ($uploadCurrentStep/$uploadTotalSteps)"
                    val path = "files/${UUID.randomUUID()}.apk"
                    val res = SupabaseClient.uploadStorageFile(
                        context = context,
                        uri = pickedApkUri!!,
                        path = path,
                        mimeType = "application/vnd.android.package-archive",
                        accessToken = resolvedToken
                    ) { written, tot ->
                        if (tot > 0) uploadProgress = (written.toFloat() / tot).coerceIn(0f, 1f)
                    }
                    if (res != null) {
                        finalFileUrl = res
                    } else {
                        throw Exception("تعذر رفع ملف APK إلى السحابة")
                    }
                }

                // 4. Save to apps table
                val rating = ratingStr.toFloatOrNull() ?: 4.5f
                val finalCalculatedSize = if (sizeStr.isNotBlank() && sizeStr != "120 MB") {
                    sizeStr
                } else if (pickedApkSizeBytes > 0) {
                    formatSize(pickedApkSizeBytes)
                } else {
                    sizeStr
                }
                val features = featuresStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }

                onSaveApp(
                    editingAppId,
                    name,
                    selectedCategory,
                    rating,
                    finalCalculatedSize,
                    description,
                    features,
                    finalIconUrl.ifBlank { "📱" },
                    finalFileUrl,
                    finalScreenshots
                )

                snackbarHostState.showSnackbar(if (editingAppId != null) "تم تحديث التطبيق بنجاح! ✔" else "تم رفع وحفظ التطبيق في المتجر! 🎉")
                clearForm()

            } catch (e: Exception) {
                snackbarHostState.showSnackbar("فشل الرفع أو الحفظ: ${e.message ?: "خطأ غير متوقع"}")
            } finally {
                isUploading = false
                isSaving = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = PrimeBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    NeonCard(modifier = Modifier.fillMaxWidth()) {
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

                            Spacer(modifier = Modifier.height(14.dp))

                            // ========================================================
                            // SECTION 1: رفع الأيقونة (Icon Picker & Preview)
                            // ========================================================
                            Text(
                                text = "أيقونة التطبيق:",
                                color = PrimeTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Button to pick icon from device
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimeGradientBrush)
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable { iconPicker.launch("image/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📷 رفع الأيقونة",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Icon Preview Area
                            if (pickedIconUri != null || apkExtractedIconBytes != null || storedIconUrl.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E142D), RoundedCornerShape(14.dp))
                                        .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF281C38))
                                            .border(1.dp, NeonBorderGlow, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pickedIconUri != null) {
                                            AsyncImage(
                                                model = pickedIconUri,
                                                contentDescription = "أيقونة جديدة",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (apkExtractedIconBytes != null) {
                                            AsyncImage(
                                                model = apkExtractedIconBytes,
                                                contentDescription = "أيقونة مستخرجة",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (storedIconUrl.startsWith("http")) {
                                            AsyncImage(
                                                model = storedIconUrl,
                                                contentDescription = "الأيقونة الحالية",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(text = storedIconUrl.ifBlank { "📱" }, fontSize = 28.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                pickedIconUri != null -> "أيقونة محددة من الجهاز"
                                                apkExtractedIconBytes != null -> "أيقونة مستخرجة تلقائياً من APK"
                                                else -> "الأيقونة الحالية للتطبيق"
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (pickedIconUri != null) "جاهزة للرفع إلى السحابة" else "يمكنك استبدالها في أي وقت",
                                            color = PrimeTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            pickedIconUri = null
                                            apkExtractedIconBytes = null
                                            storedIconUrl = ""
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "حذف الأيقونة",
                                            tint = AccentRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                // Fallback emoji / url field if user wants emoji
                                AdminTextField(
                                    value = storedIconUrl,
                                    onValueChange = { storedIconUrl = it },
                                    label = "أو أدخل Emoji كأيقونة (مثل 🎮)"
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ========================================================
                            // SECTION 2: لقطات الشاشة (Screenshots Picker & LazyRow Preview)
                            // ========================================================
                            Text(
                                text = "لقطات الشاشة:",
                                color = PrimeTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimeGradientBrush)
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable { shotsPicker.launch("image/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🖼 رفع لقطات الشاشة (اختيار متعدد)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Horizontal Screenshots Preview
                            val hasShots = storedScreenshotUrls.isNotEmpty() || pickedShotUris.isNotEmpty()
                            if (hasShots) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E142D), RoundedCornerShape(14.dp))
                                        .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
                                        .padding(8.dp)
                                ) {
                                    // 1. Existing stored screenshots
                                    itemsIndexed(storedScreenshotUrls) { index, shotUrl ->
                                        ScreenshotPreviewThumb(
                                            model = shotUrl,
                                            onRemove = { storedScreenshotUrls.removeAt(index) },
                                            onClick = { fullscreenPreviewUrl = shotUrl }
                                        )
                                    }

                                    // 2. Newly picked local Uris
                                    itemsIndexed(pickedShotUris) { index, uri ->
                                        ScreenshotPreviewThumb(
                                            model = uri,
                                            onRemove = { pickedShotUris.removeAt(index) },
                                            onClick = { fullscreenPreviewUrl = uri }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ========================================================
                            // SECTION 3: ملف التطبيق (APK File Picker & Card Preview)
                            // ========================================================
                            Text(
                                text = "ملف التطبيق أو اللعبة (APK):",
                                color = PrimeTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimeGradientBrush)
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable { apkPicker.launch("*/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Android,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📦 رفع التطبيق / اللعبة (APK)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // APK Preview Card
                            if (pickedApkUri != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E142D))
                                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(NeonPurpleDeep, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📦", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pickedApkFileName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatSize(pickedApkSizeBytes),
                                            color = NeonPurplePrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            pickedApkUri = null
                                            pickedApkFileName = ""
                                            pickedApkSizeBytes = 0L
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "إلغاء الملف",
                                            tint = AccentRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            } else if (directFileUrl.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E142D))
                                        .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = "📦", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "الملف المخزن حالياً",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = directFileUrl.substringAfterLast("/"),
                                                color = PrimeTextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PrimeGradientBrush)
                                            .clickable { apkPicker.launch("*/*") }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "استبدال",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Direct URL Fallback (below APK button)
                            AdminTextField(
                                value = directFileUrl,
                                onValueChange = { directFileUrl = it },
                                label = "رابط ملف APK المباشر (بديل أو للملفات الكبيرة > 50MB)"
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ========================================================
                            // UPLOAD PROGRESS SECTION
                            // ========================================================
                            AnimatedVisibility(
                                visible = isUploading,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF221538))
                                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(16.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uploadStepLabel,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${(uploadProgress * 100).toInt()}%",
                                            color = NeonPurplePrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                uploadJob?.cancel()
                                                isUploading = false
                                                isSaving = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("تم إلغاء عملية الرفع")
                                                }
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "إلغاء الرفع",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { uploadProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = NeonPurplePrimary,
                                        trackColor = Color(0xFF3B2456)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // ========================================================
                            // SUBMIT BUTTON (حفظ التطبيق)
                            // ========================================================
                            val canSave = name.isNotBlank() && !isSaving && !isUploading
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (canSave) PrimeGradientBrush else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A2038), Color(0xFF1E172B))))
                                    .border(1.dp, if (canSave) NeonBorderGlow else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable(enabled = canSave) { startSaveProcess() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (editingAppId != null) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = if (canSave) Color.White else PrimeTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isUploading -> "جاري الرفع إلى السحابة... ☁️"
                                            isSaving -> "جاري الحفظ... 💾"
                                            editingAppId != null -> "تحديث التطبيق في المتجر ✔"
                                            else -> "حفظ التطبيق في المتجر 💾"
                                        },
                                        color = if (canSave) Color.White else PrimeTextMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
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
                                    if (!app.iconUrl.isNullOrBlank() && app.iconUrl.startsWith("http")) {
                                        AsyncImage(
                                            model = app.iconUrl,
                                            contentDescription = app.nameAr,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(text = app.iconEmoji, fontSize = 20.sp)
                                    }
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
    }

    // ========================================================
    // MODALS & DIALOGS
    // ========================================================

    // 1. Alert for File Size > 50MB
    if (showOver50MbAlert) {
        AlertDialog(
            onDismissRequest = { showOver50MbAlert = false },
            containerColor = Color(0xFF1E142D),
            title = {
                Text(
                    text = "تنبيه حجم الملف ⚠️",
                    color = AccentGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "الحد الأقصى للرفع المباشر هو 50MB. يرجى استخدام حقل \"الرابط المباشر\" بدلاً منه لتنزيل هذا الملف.",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showOver50MbAlert = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                ) {
                    Text("حسناً", color = Color.White)
                }
            }
        )
    }

    // 2. Fullscreen Screenshot Preview Modal
    if (fullscreenPreviewUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenPreviewUrl = null }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF120C1D))
                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "معاينة لقطة الشاشة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { fullscreenPreviewUrl = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = fullscreenPreviewUrl,
                        contentDescription = "معاينة مكبرة",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    // 3. Delete App Confirmation Dialog
    if (appToDelete != null) {
        AlertDialog(
            onDismissRequest = { appToDelete = null },
            containerColor = Color(0xFF16101F),
            title = {
                Text(text = "تأكيد الحذف 🗑️", color = Color.White, fontWeight = FontWeight.Bold)
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
                        scope.launch {
                            snackbarHostState.showSnackbar("تم حذف التطبيق بنجاح")
                        }
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
private fun ScreenshotPreviewThumb(
    model: Any,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(90.dp, 130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF281C38))
            .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = model,
            contentDescription = "لقطة شاشة",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Delete button on top corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "حذف اللقطة",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
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

// ========================================================
// FILE & APK UTILITIES
// ========================================================

private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "app.apk"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "app.apk"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return Pair(name, size)
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun getFileExtension(context: Context, uri: Uri, defaultExt: String): String {
    val mime = context.contentResolver.getType(uri)
    val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!ext.isNullOrBlank()) return ext
    val (name, _) = getFileNameAndSize(context, uri)
    if (name.contains(".")) {
        return name.substringAfterLast(".").lowercase()
    }
    return defaultExt
}

private fun extractApkMetadata(context: Context, uri: Uri): Triple<String?, String?, ByteArray?> {
    return try {
        val tempFile = File(context.cacheDir, "temp_inspect_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
        var appName: String? = null
        var versionName: String? = null
        var iconBytes: ByteArray? = null

        if (info != null) {
            info.applicationInfo?.sourceDir = tempFile.absolutePath
            info.applicationInfo?.publicSourceDir = tempFile.absolutePath
            appName = info.applicationInfo?.loadLabel(pm)?.toString()
            versionName = info.versionName
            val drawable = info.applicationInfo?.loadIcon(pm)
            if (drawable != null) {
                try {
                    val bmp = if (drawable is android.graphics.drawable.BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(b)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        b
                    }
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    iconBytes = baos.toByteArray()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
        tempFile.delete()
        Triple(appName, versionName, iconBytes)
    } catch (e: Exception) {
        Triple(null, null, null)
    }
}
