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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.github.GitHubStorageHelper
import com.example.data.github.GitHubUploadService
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.StoreCategories
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.NeonCard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
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
        screenshots: List<String>,
        isOpenSource: Boolean,
        sourceCodeUrl: String,
        supportedAbis: String,
        version: String,
        categoryKey: String
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
    var selectedCategoryItem by remember { mutableStateOf(StoreCategories.find("apps")) }
    var versionStr by remember { mutableStateOf("1.0.0") }
    var ratingStr by remember { mutableStateOf("4.5") }
    var sizeStr by remember { mutableStateOf("120 MB") }
    var description by remember { mutableStateOf("") }
    var featuresStr by remember { mutableStateOf("") }
    var directFileUrl by remember { mutableStateOf("") }

    // Uploaded / Existing URLs
    var storedIconUrl by remember { mutableStateOf("") }
    val storedScreenshotUrls = remember { mutableStateListOf<String>() }

    // Open Source & CPU Architecture states
    var isOpenSource by remember { mutableStateOf(false) }
    var sourceCodeUrl by remember { mutableStateOf("") }
    var detectedAbis by remember { mutableStateOf("Universal (All Architectures)") }

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

    // Category Picker Modal
    var showCategoryModal by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }

    // Store Update Section State
    var storeUpdateVersionCode by remember { mutableStateOf("1") }
    var storeUpdateVersionName by remember { mutableStateOf("1.0.0") }
    var storeUpdateNotes by remember { mutableStateOf("") }
    var storeUpdateApkUrl by remember { mutableStateOf("") }
    var isPublishingStoreUpdate by remember { mutableStateOf(false) }

    // GitHub Settings State (Loaded from EncryptedSharedPreferences)
    var ghOwner by remember { mutableStateOf(GitHubStorageHelper.DEFAULT_OWNER) }
    var ghRepo by remember { mutableStateOf(GitHubStorageHelper.DEFAULT_REPO) }
    var ghToken by remember { mutableStateOf("") }
    var isTestingGhConnection by remember { mutableStateOf(false) }
    var isSavingGhSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cfg = GitHubStorageHelper.loadConfig(context)
        ghOwner = cfg.owner
        ghRepo = cfg.repo
        ghToken = cfg.token

        // Fetch current store update config
        val curUpdate = withContext(Dispatchers.IO) {
            SupabaseClient.fetchStoreUpdate()
        }
        if (curUpdate != null) {
            storeUpdateVersionCode = curUpdate.versionCode.toString()
            storeUpdateVersionName = curUpdate.versionName
            storeUpdateNotes = curUpdate.notes
            storeUpdateApkUrl = curUpdate.apkUrl
        }
    }

    // Upload Progress & Size States
    var uploadUploadedMbText by remember { mutableStateOf("0 MB") }
    var uploadTotalMbText by remember { mutableStateOf("0 MB") }
    var isUploadingToGitHub by remember { mutableStateOf(false) }

    // Alerts & Dialogs
    var showOver2GbAlert by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppItem?>(null) }
    var fullscreenPreviewUrl by remember { mutableStateOf<Any?>(null) }
    var verifiedSavedApp by remember { mutableStateOf<AppItem?>(null) }
    var supabaseErrorResult by remember { mutableStateOf<SupabaseClient.SupabaseMutationResult?>(null) }

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

                val max2GbBytes = 2147483648L // 2 GiB
                if (fSize > max2GbBytes) {
                    showOver2GbAlert = true
                    pickedApkUri = null
                    pickedApkFileName = ""
                    pickedApkSizeBytes = 0L
                    scope.launch {
                        snackbarHostState.showSnackbar("❌ الحد الأقصى 2GB لكل ملف")
                    }
                    return@rememberLauncherForActivityResult
                }

                scope.launch(Dispatchers.IO) {
                    val (extractedName, extractedVer, iconBytes) = extractApkMetadata(context, uri)
                    val abis = com.example.data.installer.ApkAbiDetector.detectAbis(context, uri)
                    withContext(Dispatchers.Main) {
                        detectedAbis = abis
                        if (name.isBlank() && !extractedName.isNullOrBlank()) {
                            name = extractedName
                        }
                        if (!extractedVer.isNullOrBlank()) {
                            versionStr = extractedVer
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
        selectedCategoryItem = StoreCategories.find(app.categoryKey.ifBlank { app.category.name.lowercase() })
        versionStr = app.version.ifBlank { "1.0.0" }
        ratingStr = app.rating.toString()
        sizeStr = app.size
        description = app.descriptionAr
        featuresStr = app.featuresAr.joinToString("\n")
        storedIconUrl = app.iconUrl ?: (if (app.iconEmoji.length <= 4 && !app.iconEmoji.startsWith("http")) app.iconEmoji else "")
        directFileUrl = app.fileUrl.orEmpty()
        isOpenSource = app.isOpenSource
        sourceCodeUrl = app.sourceCodeUrl.orEmpty()
        detectedAbis = app.supportedAbis
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
        selectedCategoryItem = StoreCategories.find("apps")
        versionStr = "1.0.0"
        ratingStr = "4.5"
        sizeStr = "120 MB"
        description = ""
        featuresStr = ""
        storedIconUrl = ""
        directFileUrl = ""
        isOpenSource = false
        sourceCodeUrl = ""
        detectedAbis = "Universal (All Architectures)"
        storedScreenshotUrls.clear()

        pickedIconUri = null
        apkExtractedIconBytes = null
        pickedShotUris.clear()
        pickedApkUri = null
        pickedApkFileName = ""
        pickedApkSizeBytes = 0L
    }

    fun startSaveProcess() {
        if (name.isBlank() || isSaving || isUploading) return

        // 1) Verify that icon will be available
        val hasIconSource = pickedIconUri != null || apkExtractedIconBytes != null || storedIconUrl.isNotBlank()
        if (!hasIconSource) {
            scope.launch {
                snackbarHostState.showSnackbar("❌ يجب توفير أيقونة للتطبيق (رفع صورة من الجهاز أو استخراجها من APK أو كتابة رابط)")
            }
            return
        }

        // 2) Verify that APK or direct link will be available
        val hasApkSource = pickedApkUri != null || directFileUrl.isNotBlank()
        if (!hasApkSource) {
            scope.launch {
                snackbarHostState.showSnackbar("❌ يجب توفير ملف APK أو رابط تحميل مباشر للتطبيق")
            }
            return
        }

        if (directFileUrl.isNotBlank() && !SupabaseClient.isValidDownloadLink(directFileUrl)) {
            scope.launch {
                snackbarHostState.showSnackbar("⚠️ الرابط المباشر يجب أن يكون رابط Supabase Storage أو ينتهي بـ .apk أو من GitHub Releases.")
            }
            return
        }

        var total = 0
        if (pickedIconUri != null || (apkExtractedIconBytes != null && storedIconUrl.isBlank())) total++
        total += pickedShotUris.size
        val hasApkToUpload = pickedApkUri != null
        if (hasApkToUpload) total++

        isSaving = true
        isUploading = total > 0
        uploadTotalSteps = total
        uploadCurrentStep = 0
        uploadProgress = 0f
        uploadUploadedMbText = "0 MB"
        uploadTotalMbText = formatSize(pickedApkSizeBytes)
        isUploadingToGitHub = false

        uploadJob = scope.launch {
            try {
                var finalIconUrl = storedIconUrl

                // 1. Upload Icon
                val iconToken = SupabaseClient.getValidToken(forceRefresh = false) ?: resolvedToken
                if (pickedIconUri != null) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع الأيقونة ($uploadCurrentStep/$uploadTotalSteps)"
                    val ext = getFileExtension(context, pickedIconUri!!, "png")
                    val path = "icons/${UUID.randomUUID()}.$ext"
                    val mime = context.contentResolver.getType(pickedIconUri!!) ?: "image/png"
                    val uploadResult = SupabaseClient.uploadStorageFileDetailed(
                        context = context,
                        uri = pickedIconUri!!,
                        path = path,
                        mimeType = mime,
                        accessToken = iconToken
                    ) { written, tot ->
                        if (tot > 0) uploadProgress = (written.toFloat() / tot).coerceIn(0f, 1f)
                    }
                    if (uploadResult.isSuccess) {
                        finalIconUrl = uploadResult.getOrThrow()
                    } else {
                        val errorText = uploadResult.exceptionOrNull()?.message ?: "خطأ في السيرفر"
                        throw Exception("فشل رفع الأيقونة: $errorText")
                    }
                } else if (apkExtractedIconBytes != null && finalIconUrl.isBlank()) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع الأيقونة المستخرجة ($uploadCurrentStep/$uploadTotalSteps)"
                    val path = "icons/${UUID.randomUUID()}.png"
                    val uploadResult = SupabaseClient.uploadStorageBytesDetailed(
                        bytes = apkExtractedIconBytes!!,
                        path = path,
                        mimeType = "image/png",
                        accessToken = iconToken
                    )
                    if (uploadResult.isSuccess) {
                        finalIconUrl = uploadResult.getOrThrow()
                    } else {
                        val errorText = uploadResult.exceptionOrNull()?.message ?: "خطأ في السيرفر"
                        throw Exception("فشل رفع الأيقونة المستخرجة: $errorText")
                    }
                }

                // 2. Upload Screenshots
                val shotToken = SupabaseClient.getValidToken(forceRefresh = false) ?: resolvedToken
                val finalScreenshots = storedScreenshotUrls.toMutableList()
                for ((idx, sUri) in pickedShotUris.withIndex()) {
                    uploadCurrentStep++
                    uploadStepLabel = "جاري رفع لقطة الشاشة ${idx + 1}/${pickedShotUris.size} ($uploadCurrentStep/$uploadTotalSteps)"
                    val ext = getFileExtension(context, sUri, "jpg")
                    val path = "shots/${UUID.randomUUID()}.$ext"
                    val mime = context.contentResolver.getType(sUri) ?: "image/jpeg"
                    val uploadResult = SupabaseClient.uploadStorageFileDetailed(
                        context = context,
                        uri = sUri,
                        path = path,
                        mimeType = mime,
                        accessToken = shotToken
                    ) { written, tot ->
                        if (tot > 0) uploadProgress = (written.toFloat() / tot).coerceIn(0f, 1f)
                    }
                    if (uploadResult.isSuccess) {
                        finalScreenshots.add(uploadResult.getOrThrow())
                    } else {
                        val errorText = uploadResult.exceptionOrNull()?.message ?: "خطأ في السيرفر"
                        throw Exception("فشل رفع لقطة الشاشة ${idx + 1}: $errorText")
                    }
                }

                // 3. Upload APK to GitHub Releases
                var finalFileUrl = directFileUrl
                var uploadedApkSizeStr = sizeStr

                if (hasApkToUpload) {
                    val apkUri = pickedApkUri!!
                    val max2Gb = 2147483648L
                    if (pickedApkSizeBytes > max2Gb) {
                        throw Exception("حجم الملف يتجاوز 2 جيجابايت")
                    }

                    val currentGhConfig = GitHubStorageHelper.loadConfig(context)
                    if (currentGhConfig.token.isBlank()) {
                        throw Exception("يرجى إدخال مفتاح GitHub Token أولاً في قسم \"إعدادات GitHub\"")
                    }

                    uploadCurrentStep++
                    isUploadingToGitHub = true
                    uploadStepLabel = "جاري إنشاء Release ورفع الملف إلى GitHub ($uploadCurrentStep/$uploadTotalSteps)"
                    uploadProgress = 0f

                    GitHubUploadService.start(context, pickedApkFileName.ifBlank { name })

                    try {
                        val uploadResult = GitHubStorageHelper.uploadAppRelease(
                            context = context,
                            owner = currentGhConfig.owner,
                            repo = currentGhConfig.repo,
                            token = currentGhConfig.token,
                            appId = editingAppId ?: "app_${System.currentTimeMillis()}",
                            appName = name.ifBlank { "App" },
                            uri = apkUri,
                            originalFileName = pickedApkFileName.ifBlank { "app.apk" },
                            fileSizeBytes = pickedApkSizeBytes
                        ) { bytesWritten, totalBytes ->
                            val progressFrac = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                            uploadProgress = progressFrac
                            val upMb = formatSize(bytesWritten)
                            val totMb = formatSize(totalBytes)
                            uploadUploadedMbText = upMb
                            uploadTotalMbText = totMb
                            val percent = (progressFrac * 100).toInt()
                            GitHubUploadService.updateProgress(context, pickedApkFileName, percent, upMb, totMb)
                        }

                        if (uploadResult.isFailure) {
                            throw uploadResult.exceptionOrNull() ?: Exception("فشل رفع الملف إلى GitHub Releases")
                        }

                        val ghAsset = uploadResult.getOrThrow()
                        finalFileUrl = ghAsset.downloadUrl
                        uploadedApkSizeStr = ghAsset.sizeMB

                    } finally {
                        GitHubUploadService.stop(context)
                        isUploadingToGitHub = false
                    }
                }

                if (finalIconUrl.isBlank()) throw Exception("لم يتم توفير رابط أيقونة صحيح")
                if (finalFileUrl.isBlank()) throw Exception("لم يتم توفير ملف APK أو رابط تحميل")

                // 4. Save to apps table
                val rating = ratingStr.toFloatOrNull() ?: 4.5f
                val finalCalculatedSize = if (uploadedApkSizeStr.isNotBlank() && uploadedApkSizeStr != "120 MB") {
                    uploadedApkSizeStr
                } else if (sizeStr.isNotBlank() && sizeStr != "120 MB") {
                    sizeStr
                } else if (pickedApkSizeBytes > 0) {
                    formatSize(pickedApkSizeBytes)
                } else {
                    sizeStr
                }
                val features = featuresStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val categoryKey = selectedCategoryItem.key

                val finalFreshToken = SupabaseClient.getValidToken(forceRefresh = false) ?: resolvedToken

                val mutationResult = if (editingAppId == null) {
                    SupabaseClient.insertApp(
                        accessToken = finalFreshToken,
                        name = name,
                        category = categoryKey,
                        rating = rating,
                        size = finalCalculatedSize,
                        description = description,
                        features = features,
                        iconUrl = finalIconUrl,
                        fileUrl = finalFileUrl,
                        screenshots = finalScreenshots,
                        architecture = detectedAbis,
                        version = versionStr.ifBlank { "1.0.0" },
                        packageName = "com.primestore.${name.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                        downloads = "0"
                    )
                } else {
                    SupabaseClient.updateApp(
                        accessToken = finalFreshToken,
                        id = editingAppId!!,
                        name = name,
                        category = categoryKey,
                        rating = rating,
                        size = finalCalculatedSize,
                        description = description,
                        features = features,
                        iconUrl = finalIconUrl,
                        fileUrl = finalFileUrl,
                        screenshots = finalScreenshots,
                        architecture = detectedAbis,
                        version = versionStr.ifBlank { "1.0.0" },
                        packageName = "com.primestore.${name.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                        downloads = "0"
                    )
                }

                if (!mutationResult.success) {
                    if (SupabaseClient.isTokenExpiredError(mutationResult.httpCode, "${mutationResult.message} ${mutationResult.details}")) {
                        snackbarHostState.showSnackbar("انتهت الجلسة، سجّل الدخول من جديد")
                        onBackClick()
                    } else {
                        supabaseErrorResult = mutationResult
                    }
                    return@launch
                }

                val matchingEnum = when {
                    categoryKey == "games" || categoryKey.startsWith("game_") -> AppCategory.GAMES
                    categoryKey == "root_tools" -> AppCategory.ROOT_TOOLS
                    categoryKey == "open_source" -> AppCategory.OPEN_SOURCE
                    else -> AppCategory.APPS
                }

                onSaveApp(
                    editingAppId,
                    name,
                    matchingEnum,
                    rating,
                    finalCalculatedSize,
                    description,
                    features,
                    finalIconUrl,
                    finalFileUrl,
                    finalScreenshots,
                    isOpenSource,
                    sourceCodeUrl,
                    detectedAbis,
                    versionStr.ifBlank { "1.0.0" },
                    categoryKey
                )

                // 5. Post-save verification
                var verifiedApp: AppItem? = null
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(800)
                    val allApps = SupabaseClient.fetchApps()
                    verifiedApp = allApps.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
                }

                if (verifiedApp != null) {
                    verifiedSavedApp = verifiedApp
                    snackbarHostState.showSnackbar("✔ تم حفظ التطبيق وتأكيد الروابط في السحابة بنجاح!")
                } else {
                    snackbarHostState.showSnackbar(if (editingAppId != null) "تم إرسال التحديث بنجاح! ✔" else "تم إرسال التطبيق بنجاح! 🎉")
                }

                clearForm()

            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (SupabaseClient.isTokenExpiredError(0, msg)) {
                    snackbarHostState.showSnackbar("انتهت الجلسة، سجّل الدخول من جديد")
                    onBackClick()
                } else {
                    snackbarHostState.showSnackbar("❌ فشل الحفظ: ${e.message ?: "حدث خطأ غير متوقع"}")
                }
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
                // ========================================================
                // 1) GITHUB SETTINGS CARD
                // ========================================================
                item {
                    NeonCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        hasGlow = false
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🐙 ", fontSize = 16.sp)
                                    Text(
                                        text = "إعدادات GitHub (مستودع ملفات APK)",
                                        color = NeonPurplePrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "🔒 مشفر محلياً",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "يتم رفع ملفات الـ APK تلقائياً إلى GitHub Releases بدعم حتى 2GB للملف الواحد.",
                                color = PrimeTextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AdminTextField(
                                    value = ghOwner,
                                    onValueChange = { ghOwner = it },
                                    label = "اسم المستخدم (owner)",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AdminTextField(
                                    value = ghRepo,
                                    onValueChange = { ghRepo = it },
                                    label = "اسم المستودع (repo)",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            AdminTextField(
                                value = ghToken,
                                onValueChange = { ghToken = it },
                                label = "مفتاح GitHub الشخصي (Personal Access Token)",
                                keyboardType = KeyboardType.Password,
                                visualTransformation = PasswordVisualTransformation()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimeGradientBrush)
                                        .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isSavingGhSettings) {
                                            isSavingGhSettings = true
                                            GitHubStorageHelper.saveConfig(context, ghOwner, ghRepo, ghToken)
                                            isSavingGhSettings = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("✔ تم حفظ إعدادات GitHub بأمان")
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "💾 حفظ الإعدادات",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF281C38))
                                        .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isTestingGhConnection) {
                                            isTestingGhConnection = true
                                            scope.launch {
                                                val testRes = GitHubStorageHelper.testConnection(ghOwner, ghRepo, ghToken)
                                                isTestingGhConnection = false
                                                if (testRes.isSuccess) {
                                                    snackbarHostState.showSnackbar(testRes.getOrNull() ?: "تم الاتصال بنجاح!")
                                                } else {
                                                    snackbarHostState.showSnackbar("❌ فشل الاتصال: ${testRes.exceptionOrNull()?.message}")
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isTestingGhConnection) "جاري الفحص..." else "⚡ اختبار الاتصال",
                                        color = if (isTestingGhConnection) PrimeTextMuted else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ========================================================
                // 2) STORE UPDATE SECTION (تحديث المتجر store_update)
                // ========================================================
                item {
                    NeonCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        hasGlow = false
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = null,
                                    tint = NeonPurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تحديث المتجر (Store Update)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "نشر تحديث جديد لمتجر Prime Store ليظهر تلقائياً للمستخدمين في شاشة الإعدادات مع التثبيت المباشر.",
                                color = PrimeTextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AdminTextField(
                                    value = storeUpdateVersionCode,
                                    onValueChange = { storeUpdateVersionCode = it },
                                    label = "كود الإصدار (version_code رقمي)",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AdminTextField(
                                    value = storeUpdateVersionName,
                                    onValueChange = { storeUpdateVersionName = it },
                                    label = "اسم الإصدار (مثال: 1.0.2)",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = storeUpdateNotes,
                                onValueChange = { storeUpdateNotes = it },
                                label = "ملاحظات التحديث (سجل التغييرات)",
                                singleLine = false,
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = storeUpdateApkUrl,
                                onValueChange = { storeUpdateApkUrl = it },
                                label = "رابط ملف APK المباشر للتحديث (أو رابط GitHub Release)"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimeGradientBrush)
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isPublishingStoreUpdate) {
                                        val code = storeUpdateVersionCode.toIntOrNull() ?: 1
                                        isPublishingStoreUpdate = true
                                        scope.launch {
                                            val success = withContext(Dispatchers.IO) {
                                                SupabaseClient.updateStoreUpdate(
                                                    versionCode = code,
                                                    versionName = storeUpdateVersionName.ifBlank { "1.0.0" },
                                                    notes = storeUpdateNotes,
                                                    apkUrl = storeUpdateApkUrl
                                                )
                                            }
                                            isPublishingStoreUpdate = false
                                            if (success.success) {
                                                snackbarHostState.showSnackbar("✔ تم نشر تحديث المتجر بنجاح في جدول store_update!")
                                            } else {
                                                snackbarHostState.showSnackbar("❌ فشل تحديث جدول store_update، تحقق من الاتصال")
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPublishingStoreUpdate) "جاري النشر..." else "🚀 نشر التحديث للمتجر",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // ========================================================
                // 3) APP ADD/EDIT FORM
                // ========================================================
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

                            // Category Selector with full Modal
                            Text(
                                text = "القسم (التصنيف):",
                                color = PrimeTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF221538))
                                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(12.dp))
                                    .clickable { showCategoryModal = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = selectedCategoryItem.emoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = selectedCategoryItem.titleAr,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${selectedCategoryItem.group} • ${selectedCategoryItem.titleEn}",
                                            color = PrimeTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "تغيير القسم ›",
                                    color = NeonPurplePrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Version, Rating & Size
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AdminTextField(
                                    value = versionStr,
                                    onValueChange = { versionStr = it },
                                    label = "رقم الإصدار (مثال: 1.0.0)",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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

                            // Open Source Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E142D), RoundedCornerShape(12.dp))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "مشروع مفتوح المصدر (Open Source FOSS)؟",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "تفعيل عرض شارة المصدر المفتوح ورابط الكود",
                                        color = PrimeTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = isOpenSource,
                                    onCheckedChange = { isOpenSource = it },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NeonPurplePrimary
                                    )
                                )
                            }

                            AnimatedVisibility(visible = isOpenSource) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AdminTextField(
                                        value = sourceCodeUrl,
                                        onValueChange = { sourceCodeUrl = it },
                                        label = "رابط الكود المصدر (GitHub / GitLab URL)"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // SECTION 1: Icon Picker
                            Text(
                                text = "أيقونة التطبيق:",
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
                                                apkExtractedIconBytes != null -> "أيقونة مستخرجة من APK"
                                                else -> "الأيقونة الحالية للتطبيق"
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
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
                                AdminTextField(
                                    value = storedIconUrl,
                                    onValueChange = { storedIconUrl = it },
                                    label = "أو أدخل Emoji كأيقونة (مثل 🎮)"
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // SECTION 2: Screenshots
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
                                    itemsIndexed(storedScreenshotUrls) { index, shotUrl ->
                                        ScreenshotPreviewThumb(
                                            model = shotUrl,
                                            onRemove = { storedScreenshotUrls.removeAt(index) },
                                            onClick = { fullscreenPreviewUrl = shotUrl }
                                        )
                                    }
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

                            // SECTION 3: APK File Picker
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
                            }

                            AdminTextField(
                                value = directFileUrl,
                                onValueChange = { directFileUrl = it },
                                label = "أو أدخل رابط ملف APK المباشر"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = detectedAbis,
                                onValueChange = { detectedAbis = it },
                                label = "المعالجات المدعومة (CPU Architecture) - فحص تلقائي من APK"
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // UPLOAD PROGRESS
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
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isUploadingToGitHub && uploadTotalMbText.isNotBlank()) {
                                                "$uploadUploadedMbText / $uploadTotalMbText (${(uploadProgress * 100).toInt()}%)"
                                            } else {
                                                "${(uploadProgress * 100).toInt()}%"
                                            },
                                            color = NeonPurplePrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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

                            // SUBMIT BUTTON
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

                // ========================================================
                // 4) APPS LIST
                // ========================================================
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "التطبيقات المسجلة في المتجر (${apps.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

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
                                        text = "${app.displayCategoryTitle} • ${app.displayVersionBadge} • ⭐ ${app.rating} • ${app.size}",
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

    // =========================================================================
    // CATEGORY PICKER FULLSCREEN MODAL (Searchable & Grouped by عام, تطبيقات, ألعاب)
    // =========================================================================
    if (showCategoryModal) {
        Dialog(onDismissRequest = { showCategoryModal = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF16101F))
                    .border(1.dp, NeonBorderGlow, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختر قسم التطبيق",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showCategoryModal = false }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search field
                    OutlinedTextField(
                        value = categorySearchQuery,
                        onValueChange = { categorySearchQuery = it },
                        placeholder = { Text("بحث عن قسم...", fontSize = 12.sp, color = PrimeTextMuted) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NeonPurplePrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonPurplePrimary,
                            unfocusedBorderColor = NeonBorder,
                            focusedContainerColor = Color(0xFF1E142D),
                            unfocusedContainerColor = Color(0xFF1E142D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val q = categorySearchQuery.trim().lowercase()
                    val filteredCategories = remember(q) {
                        if (q.isEmpty()) {
                            StoreCategories.ALL_SELECTIONS
                        } else {
                            StoreCategories.ALL_SELECTIONS.filter {
                                it.titleAr.contains(q) || it.titleEn.lowercase().contains(q) || it.key.lowercase().contains(q)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val grouped = filteredCategories.groupBy { it.group }
                        for ((groupName, items) in grouped) {
                            item {
                                Text(
                                    text = groupName,
                                    color = NeonPurplePrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(items) { cat ->
                                val isSelected = selectedCategoryItem.key.lowercase() == cat.key.lowercase()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFF2E1747) else Color(0xFF1E142D))
                                        .border(1.dp, if (isSelected) NeonBorderGlow else NeonBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCategoryItem = cat
                                            showCategoryModal = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = cat.emoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = cat.titleAr,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = cat.titleEn,
                                                color = PrimeTextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "تم الاختيار",
                                            tint = NeonPurplePrimary,
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
    }

    // Alerts
    if (showOver2GbAlert) {
        AlertDialog(
            onDismissRequest = { showOver2GbAlert = false },
            containerColor = Color(0xFF1E142D),
            title = {
                Text(text = "تنبيه حجم الملف ⚠️", color = AccentGold, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "الحد الأقصى 2GB لكل ملف. يرجى اختيار ملف أصغر حجماً.", color = Color.White, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = { showOver2GbAlert = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                ) {
                    Text("حسناً", color = Color.White)
                }
            }
        )
    }

    if (fullscreenPreviewUrl != null) {
        Dialog(onDismissRequest = { fullscreenPreviewUrl = null }) {
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
                        Text(text = "معاينة لقطة الشاشة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { fullscreenPreviewUrl = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
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

    if (verifiedSavedApp != null) {
        val app = verifiedSavedApp!!
        AlertDialog(
            onDismissRequest = { verifiedSavedApp = null },
            containerColor = Color(0xFF1E142D),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎉 ", fontSize = 20.sp)
                    Text(
                        text = "تم تأكيد الحفظ في Supabase",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "التطبيق: ${app.nameAr}", color = NeonPurplePrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "الإصدار: ${app.displayVersionBadge}", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "القسم: ${app.displayCategoryTitle}", color = Color.White, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { verifiedSavedApp = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
                ) {
                    Text("تم، رائع!", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (supabaseErrorResult != null) {
        val err = supabaseErrorResult!!
        AlertDialog(
            onDismissRequest = { supabaseErrorResult = null },
            containerColor = Color(0xFF1E112E),
            title = {
                Text(
                    text = "⚠️ خطأ في حفظ التطبيق (${err.code})",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "تفاصيل الخطأ من Supabase:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "الرسالة (message):\n${err.message.ifBlank { "غير محددة" }}",
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp
                    )
                    if (err.details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "التفاصيل (details):\n${err.details}",
                            color = PrimeTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { supabaseErrorResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("إغلاق", color = Color.White, fontWeight = FontWeight.Bold)
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
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
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
    } catch (_: Exception) {}
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
                } catch (_: Exception) {}
            }
        }
        tempFile.delete()
        Triple(appName, versionName, iconBytes)
    } catch (_: Exception) {
        Triple(null, null, null)
    }
}
