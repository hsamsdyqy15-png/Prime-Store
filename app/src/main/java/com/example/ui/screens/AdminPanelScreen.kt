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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.github.AdminPublishCoordinator
import com.example.data.github.AdminPublishDraft
import com.example.data.github.GitHubStorageHelper
import com.example.data.github.GitHubTokenProvider
import com.example.data.installer.ApkAbiDetector
import com.example.data.local.CategoryEntity
import com.example.data.model.AppItem
import com.example.data.model.CategoryItem
import com.example.data.model.StoreCategories
import com.example.data.repository.CategoriesRepository
import com.example.data.repository.SettingsRepository
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.NeonCard
import com.example.ui.components.StoreImageLoader
import com.example.ui.theme.PrimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
fun AdminPanelScreen(
    adminEmail: String,
    apps: List<AppItem>,
    allGameCategories: List<CategoryEntity> = emptyList(),
    allAppCategories: List<CategoryEntity> = emptyList(),
    onAddCategory: (nameEn: String, nameAr: String, emoji: String, type: String) -> Unit = { _, _, _, _ -> },
    onUpdateCategory: (key: String, nameEn: String, nameAr: String, emoji: String) -> Unit = { _, _, _, _ -> },
    onToggleCategoryHidden: (key: String, hidden: Boolean) -> Unit = { _, _ -> },
    onMoveCategory: (key: String, type: String, moveUp: Boolean) -> Unit = { _, _, _ -> },
    onCatalogChanged: () -> Unit,
    onDeleteApp: (String) -> Unit,
    onSessionExpired: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = PrimeTheme.colors
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageLoader = remember(context) { StoreImageLoader.get(context) }

    val isOwner = remember(adminEmail) { SupabaseClient.isOwner(adminEmail) }
    val tokenStatus by GitHubTokenProvider.status.collectAsState()
    val publishState by AdminPublishCoordinator.state.collectAsState()

    val categoriesRepo = remember(context) { CategoriesRepository.getInstance(context) }

    var editingAppId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf(StoreCategories.ITEM_TYPE_APP) }
    var selectedCategoryItem by remember { mutableStateOf(StoreCategories.find("tools", StoreCategories.ITEM_TYPE_APP)) }
    var versionStr by remember { mutableStateOf("1.0.0") }
    var ratingStr by remember { mutableStateOf("4.5") }
    var sizeStr by remember { mutableStateOf("120 MB") }
    var description by remember { mutableStateOf("") }
    var featuresStr by remember { mutableStateOf("") }
    var directFileUrl by remember { mutableStateOf("") }
    var sha256Input by remember { mutableStateOf("") }
    var splitApkUrlsText by remember { mutableStateOf("") }
    var obbUrl by remember { mutableStateOf("") }
    var showAdvancedInstallerFields by remember { mutableStateOf(false) }

    var storedIconUrl by remember { mutableStateOf("") }
    val storedScreenshotUrls = remember { mutableStateListOf<String>() }

    var isOpenSource by remember { mutableStateOf(false) }
    var sourceCodeUrl by remember { mutableStateOf("") }
    var detectedAbis by remember { mutableStateOf(context.getString(R.string.abi_universal_all)) }

    var pickedIconUri by remember { mutableStateOf<Uri?>(null) }
    var apkExtractedIconBytes by remember { mutableStateOf<ByteArray?>(null) }
    val pickedShotUris = remember { mutableStateListOf<Uri>() }
    var pickedApkUri by remember { mutableStateOf<Uri?>(null) }
    var pickedApkFileName by remember { mutableStateOf("") }
    var pickedApkSizeBytes by remember { mutableLongStateOf(0L) }
    var draftInitialized by remember { mutableStateOf(false) }

    var showCategoryModal by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }

    // Category Manager State
    var showCategoryManagerSection by remember { mutableStateOf(false) }
    var catManagerTypeTab by remember { mutableStateOf(StoreCategories.ITEM_TYPE_GAME) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryEntity by remember { mutableStateOf<CategoryEntity?>(null) }
    var catDialogNameEn by remember { mutableStateOf("") }
    var catDialogNameAr by remember { mutableStateOf("") }
    var catDialogEmoji by remember { mutableStateOf("🎮") }

    var storeUpdateVersionCode by remember { mutableStateOf("1") }
    var storeUpdateVersionName by remember { mutableStateOf("1.0.0") }
    var storeUpdateNotes by remember { mutableStateOf("") }
    var storeUpdateApkUrl by remember { mutableStateOf("") }
    var isPublishingStoreUpdate by remember { mutableStateOf(false) }

    // Owner-only GitHub token input state (write-only; never populated with stored secret)
    var ownerTokenInput by remember { mutableStateOf("") }
    var isSavingGhKey by remember { mutableStateOf(false) }
    var isTestingGhKey by remember { mutableStateOf(false) }
    var isDeletingGhKey by remember { mutableStateOf(false) }

    // 5-step Publish Readiness Check state
    var isRunningReadinessCheck by remember { mutableStateOf(false) }
    val readinessSteps = remember {
        mutableStateListOf(
            ReadinessStepResult(R.string.self_check_session),
            ReadinessStepResult(R.string.self_check_admin_list),
            ReadinessStepResult(R.string.self_check_central_key),
            ReadinessStepResult(R.string.self_check_gh_repo),
            ReadinessStepResult(R.string.self_check_apps_write)
        )
    }

    var showOver2GbAlert by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppItem?>(null) }
    var fullscreenPreviewUrl by remember { mutableStateOf<Any?>(null) }
    var verifiedSavedApp by remember { mutableStateOf<AppItem?>(null) }
    var supabaseErrorResult by remember { mutableStateOf<SupabaseClient.SupabaseMutationResult?>(null) }

    fun buildCurrentDraft(): AdminPublishDraft = AdminPublishDraft(
        editingAppId = editingAppId,
        name = name,
        categoryKey = selectedCategoryItem.key,
        versionStr = versionStr,
        ratingStr = ratingStr,
        sizeStr = sizeStr,
        description = description,
        featuresStr = featuresStr,
        directFileUrl = directFileUrl,
        storedIconUrl = storedIconUrl,
        storedScreenshotUrls = storedScreenshotUrls.toList(),
        isOpenSource = isOpenSource,
        sourceCodeUrl = sourceCodeUrl,
        detectedAbis = detectedAbis,
        pickedIconUriStr = pickedIconUri?.toString(),
        pickedShotUriStrs = pickedShotUris.map { it.toString() },
        pickedApkUriStr = pickedApkUri?.toString(),
        pickedApkFileName = pickedApkFileName,
        pickedApkSizeBytes = pickedApkSizeBytes,
        itemType = itemType,
        sha256 = sha256Input,
        splitApkUrlsText = splitApkUrlsText,
        obbUrl = obbUrl
    )

    fun clearForm(clearPersistedDraft: Boolean = true) {
        editingAppId = null
        name = ""
        itemType = StoreCategories.ITEM_TYPE_APP
        selectedCategoryItem = StoreCategories.find("tools", StoreCategories.ITEM_TYPE_APP)
        versionStr = "1.0.0"
        ratingStr = "4.5"
        sizeStr = "120 MB"
        description = ""
        featuresStr = ""
        storedIconUrl = ""
        directFileUrl = ""
        sha256Input = ""
        splitApkUrlsText = ""
        obbUrl = ""
        showAdvancedInstallerFields = false
        isOpenSource = false
        sourceCodeUrl = ""
        detectedAbis = context.getString(R.string.abi_universal_all)
        storedScreenshotUrls.clear()

        pickedIconUri = null
        apkExtractedIconBytes = null
        pickedShotUris.clear()
        pickedApkUri = null
        pickedApkFileName = ""
        pickedApkSizeBytes = 0L
        if (clearPersistedDraft) {
            AdminPublishCoordinator.clearDraft(context)
        }
    }

    LaunchedEffect(adminEmail) {
        if (!SupabaseClient.isAdmin(adminEmail)) {
            onBackClick()
        }
    }

    // Proactive session refresh + metadata status + draft restoration on panel open
    LaunchedEffect(Unit) {
        GitHubStorageHelper.purgeLegacyLocalToken(context)

        val draft = AdminPublishCoordinator.loadDraft(context)
        if (draft != null && !draftInitialized) {
            editingAppId = draft.editingAppId
            name = draft.name
            itemType = StoreCategories.inferItemType(draft.categoryKey, draft.itemType)
            selectedCategoryItem = StoreCategories.find(draft.categoryKey, itemType)
            versionStr = draft.versionStr
            ratingStr = draft.ratingStr
            sizeStr = draft.sizeStr
            description = draft.description
            featuresStr = draft.featuresStr
            directFileUrl = draft.directFileUrl
            sha256Input = draft.sha256
            splitApkUrlsText = draft.splitApkUrlsText
            obbUrl = draft.obbUrl
            showAdvancedInstallerFields = draft.sha256.isNotBlank() || draft.splitApkUrlsText.isNotBlank() || draft.obbUrl.isNotBlank()
            storedIconUrl = draft.storedIconUrl
            storedScreenshotUrls.clear()
            storedScreenshotUrls.addAll(draft.storedScreenshotUrls)
            isOpenSource = draft.isOpenSource
            sourceCodeUrl = draft.sourceCodeUrl
            detectedAbis = draft.detectedAbis
            pickedIconUri = draft.pickedIconUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            pickedShotUris.clear()
            pickedShotUris.addAll(draft.pickedShotUriStrs.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() })
            pickedApkUri = draft.pickedApkUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            pickedApkFileName = draft.pickedApkFileName
            pickedApkSizeBytes = draft.pickedApkSizeBytes
        }
        draftInitialized = true

        val validToken = SupabaseClient.getValidUserToken(forceRefresh = false)
        if (validToken.isNullOrBlank()) {
            onSessionExpired()
            return@LaunchedEffect
        }

        GitHubTokenProvider.refreshStatusMetadata(context)

        val curUpdate = withContext(Dispatchers.IO) {
            SupabaseClient.fetchStoreUpdate()
        }
        if (curUpdate != null) {
            storeUpdateVersionCode = curUpdate.versionCode.toString()
            storeUpdateVersionName = curUpdate.versionName.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) } ?: "1.0.0"
            storeUpdateNotes = curUpdate.notes.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }.orEmpty()
            storeUpdateApkUrl = curUpdate.apkUrl.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }.orEmpty()
        }
    }

    // Persist draft when form edits occur
    LaunchedEffect(
        draftInitialized,
        editingAppId,
        name,
        itemType,
        selectedCategoryItem,
        versionStr,
        ratingStr,
        sizeStr,
        description,
        featuresStr,
        directFileUrl,
        sha256Input,
        splitApkUrlsText,
        obbUrl,
        storedIconUrl,
        storedScreenshotUrls.size,
        isOpenSource,
        sourceCodeUrl,
        detectedAbis,
        pickedIconUri,
        pickedShotUris.size,
        pickedApkUri,
        pickedApkFileName,
        pickedApkSizeBytes
    ) {
        if (!draftInitialized || publishState.isRunning) return@LaunchedEffect
        val hasContent = name.isNotBlank() || description.isNotBlank() || directFileUrl.isNotBlank() ||
            pickedApkUri != null || pickedIconUri != null || storedIconUrl.isNotBlank() ||
            pickedShotUris.isNotEmpty() || storedScreenshotUrls.isNotEmpty() ||
            sha256Input.isNotBlank() || splitApkUrlsText.isNotBlank() || obbUrl.isNotBlank()
        if (hasContent) {
            AdminPublishCoordinator.saveDraft(context, buildCurrentDraft())
        }
    }

    // Observe coordinator completion / session expiry signals
    LaunchedEffect(publishState.completedSuccessMessage, publishState.sessionExpiredDuringPublish, publishState.supabaseError) {
        if (publishState.sessionExpiredDuringPublish) {
            onSessionExpired()
            return@LaunchedEffect
        }
        val err = publishState.supabaseError
        if (err != null) {
            supabaseErrorResult = err
            AdminPublishCoordinator.clearCompletionSignals()
        }
        val msg = publishState.completedSuccessMessage
        if (msg != null) {
            verifiedSavedApp = publishState.verifiedSavedApp
            snackbarHostState.showSnackbar(msg)
            clearForm(clearPersistedDraft = true)
            AdminPublishCoordinator.clearCompletionSignals()
        }
    }

    fun runReadinessDiagnostic() {
        if (isRunningReadinessCheck) return
        scope.launch {
            isRunningReadinessCheck = true
            for (i in readinessSteps.indices) {
                readinessSteps[i] = readinessSteps[i].copy(state = CheckStepState.IDLE, detail = null)
            }

            // 1) Session validity
            readinessSteps[0] = readinessSteps[0].copy(state = CheckStepState.RUNNING)
            val sessionRes = SupabaseClient.checkAuthUserSession()
            if (sessionRes.isSuccess) {
                readinessSteps[0] = readinessSteps[0].copy(state = CheckStepState.PASSED, detail = sessionRes.getOrNull())
            } else {
                val msg = context.getString(R.string.err_session_expired_relogin)
                readinessSteps[0] = readinessSteps[0].copy(state = CheckStepState.FAILED, detail = msg)
                isRunningReadinessCheck = false
                return@launch
            }

            // 2) Admin list check
            readinessSteps[1] = readinessSteps[1].copy(state = CheckStepState.RUNNING)
            if (SupabaseClient.isAdmin(adminEmail)) {
                readinessSteps[1] = readinessSteps[1].copy(state = CheckStepState.PASSED, detail = adminEmail)
            } else {
                readinessSteps[1] = readinessSteps[1].copy(
                    state = CheckStepState.FAILED,
                    detail = context.getString(R.string.toast_admin_not_authorized)
                )
            }

            // 3) Read central GitHub key
            readinessSteps[2] = readinessSteps[2].copy(state = CheckStepState.RUNNING)
            val keyRes = GitHubTokenProvider.getToken(context, adminEmail, forceRefresh = true)
            var fetchedToken: String? = null
            if (keyRes.isSuccess) {
                fetchedToken = keyRes.getOrNull()
                readinessSteps[2] = readinessSteps[2].copy(
                    state = CheckStepState.PASSED,
                    detail = context.getString(R.string.gh_status_enabled_short)
                )
            } else {
                val msg = keyRes.exceptionOrNull()?.message
                    ?: context.getString(R.string.err_gh_token_missing_admin)
                readinessSteps[2] = readinessSteps[2].copy(state = CheckStepState.FAILED, detail = msg)
            }

            // 4) GitHub repository connection
            readinessSteps[3] = readinessSteps[3].copy(state = CheckStepState.RUNNING)
            if (!fetchedToken.isNullOrBlank()) {
                val ghRes = GitHubStorageHelper.testConnection(
                    token = fetchedToken,
                    userEmail = adminEmail,
                    context = context
                )
                if (ghRes.isSuccess) {
                    readinessSteps[3] = readinessSteps[3].copy(
                        state = CheckStepState.PASSED,
                        detail = ghRes.getOrNull()
                    )
                } else {
                    readinessSteps[3] = readinessSteps[3].copy(
                        state = CheckStepState.FAILED,
                        detail = ghRes.exceptionOrNull()?.message
                    )
                }
            } else {
                readinessSteps[3] = readinessSteps[3].copy(
                    state = CheckStepState.FAILED,
                    detail = if (isOwner) context.getString(R.string.err_gh_token_missing_owner)
                    else context.getString(R.string.err_gh_token_missing_admin)
                )
            }

            // 5) Write permission on apps table
            readinessSteps[4] = readinessSteps[4].copy(state = CheckStepState.RUNNING)
            val appsPermRes = SupabaseClient.checkAppsWritePermission()
            if (appsPermRes.success) {
                readinessSteps[4] = readinessSteps[4].copy(state = CheckStepState.PASSED)
            } else {
                val reason = if (SupabaseClient.isRlsPermissionError(appsPermRes.httpCode, appsPermRes.message)) {
                    context.getString(R.string.err_rls_not_admin)
                } else {
                    SecureLogger.sanitize(appsPermRes.message)
                }
                readinessSteps[4] = readinessSteps[4].copy(state = CheckStepState.FAILED, detail = reason)
            }

            isRunningReadinessCheck = false
        }
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedIconUri = uri
            apkExtractedIconBytes = null
            AdminPublishCoordinator.clearError()
        }
    }

    val shotsPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            pickedShotUris.addAll(uris)
            AdminPublishCoordinator.clearError()
        }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (fName, fSize) = getFileNameAndSize(context, uri)
            val isSupportedBundle = listOf(".apk", ".apks", ".xapk", ".zip").any {
                fName.endsWith(it, ignoreCase = true)
            }
            if (!isSupportedBundle) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.github_unsupported_ext, fName))
                }
            } else {
                pickedApkUri = uri
                pickedApkFileName = fName
                pickedApkSizeBytes = fSize
                AdminPublishCoordinator.clearError()

                val max2GbBytes = 2147483648L
                if (fSize > max2GbBytes) {
                    showOver2GbAlert = true
                    pickedApkUri = null
                    pickedApkFileName = ""
                    pickedApkSizeBytes = 0L
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.github_file_over_2gb))
                    }
                    return@rememberLauncherForActivityResult
                }

                if (fName.endsWith(".apk", ignoreCase = true)) {
                    scope.launch(Dispatchers.IO) {
                        val (extractedName, extractedVer, iconBytes) = extractApkMetadata(context, uri)
                        val abis = ApkAbiDetector.detectAbis(context, uri)
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
                } else {
                    if (sizeStr.isBlank() || sizeStr == "120 MB") {
                        sizeStr = formatSize(fSize)
                    }
                }
            }
        }
    }

    fun populateForEdit(app: AppItem) {
        editingAppId = app.id
        name = app.nameAr
        itemType = StoreCategories.inferItemType(app.categoryKey, app.itemType, app.category)
        selectedCategoryItem = StoreCategories.find(app.categoryKey.ifBlank { app.category.name.lowercase() }, itemType)
        versionStr = app.version.ifBlank { "1.0.0" }
        ratingStr = app.rating.toString()
        sizeStr = app.size
        description = app.descriptionAr
        featuresStr = app.featuresAr.joinToString("\n")
        storedIconUrl = app.iconUrl ?: (if (app.iconEmoji.length <= 4 && !app.iconEmoji.startsWith("http")) app.iconEmoji else "")
        directFileUrl = app.fileUrl.orEmpty()
        sha256Input = app.sha256.orEmpty()
        splitApkUrlsText = app.splitApkUrls.joinToString("\n")
        obbUrl = app.obbUrl.orEmpty()
        showAdvancedInstallerFields = sha256Input.isNotBlank() || splitApkUrlsText.isNotBlank() || obbUrl.isNotBlank()
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
        AdminPublishCoordinator.clearError()
    }

    fun startSaveProcess() {
        if (name.isBlank() || publishState.isRunning) return

        val hasIconSource = pickedIconUri != null || apkExtractedIconBytes != null || storedIconUrl.isNotBlank()
        if (!hasIconSource) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.admin_pick_icon_btn))
            }
            return
        }

        val hasApkSource = pickedApkUri != null || directFileUrl.isNotBlank()
        if (!hasApkSource) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_empty_file))
            }
            return
        }

        if (directFileUrl.isNotBlank() && !SupabaseClient.isValidDownloadLink(directFileUrl)) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.toast_invalid_download_link))
            }
            return
        }

        AdminPublishCoordinator.clearError()
        AdminPublishCoordinator.startPublish(
            context = context,
            adminEmail = adminEmail,
            draft = buildCurrentDraft(),
            apkExtractedIconBytes = apkExtractedIconBytes,
            onCatalogSaved = onCatalogChanged
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .testTag("admin_panel_screen")
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("admin_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.textPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.admin_panel_header),
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.admin_logged_in_as, adminEmail),
                        color = colors.accent,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.size(48.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 40.dp)
            ) {
                // 1) CENTRALIZED GITHUB KEY SECTION (OWNER ONLY VS READ-ONLY ADMIN STATUS)
                item {
                    if (isOwner) {
                        OwnerGitHubKeyCard(
                            tokenStatus = tokenStatus,
                            tokenInput = ownerTokenInput,
                            onTokenInputChange = { ownerTokenInput = it },
                            isSaving = isSavingGhKey,
                            isTesting = isTestingGhKey,
                            isDeleting = isDeletingGhKey,
                            onSaveClick = {
                                val raw = ownerTokenInput.trim()
                                if (raw.isBlank()) return@OwnerGitHubKeyCard
                                isSavingGhKey = true
                                scope.launch {
                                    val res = SupabaseClient.upsertGitHubTokenSecret(raw)
                                    isSavingGhKey = false
                                    if (res.success) {
                                        ownerTokenInput = ""
                                        GitHubTokenProvider.markSavedInMemory(context, raw)
                                        GitHubTokenProvider.refreshStatusMetadata(context)
                                        snackbarHostState.showSnackbar(context.getString(R.string.gh_key_saved_success))
                                    } else if (SupabaseClient.isTokenExpiredError(res.httpCode, res.message)) {
                                        onSessionExpired()
                                    } else if (SupabaseClient.isRlsPermissionError(res.httpCode, res.message)) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.err_rls_not_admin))
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            SecureLogger.sanitize(res.message.ifBlank { context.getString(R.string.error_unexpected) })
                                        )
                                    }
                                }
                            },
                            onTestClick = {
                                isTestingGhKey = true
                                scope.launch {
                                    val candidateToken = ownerTokenInput.trim()
                                    val tokenResult = if (candidateToken.isNotBlank()) {
                                        Result.success(candidateToken)
                                    } else {
                                        GitHubTokenProvider.getToken(context, adminEmail, forceRefresh = true)
                                    }
                                    val msg = if (tokenResult.isSuccess) {
                                        val testRes = GitHubStorageHelper.testConnection(
                                            token = tokenResult.getOrThrow(),
                                            userEmail = adminEmail,
                                            context = context
                                        )
                                        testRes.getOrElse { it.message ?: context.getString(R.string.github_network_fail) }
                                    } else {
                                        tokenResult.exceptionOrNull()?.message
                                            ?: context.getString(R.string.err_gh_token_missing_owner)
                                    }
                                    isTestingGhKey = false
                                    snackbarHostState.showSnackbar(msg)
                                }
                            },
                            onDeleteConfirm = {
                                isDeletingGhKey = true
                                scope.launch {
                                    val res = SupabaseClient.deleteGitHubTokenSecret()
                                    isDeletingGhKey = false
                                    if (res.success) {
                                        ownerTokenInput = ""
                                        GitHubTokenProvider.markDeleted(context)
                                        GitHubTokenProvider.refreshStatusMetadata(context)
                                        snackbarHostState.showSnackbar(context.getString(R.string.gh_key_deleted_success))
                                    } else if (SupabaseClient.isTokenExpiredError(res.httpCode, res.message)) {
                                        onSessionExpired()
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            SecureLogger.sanitize(res.message.ifBlank { context.getString(R.string.error_unexpected) })
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        AdminGitHubStatusCard(tokenStatus = tokenStatus)
                    }
                }

                // 1.5) PUBLISH READINESS CHECK ("فحص جاهزية النشر")
                item {
                    PublishReadinessCard(
                        isRunning = isRunningReadinessCheck,
                        steps = readinessSteps,
                        onRunCheck = { runReadinessDiagnostic() }
                    )
                }

                // 2) STORE UPDATE SECTION
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
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.admin_store_update_section),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                AdminTextField(
                                    value = storeUpdateVersionCode,
                                    onValueChange = { storeUpdateVersionCode = it },
                                    label = stringResource(R.string.admin_new_version_code),
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AdminTextField(
                                    value = storeUpdateVersionName,
                                    onValueChange = { storeUpdateVersionName = it },
                                    label = stringResource(R.string.admin_new_version_name),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = storeUpdateNotes,
                                onValueChange = { storeUpdateNotes = it },
                                label = stringResource(R.string.admin_update_notes),
                                singleLine = false,
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = storeUpdateApkUrl,
                                onValueChange = { storeUpdateApkUrl = it },
                                label = stringResource(R.string.admin_update_apk_url)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isPublishingStoreUpdate) {
                                        val cleanUrl = storeUpdateApkUrl.trim()
                                        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.error_unexpected))
                                            }
                                            return@clickable
                                        }
                                        val code = (storeUpdateVersionCode.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
                                        val cleanVer = storeUpdateVersionName.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "1.0.0"
                                        val cleanNotes = storeUpdateNotes.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
                                        isPublishingStoreUpdate = true
                                        scope.launch {
                                            val res = withContext(Dispatchers.IO) {
                                                SupabaseClient.updateStoreUpdate(
                                                    versionCode = code,
                                                    versionName = cleanVer,
                                                    notes = cleanNotes,
                                                    apkUrl = cleanUrl
                                                )
                                            }
                                            isPublishingStoreUpdate = false
                                            if (res.success) {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.admin_store_update_published)
                                                )
                                            } else if (SupabaseClient.isTokenExpiredError(res.httpCode, res.message)) {
                                                onSessionExpired()
                                            } else if (SupabaseClient.isRlsPermissionError(res.httpCode, res.message)) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.err_rls_not_admin))
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        R.string.toast_supabase_error,
                                                        res.httpCode,
                                                        res.message.ifBlank { context.getString(R.string.error_unexpected) }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPublishingStoreUpdate) {
                                        stringResource(R.string.loading)
                                    } else {
                                        stringResource(R.string.admin_publish_store_update)
                                    },
                                    color = colors.textOnAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 3) APP ADD/EDIT FORM
                item {
                    NeonCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (editingAppId != null) {
                                        stringResource(R.string.admin_edit_app_header, name.ifBlank { editingAppId.orEmpty() })
                                    } else {
                                        stringResource(R.string.admin_add_new_app)
                                    },
                                    color = colors.accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (editingAppId != null || name.isNotBlank() || directFileUrl.isNotBlank() || pickedApkUri != null) {
                                    Text(
                                        text = if (editingAppId != null) stringResource(R.string.admin_cancel_edit) else stringResource(R.string.clear_all),
                                        color = colors.danger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(enabled = !publishState.isRunning) {
                                            clearForm(clearPersistedDraft = true)
                                            AdminPublishCoordinator.clearError()
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // App Name
                            AdminTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = stringResource(R.string.admin_app_name_label)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Mandatory Item Type Selector (Game vs App) BEFORE Category Picker
                            Text(
                                text = stringResource(R.string.admin_item_type_label),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isGameSelected = itemType == StoreCategories.ITEM_TYPE_GAME
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isGameSelected) colors.accent else colors.cardAlt)
                                        .border(1.dp, if (isGameSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                                        .clickable {
                                            itemType = StoreCategories.ITEM_TYPE_GAME
                                            if (selectedCategoryItem.type != StoreCategories.ITEM_TYPE_GAME) {
                                                val fallback = allGameCategories.firstOrNull { !it.isHidden }?.let { categoriesRepo.toCategoryItem(it) }
                                                    ?: StoreCategories.GAMES.first()
                                                selectedCategoryItem = fallback
                                            }
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("admin_type_game"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🎮 ${stringResource(R.string.admin_type_game)}",
                                        color = if (isGameSelected) colors.textOnAccent else colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val isAppSelected = itemType == StoreCategories.ITEM_TYPE_APP
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isAppSelected) colors.accent else colors.cardAlt)
                                        .border(1.dp, if (isAppSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                                        .clickable {
                                            itemType = StoreCategories.ITEM_TYPE_APP
                                            if (selectedCategoryItem.type != StoreCategories.ITEM_TYPE_APP) {
                                                val fallback = allAppCategories.firstOrNull { !it.isHidden }?.let { categoriesRepo.toCategoryItem(it) }
                                                    ?: StoreCategories.APPS.first()
                                                selectedCategoryItem = fallback
                                            }
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("admin_type_app"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📱 ${stringResource(R.string.admin_type_app)}",
                                        color = if (isAppSelected) colors.textOnAccent else colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Selector with Modal (filtered to selected Item Type)
                            val selectedCategoryLabel = if (selectedCategoryItem.isCustomName) {
                                val lang = SettingsRepository.getInstance(context).getEffectiveLanguageCode()
                                if (lang == "ar" || lang == "fa" || lang == "ur") {
                                    selectedCategoryItem.titleAr.ifBlank { selectedCategoryItem.titleEn }
                                } else {
                                    selectedCategoryItem.titleEn.ifBlank { selectedCategoryItem.titleAr }
                                }
                            } else {
                                stringResource(selectedCategoryItem.titleRes)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { showCategoryModal = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("admin_category_selector"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = selectedCategoryItem.emoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = stringResource(
                                                R.string.admin_select_category_btn,
                                                selectedCategoryLabel
                                            ),
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${stringResource(selectedCategoryItem.groupRes)} • ${selectedCategoryItem.titleEn}",
                                            color = colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.edit),
                                    color = colors.accent,
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
                                    label = stringResource(R.string.admin_app_version_label),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AdminTextField(
                                    value = ratingStr,
                                    onValueChange = { ratingStr = it },
                                    label = stringResource(R.string.admin_app_rating_label),
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AdminTextField(
                                    value = sizeStr,
                                    onValueChange = { sizeStr = it },
                                    label = stringResource(R.string.admin_app_size_label),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Description
                            AdminTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = stringResource(R.string.admin_app_desc_label),
                                singleLine = false,
                                minLines = 3
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Features
                            AdminTextField(
                                value = featuresStr,
                                onValueChange = { featuresStr = it },
                                label = stringResource(R.string.admin_app_features_label),
                                singleLine = false,
                                minLines = 3
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Open Source Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.cardAlt, RoundedCornerShape(12.dp))
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.admin_open_source_toggle),
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isOpenSource,
                                    onCheckedChange = { isOpenSource = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.textOnAccent,
                                        checkedTrackColor = colors.accent
                                    )
                                )
                            }

                            AnimatedVisibility(visible = isOpenSource) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AdminTextField(
                                        value = sourceCodeUrl,
                                        onValueChange = { sourceCodeUrl = it },
                                        label = stringResource(R.string.admin_source_code_url)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // SECTION 1: Icon Picker
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { iconPicker.launch("image/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.admin_pick_icon_btn),
                                        color = colors.textOnAccent,
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
                                        .background(colors.cardAlt, RoundedCornerShape(14.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pickedIconUri != null) {
                                            AsyncImage(
                                                model = pickedIconUri,
                                                imageLoader = imageLoader,
                                                contentDescription = stringResource(R.string.admin_pick_icon_btn),
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (apkExtractedIconBytes != null) {
                                            AsyncImage(
                                                model = apkExtractedIconBytes,
                                                imageLoader = imageLoader,
                                                contentDescription = stringResource(R.string.admin_pick_icon_btn),
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (storedIconUrl.startsWith("http")) {
                                            AsyncImage(
                                                model = storedIconUrl,
                                                imageLoader = imageLoader,
                                                contentDescription = stringResource(R.string.admin_pick_icon_btn),
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
                                            text = name.ifBlank { stringResource(R.string.admin_app_name_label) },
                                            color = colors.textPrimary,
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
                                            contentDescription = stringResource(R.string.delete),
                                            tint = colors.danger,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                AdminTextField(
                                    value = storedIconUrl,
                                    onValueChange = { storedIconUrl = it },
                                    label = stringResource(R.string.admin_or_icon_url)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // SECTION 2: Screenshots
                            val totalShots = storedScreenshotUrls.size + pickedShotUris.size
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { shotsPicker.launch("image/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.admin_add_screenshots_btn, totalShots),
                                        color = colors.textOnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (totalShots > 0) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.cardAlt, RoundedCornerShape(14.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.buttonGradient)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { apkPicker.launch("*/*") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Android,
                                        contentDescription = null,
                                        tint = colors.textOnAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.admin_pick_apk_btn),
                                        color = colors.textOnAccent,
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
                                        .background(colors.cardAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(colors.surface, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📦", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(
                                                R.string.admin_picked_file_info,
                                                pickedApkFileName,
                                                formatSize(pickedApkSizeBytes)
                                            ),
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
                                            contentDescription = stringResource(R.string.cancel),
                                            tint = colors.danger,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            AdminTextField(
                                value = directFileUrl,
                                onValueChange = { directFileUrl = it },
                                label = stringResource(R.string.admin_or_direct_apk_url)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AdminTextField(
                                value = detectedAbis,
                                onValueChange = { detectedAbis = it },
                                label = stringResource(R.string.admin_abis_label)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Collapsible Advanced Bundle & Verification Fields
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.cardAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { showAdvancedInstallerFields = !showAdvancedInstallerFields }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("admin_advanced_installer_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🧩 ${stringResource(R.string.admin_advanced_installer_fields)}",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showAdvancedInstallerFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.accent
                                )
                            }

                            AnimatedVisibility(visible = showAdvancedInstallerFields) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AdminTextField(
                                        value = sha256Input,
                                        onValueChange = { sha256Input = it },
                                        label = stringResource(R.string.admin_sha256_optional)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AdminTextField(
                                        value = splitApkUrlsText,
                                        onValueChange = { splitApkUrlsText = it },
                                        label = stringResource(R.string.admin_split_urls_optional),
                                        singleLine = false,
                                        minLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AdminTextField(
                                        value = obbUrl,
                                        onValueChange = { obbUrl = it },
                                        label = stringResource(R.string.admin_obb_url_optional)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // UPLOAD PROGRESS (App-scoped Coordinator state)
                            AnimatedVisibility(
                                visible = publishState.isRunning,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.cardAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = publishState.stepLabel,
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (publishState.isGitHubPhase && publishState.totalMbText.isNotBlank()) {
                                                "${publishState.uploadedMbText} / ${publishState.totalMbText} (${(publishState.progress * 100).toInt()}%)"
                                            } else {
                                                "${(publishState.progress * 100).toInt()}%"
                                            },
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { publishState.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = colors.accent,
                                        trackColor = colors.surface
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // FAILURE + RETRY BANNER ("فشل الرفع" + "إعادة المحاولة")
                            val activeError = publishState.errorMessage
                            AnimatedVisibility(
                                visible = !publishState.isRunning && !activeError.isNullOrBlank(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 14.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.danger.copy(alpha = 0.12f))
                                        .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = null,
                                            tint = colors.danger,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.err_upload_failed),
                                            color = colors.danger,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeError.orEmpty(),
                                        color = colors.textPrimary,
                                        fontSize = 12.sp
                                    )
                                    if (publishState.canRetry) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.accent)
                                                .testTag("retry_publish_button")
                                                .clickable {
                                                    AdminPublishCoordinator.retryLastPublish(context, adminEmail) {
                                                        onCatalogChanged()
                                                    }
                                                }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    tint = colors.textOnAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.retry),
                                                    color = colors.textOnAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // SUBMIT BUTTON
                            val canSave = name.isNotBlank() && !publishState.isRunning
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (canSave) {
                                            Modifier.background(colors.buttonGradient)
                                        } else {
                                            Modifier.background(colors.cardAlt)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (canSave) colors.border else colors.border.copy(alpha = 0.4f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .testTag("admin_save_app_button")
                                    .clickable(enabled = canSave) { startSaveProcess() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (editingAppId != null) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = if (canSave) colors.textOnAccent else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            publishState.isRunning -> stringResource(R.string.admin_uploading_cloud)
                                            editingAppId != null -> stringResource(R.string.admin_update_app_btn)
                                            else -> stringResource(R.string.admin_save_app_btn)
                                        },
                                        color = if (canSave) colors.textOnAccent else colors.textMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3.5) CATEGORY MANAGER (Room-backed Add / Rename / Emoji / Reorder / Hide)
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    NeonCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCategoryManagerSection = !showCategoryManagerSection }
                                    .testTag("admin_category_manager_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🗂️ ${stringResource(R.string.admin_category_manager)}",
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.admin_cat_manage_desc),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (showCategoryManagerSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.accent
                                )
                            }

                            if (showCategoryManagerSection) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val gamesTabSelected = catManagerTypeTab == StoreCategories.ITEM_TYPE_GAME
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (gamesTabSelected) colors.accent else colors.cardAlt)
                                            .border(1.dp, if (gamesTabSelected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                                            .clickable { catManagerTypeTab = StoreCategories.ITEM_TYPE_GAME }
                                            .padding(vertical = 8.dp)
                                            .testTag("admin_catmgr_tab_games"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🎮 ${stringResource(R.string.tab_games)} (${allGameCategories.size})",
                                            color = if (gamesTabSelected) colors.textOnAccent else colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val appsTabSelected = catManagerTypeTab == StoreCategories.ITEM_TYPE_APP
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (appsTabSelected) colors.accent else colors.cardAlt)
                                            .border(1.dp, if (appsTabSelected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                                            .clickable { catManagerTypeTab = StoreCategories.ITEM_TYPE_APP }
                                            .padding(vertical = 8.dp)
                                            .testTag("admin_catmgr_tab_apps"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "📱 ${stringResource(R.string.tab_apps_only)} (${allAppCategories.size})",
                                            color = if (appsTabSelected) colors.textOnAccent else colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.buttonGradient)
                                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                        .clickable {
                                            catDialogNameEn = ""
                                            catDialogNameAr = ""
                                            catDialogEmoji = if (catManagerTypeTab == StoreCategories.ITEM_TYPE_GAME) "🎮" else "📱"
                                            showAddCategoryDialog = true
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("admin_add_category_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = colors.textOnAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.admin_add_category),
                                            color = colors.textOnAccent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCategoryManagerSection) {
                    val managedList = if (catManagerTypeTab == StoreCategories.ITEM_TYPE_GAME) {
                        allGameCategories
                    } else {
                        allAppCategories
                    }
                    itemsIndexed(
                        items = managedList,
                        key = { _, entity -> "mgr_${entity.key}" },
                        contentType = { _, _ -> "admin_cat_mgr_row" }
                    ) { idx, entity ->
                        val titleText = CategoriesRepository.resolveDisplayTitle(context, entity)
                        NeonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = entity.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = titleText,
                                            color = colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (entity.isHidden) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.admin_cat_hidden_badge),
                                                color = colors.warning,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${entity.nameEn} • #${idx + 1}",
                                        color = colors.textMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onMoveCategory(entity.key, entity.type, true) },
                                    enabled = idx > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = stringResource(R.string.admin_move_up),
                                        tint = if (idx > 0) colors.accent else colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveCategory(entity.key, entity.type, false) },
                                    enabled = idx < managedList.lastIndex
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDownward,
                                        contentDescription = stringResource(R.string.admin_move_down),
                                        tint = if (idx < managedList.lastIndex) colors.accent else colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        catDialogNameEn = entity.nameEn
                                        catDialogNameAr = entity.nameAr
                                        catDialogEmoji = entity.emoji
                                        editingCategoryEntity = entity
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.admin_rename_category),
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleCategoryHidden(entity.key, !entity.isHidden) }
                                ) {
                                    Icon(
                                        imageVector = if (entity.isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (entity.isHidden) R.string.admin_cat_show else R.string.admin_cat_hide
                                        ),
                                        tint = if (entity.isHidden) colors.warning else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4) APPS LIST
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.admin_registered_apps_count, apps.size),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(
                    items = apps,
                    key = { it.id },
                    contentType = { "admin_app_row" }
                ) { app ->
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
                                        .background(colors.cardAlt, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!app.iconUrl.isNullOrBlank() && app.iconUrl.startsWith("http")) {
                                        AsyncImage(
                                            model = app.iconUrl,
                                            imageLoader = imageLoader,
                                            contentDescription = app.nameAr,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
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
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${app.localizedCategoryTitle()} • ${app.displayVersionBadge} • ⭐ ${app.rating} • ${app.size}",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { populateForEdit(app) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.edit),
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { appToDelete = app }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = colors.danger,
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

    // CATEGORY PICKER FULLSCREEN MODAL
    if (showCategoryModal) {
        Dialog(onDismissRequest = { showCategoryModal = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.admin_choose_category_title),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showCategoryModal = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = colors.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = categorySearchQuery,
                        onValueChange = { categorySearchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.admin_search_category_hint),
                                fontSize = 12.sp,
                                color = colors.textMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = colors.accent
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.cardAlt,
                            unfocusedContainerColor = colors.cardAlt
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val q = categorySearchQuery.trim().lowercase()
                    val typeSourceCategories = remember(itemType, allGameCategories, allAppCategories) {
                        val roomVisible = if (itemType == StoreCategories.ITEM_TYPE_GAME) {
                            allGameCategories.filter { !it.isHidden }
                        } else {
                            allAppCategories.filter { !it.isHidden }
                        }
                        if (roomVisible.isNotEmpty()) {
                            roomVisible.map { categoriesRepo.toCategoryItem(it) }
                        } else {
                            if (itemType == StoreCategories.ITEM_TYPE_GAME) StoreCategories.GAMES else StoreCategories.APPS
                        }
                    }
                    val filteredCategories = remember(q, typeSourceCategories) {
                        if (q.isEmpty()) {
                            typeSourceCategories
                        } else {
                            typeSourceCategories.filter { cat ->
                                context.getString(cat.titleRes).lowercase().contains(q) ||
                                    cat.titleEn.lowercase().contains(q) ||
                                    cat.titleAr.lowercase().contains(q) ||
                                    cat.key.lowercase().contains(q)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val grouped = filteredCategories.groupBy { it.groupRes }
                        for ((groupRes, groupItems) in grouped) {
                            item {
                                Text(
                                    text = stringResource(groupRes),
                                    color = colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(
                                items = groupItems,
                                key = { it.key },
                                contentType = { "admin_cat_pick" }
                            ) { cat ->
                                val isSelected = selectedCategoryItem.key.lowercase() == cat.key.lowercase()
                                val catTitleText = if (cat.isCustomName) {
                                    val lang = SettingsRepository.getInstance(context).getEffectiveLanguageCode()
                                    if (lang == "ar" || lang == "fa" || lang == "ur") {
                                        cat.titleAr.ifBlank { cat.titleEn }
                                    } else {
                                        cat.titleEn.ifBlank { cat.titleAr }
                                    }
                                } else {
                                    stringResource(cat.titleRes)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.cardAlt)
                                        .border(
                                            1.dp,
                                            if (isSelected) colors.accent else colors.border,
                                            RoundedCornerShape(10.dp)
                                        )
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
                                                text = catTitleText,
                                                color = colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = cat.titleEn,
                                                color = colors.textMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.confirm),
                                            tint = colors.accent,
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

    // Add Custom Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.admin_add_category),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTextField(
                        value = catDialogNameEn,
                        onValueChange = { catDialogNameEn = it },
                        label = stringResource(R.string.admin_cat_name_en)
                    )
                    AdminTextField(
                        value = catDialogNameAr,
                        onValueChange = { catDialogNameAr = it },
                        label = stringResource(R.string.admin_cat_name_ar)
                    )
                    AdminTextField(
                        value = catDialogEmoji,
                        onValueChange = { catDialogEmoji = it },
                        label = stringResource(R.string.admin_cat_emoji)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catDialogNameEn.isNotBlank() || catDialogNameAr.isNotBlank()) {
                            onAddCategory(catDialogNameEn, catDialogNameAr, catDialogEmoji, catManagerTypeTab)
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text(stringResource(R.string.confirm), color = colors.textOnAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textMuted)
                }
            }
        )
    }

    // Rename / Edit Emoji Category Dialog
    val catToEdit = editingCategoryEntity
    if (catToEdit != null) {
        AlertDialog(
            onDismissRequest = { editingCategoryEntity = null },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.admin_rename_category),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTextField(
                        value = catDialogNameEn,
                        onValueChange = { catDialogNameEn = it },
                        label = stringResource(R.string.admin_cat_name_en)
                    )
                    AdminTextField(
                        value = catDialogNameAr,
                        onValueChange = { catDialogNameAr = it },
                        label = stringResource(R.string.admin_cat_name_ar)
                    )
                    AdminTextField(
                        value = catDialogEmoji,
                        onValueChange = { catDialogEmoji = it },
                        label = stringResource(R.string.admin_cat_emoji)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catDialogNameEn.isNotBlank() || catDialogNameAr.isNotBlank()) {
                            onUpdateCategory(catToEdit.key, catDialogNameEn, catDialogNameAr, catDialogEmoji)
                            editingCategoryEntity = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text(stringResource(R.string.confirm), color = colors.textOnAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategoryEntity = null }) {
                    Text(stringResource(R.string.cancel), color = colors.textMuted)
                }
            }
        )
    }

    // Alerts
    if (showOver2GbAlert) {
        AlertDialog(
            onDismissRequest = { showOver2GbAlert = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.admin_file_over_2gb_title),
                    color = colors.warning,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.admin_file_over_2gb_msg),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showOver2GbAlert = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.textOnAccent
                    )
                ) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
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
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
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
                            text = stringResource(R.string.admin_screenshot_preview),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { fullscreenPreviewUrl = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = fullscreenPreviewUrl,
                        imageLoader = imageLoader,
                        contentDescription = stringResource(R.string.screenshots),
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
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.admin_delete_app_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.admin_delete_app_msg, appToDelete?.nameAr.orEmpty()),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appToDelete?.let { onDeleteApp(it.id) }
                        appToDelete = null
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.admin_delete_app_done))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { appToDelete = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cardAlt,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (verifiedSavedApp != null) {
        val app = verifiedSavedApp!!
        AlertDialog(
            onDismissRequest = { verifiedSavedApp = null },
            containerColor = colors.card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎉 ", fontSize = 20.sp)
                    Text(
                        text = stringResource(R.string.admin_saved_supabase_title),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.admin_saved_label_app, app.nameAr),
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.admin_saved_label_version, app.displayVersionBadge),
                        color = colors.textPrimary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.admin_saved_label_cat, app.localizedCategoryTitle()),
                        color = colors.textPrimary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { verifiedSavedApp = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.textOnAccent
                    )
                ) {
                    Text(stringResource(R.string.admin_done_great), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (supabaseErrorResult != null) {
        val err = supabaseErrorResult!!
        AlertDialog(
            onDismissRequest = { supabaseErrorResult = null },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.admin_supabase_error_title, err.code),
                    color = colors.danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.admin_supabase_error_details),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.admin_supabase_error_msg_label,
                            err.message.ifBlank { stringResource(R.string.admin_unspecified) }
                        ),
                        color = colors.danger,
                        fontSize = 12.sp
                    )
                    if (err.details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.admin_supabase_error_det_label, err.details),
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { supabaseErrorResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
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
    val colors = PrimeTheme.colors
    val context = LocalContext.current
    val imageLoader = remember(context) { StoreImageLoader.get(context) }
    Box(
        modifier = Modifier
            .size(90.dp, 130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = stringResource(R.string.screenshots),
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
                contentDescription = stringResource(R.string.delete),
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
    val colors = PrimeTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textMuted,
            focusedContainerColor = colors.cardAlt,
            unfocusedContainerColor = colors.cardAlt,
            cursorColor = colors.accent
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
