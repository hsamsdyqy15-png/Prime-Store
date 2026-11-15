package com.example.data.github

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.StoreCategories
import com.example.data.repository.AppsRepository
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import com.example.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.UUID

data class AdminPublishDraft(
    val editingAppId: String? = null,
    val name: String = "",
    val categoryKey: String = "tools",
    val versionStr: String = "1.0.0",
    val ratingStr: String = "4.5",
    val sizeStr: String = "120 MB",
    val description: String = "",
    val featuresStr: String = "",
    val directFileUrl: String = "",
    val storedIconUrl: String = "",
    val storedScreenshotUrls: List<String> = emptyList(),
    val isOpenSource: Boolean = false,
    val sourceCodeUrl: String = "",
    val detectedAbis: String = "Universal (All Architectures)",
    val pickedIconUriStr: String? = null,
    val pickedShotUriStrs: List<String> = emptyList(),
    val pickedApkUriStr: String? = null,
    val pickedApkFileName: String = "",
    val pickedApkSizeBytes: Long = 0L,
    val itemType: String = StoreCategories.ITEM_TYPE_APP,
    val sha256: String = "",
    val splitApkUrlsText: String = "",
    val obbUrl: String = ""
)

data class AdminPublishState(
    val isRunning: Boolean = false,
    val stepLabel: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val progress: Float = 0f,
    val uploadedMbText: String = "0 MB",
    val totalMbText: String = "0 MB",
    val isGitHubPhase: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val sessionExpiredDuringPublish: Boolean = false,
    val supabaseError: SupabaseClient.SupabaseMutationResult? = null,
    val verifiedSavedApp: AppItem? = null,
    val completedSuccessMessage: String? = null
)

object AdminPublishCoordinator {

    private const val TAG = "AdminPublishCoord"
    private const val DRAFT_PREFS = "admin_publish_draft_prefs"
    private const val KEY_DRAFT_JSON = "draft_json"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(AdminPublishState())
    val state: StateFlow<AdminPublishState> = _state.asStateFlow()

    private var lastApkExtractedIconBytes: ByteArray? = null
    private var lastOnCatalogSaved: (() -> Unit)? = null

    fun saveDraft(context: Context, draft: AdminPublishDraft) {
        try {
            val json = JSONObject().apply {
                put("editingAppId", draft.editingAppId ?: JSONObject.NULL)
                put("name", draft.name)
                put("categoryKey", draft.categoryKey)
                put("versionStr", draft.versionStr)
                put("ratingStr", draft.ratingStr)
                put("sizeStr", draft.sizeStr)
                put("description", draft.description)
                put("featuresStr", draft.featuresStr)
                put("directFileUrl", draft.directFileUrl)
                put("storedIconUrl", draft.storedIconUrl)
                put("storedScreenshotUrls", JSONArray(draft.storedScreenshotUrls))
                put("isOpenSource", draft.isOpenSource)
                put("sourceCodeUrl", draft.sourceCodeUrl)
                put("detectedAbis", draft.detectedAbis)
                put("pickedIconUriStr", draft.pickedIconUriStr ?: JSONObject.NULL)
                put("pickedShotUriStrs", JSONArray(draft.pickedShotUriStrs))
                put("pickedApkUriStr", draft.pickedApkUriStr ?: JSONObject.NULL)
                put("pickedApkFileName", draft.pickedApkFileName)
                put("pickedApkSizeBytes", draft.pickedApkSizeBytes)
                put("itemType", draft.itemType)
                put("sha256", draft.sha256)
                put("splitApkUrlsText", draft.splitApkUrlsText)
                put("obbUrl", draft.obbUrl)
            }
            context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DRAFT_JSON, json.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun loadDraft(context: Context): AdminPublishDraft? {
        return try {
            val raw = context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DRAFT_JSON, null) ?: return null
            if (raw.isBlank()) return null
            val json = JSONObject(raw)
            val shots = mutableListOf<String>()
            val sArr = json.optJSONArray("storedScreenshotUrls")
            if (sArr != null) {
                for (i in 0 until sArr.length()) shots.add(sArr.optString(i))
            }
            val pickedShots = mutableListOf<String>()
            val psArr = json.optJSONArray("pickedShotUriStrs")
            if (psArr != null) {
                for (i in 0 until psArr.length()) pickedShots.add(psArr.optString(i))
            }
            val loadedCat = json.optString("categoryKey", "tools").ifBlank { "tools" }
            AdminPublishDraft(
                editingAppId = if (json.isNull("editingAppId")) null else json.optString("editingAppId", "").ifBlank { null },
                name = json.optString("name", ""),
                categoryKey = loadedCat,
                versionStr = json.optString("versionStr", "1.0.0"),
                ratingStr = json.optString("ratingStr", "4.5"),
                sizeStr = json.optString("sizeStr", "120 MB"),
                description = json.optString("description", ""),
                featuresStr = json.optString("featuresStr", ""),
                directFileUrl = json.optString("directFileUrl", ""),
                storedIconUrl = json.optString("storedIconUrl", ""),
                storedScreenshotUrls = shots.filter { it.isNotBlank() },
                isOpenSource = json.optBoolean("isOpenSource", false),
                sourceCodeUrl = json.optString("sourceCodeUrl", ""),
                detectedAbis = json.optString("detectedAbis", "Universal (All Architectures)"),
                pickedIconUriStr = if (json.isNull("pickedIconUriStr")) null else json.optString("pickedIconUriStr", "").ifBlank { null },
                pickedShotUriStrs = pickedShots.filter { it.isNotBlank() },
                pickedApkUriStr = if (json.isNull("pickedApkUriStr")) null else json.optString("pickedApkUriStr", "").ifBlank { null },
                pickedApkFileName = json.optString("pickedApkFileName", ""),
                pickedApkSizeBytes = json.optLong("pickedApkSizeBytes", 0L),
                itemType = json.optString("itemType", StoreCategories.inferItemType(loadedCat)),
                sha256 = json.optString("sha256", ""),
                splitApkUrlsText = json.optString("splitApkUrlsText", ""),
                obbUrl = json.optString("obbUrl", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearDraft(context: Context) {
        try {
            context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DRAFT_JSON)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(
            errorMessage = null,
            canRetry = false,
            sessionExpiredDuringPublish = false
        )
    }

    fun clearCompletionSignals() {
        _state.value = _state.value.copy(
            verifiedSavedApp = null,
            completedSuccessMessage = null,
            supabaseError = null
        )
    }

    fun startPublish(
        context: Context,
        adminEmail: String,
        draft: AdminPublishDraft,
        apkExtractedIconBytes: ByteArray?,
        onCatalogSaved: () -> Unit
    ) {
        if (_state.value.isRunning) return
        val appCtx = context.applicationContext
        lastApkExtractedIconBytes = apkExtractedIconBytes
        lastOnCatalogSaved = onCatalogSaved
        saveDraft(appCtx, draft)

        activeJob = appScope.launch {
            executePublishPipeline(appCtx, adminEmail, draft, apkExtractedIconBytes)
        }
    }

    fun retryLastPublish(context: Context, adminEmail: String, onCatalogSaved: () -> Unit) {
        if (_state.value.isRunning) return
        val appCtx = context.applicationContext
        val draft = loadDraft(appCtx) ?: return
        lastOnCatalogSaved = onCatalogSaved
        activeJob = appScope.launch {
            executePublishPipeline(appCtx, adminEmail, draft, lastApkExtractedIconBytes)
        }
    }

    private suspend fun executePublishPipeline(
        context: Context,
        adminEmail: String,
        initialDraft: AdminPublishDraft,
        apkExtractedIconBytes: ByteArray?
    ) {
        var workingDraft = initialDraft
        var startedService = false
        val pickedIconUri = workingDraft.pickedIconUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val pickedShotUris = workingDraft.pickedShotUriStrs.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        val pickedApkUri = workingDraft.pickedApkUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }

        var totalSteps = 1 // final DB write
        if (pickedIconUri != null || (apkExtractedIconBytes != null && workingDraft.storedIconUrl.isBlank())) totalSteps++
        totalSteps += pickedShotUris.size
        if (pickedApkUri != null) totalSteps++

        _state.value = AdminPublishState(
            isRunning = true,
            stepLabel = context.getString(R.string.admin_uploading_cloud),
            currentStep = 0,
            totalSteps = totalSteps,
            progress = 0f,
            uploadedMbText = "0 MB",
            totalMbText = formatSize(workingDraft.pickedApkSizeBytes),
            isGitHubPhase = false
        )

        var currentStep = 0

        try {
            // Ensure service runs cleanly with WakeLock + WifiLock from the start of uploads if any APK is present
            if (pickedApkUri != null) {
                GitHubUploadService.start(
                    context,
                    workingDraft.pickedApkFileName.ifBlank { workingDraft.name.ifBlank { "app.apk" } }
                )
                startedService = true
            }

            // Proactive session refresh before starting uploads
            val initialSessionToken = SupabaseClient.getValidUserToken(forceRefresh = false)
            if (initialSessionToken.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    sessionExpiredDuringPublish = true,
                    errorMessage = context.getString(R.string.err_session_expired_relogin),
                    canRetry = true
                )
                return
            }

            // 1) Icon upload (skip if already uploaded in prior partial attempt)
            var finalIconUrl = workingDraft.storedIconUrl
            if (pickedIconUri != null && finalIconUrl.isBlank()) {
                currentStep++
                _state.value = _state.value.copy(
                    currentStep = currentStep,
                    stepLabel = "${context.getString(R.string.admin_upload_step_icon)} ($currentStep/$totalSteps)",
                    progress = 0f
                )
                val freshToken = SupabaseClient.getValidUserToken(forceRefresh = false)
                    ?: throw SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))

                val ext = getFileExtension(context, pickedIconUri, "png")
                val path = "icons/${UUID.randomUUID()}.$ext"
                val mime = context.contentResolver.getType(pickedIconUri) ?: "image/png"
                var lastIconUiTime = 0L
                val uploadRes = retryStorageUpload(context) {
                    SupabaseClient.uploadStorageFileDetailed(
                        context = context,
                        uri = pickedIconUri,
                        path = path,
                        mimeType = mime,
                        accessToken = freshToken
                    ) { written, tot ->
                        if (tot > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastIconUiTime >= 100L || written >= tot) {
                                lastIconUiTime = now
                                _state.value = _state.value.copy(progress = (written.toFloat() / tot).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                finalIconUrl = uploadRes.getOrElse { throw mapUploadThrowable(context, adminEmail, it) }
                workingDraft = workingDraft.copy(storedIconUrl = finalIconUrl, pickedIconUriStr = null)
                saveDraft(context, workingDraft)
            } else if (apkExtractedIconBytes != null && finalIconUrl.isBlank()) {
                currentStep++
                _state.value = _state.value.copy(
                    currentStep = currentStep,
                    stepLabel = "${context.getString(R.string.admin_upload_step_icon)} ($currentStep/$totalSteps)",
                    progress = 0.5f
                )
                val freshToken = SupabaseClient.getValidUserToken(forceRefresh = false)
                    ?: throw SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))

                val path = "icons/${UUID.randomUUID()}.png"
                val uploadRes = retryStorageUpload(context) {
                    SupabaseClient.uploadStorageBytesDetailed(
                        bytes = apkExtractedIconBytes,
                        path = path,
                        mimeType = "image/png",
                        accessToken = freshToken
                    )
                }
                finalIconUrl = uploadRes.getOrElse { throw mapUploadThrowable(context, adminEmail, it) }
                workingDraft = workingDraft.copy(storedIconUrl = finalIconUrl)
                saveDraft(context, workingDraft)
            }

            // 2) Screenshots upload
            val finalScreenshots = workingDraft.storedScreenshotUrls.toMutableList()
            val remainingShots = pickedShotUris.toMutableList()
            for ((idx, sUri) in pickedShotUris.withIndex()) {
                currentStep++
                _state.value = _state.value.copy(
                    currentStep = currentStep,
                    stepLabel = "${context.getString(R.string.admin_upload_step_screens)} (${idx + 1}/${pickedShotUris.size})",
                    progress = 0f
                )
                val freshToken = SupabaseClient.getValidUserToken(forceRefresh = false)
                    ?: throw SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))

                val ext = getFileExtension(context, sUri, "jpg")
                val path = "shots/${UUID.randomUUID()}.$ext"
                val mime = context.contentResolver.getType(sUri) ?: "image/jpeg"
                var lastShotUiTime = 0L
                val uploadRes = retryStorageUpload(context) {
                    SupabaseClient.uploadStorageFileDetailed(
                        context = context,
                        uri = sUri,
                        path = path,
                        mimeType = mime,
                        accessToken = freshToken
                    ) { written, tot ->
                        if (tot > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastShotUiTime >= 100L || written >= tot) {
                                lastShotUiTime = now
                                _state.value = _state.value.copy(progress = (written.toFloat() / tot).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                val shotUrl = uploadRes.getOrElse { throw mapUploadThrowable(context, adminEmail, it) }
                finalScreenshots.add(shotUrl)
                remainingShots.remove(sUri)
                workingDraft = workingDraft.copy(
                    storedScreenshotUrls = finalScreenshots.toList(),
                    pickedShotUriStrs = remainingShots.map { it.toString() }
                )
                saveDraft(context, workingDraft)
            }

            // 3) APK upload via GitHub Releases (centralized in-memory token)
            var finalFileUrl = workingDraft.directFileUrl
            var uploadedApkSizeStr = workingDraft.sizeStr
            if (pickedApkUri != null) {
                currentStep++
                val apkFileName = workingDraft.pickedApkFileName.ifBlank { "app.apk" }
                _state.value = _state.value.copy(
                    currentStep = currentStep,
                    isGitHubPhase = true,
                    stepLabel = "${context.getString(R.string.admin_upload_step_apk_gh)} ($currentStep/$totalSteps)",
                    progress = 0f,
                    uploadedMbText = "0 MB",
                    totalMbText = formatSize(workingDraft.pickedApkSizeBytes)
                )

                // Proactive session refresh right before fetching central key & uploading
                val preUploadToken = SupabaseClient.getValidUserToken(forceRefresh = false)
                if (preUploadToken.isNullOrBlank()) {
                    throw SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))
                }

                var lastNotifTime = 0L
                var lastUiTime = 0L
                val ghResult = GitHubStorageHelper.uploadAppRelease(
                    context = context,
                    userEmail = adminEmail,
                    appId = workingDraft.editingAppId ?: "app_${System.currentTimeMillis()}",
                    appName = workingDraft.name.ifBlank { "App" },
                    uri = pickedApkUri,
                    originalFileName = apkFileName,
                    fileSizeBytes = workingDraft.pickedApkSizeBytes,
                    resolveToken = { forceRefresh ->
                        GitHubTokenProvider.getToken(context, adminEmail, forceRefresh = forceRefresh)
                    },
                    onPhase = { phase ->
                        when (phase) {
                            is GitHubUploadPhase.Progress -> {
                                val frac = if (phase.totalBytes > 0) (phase.bytesWritten.toFloat() / phase.totalBytes).coerceIn(0f, 1f) else 0f
                                val upMb = formatSize(phase.bytesWritten)
                                val totMb = formatSize(phase.totalBytes)
                                val now = System.currentTimeMillis()
                                if (now - lastUiTime >= 100L || phase.bytesWritten >= phase.totalBytes) {
                                    lastUiTime = now
                                    _state.value = _state.value.copy(
                                        progress = frac,
                                        uploadedMbText = upMb,
                                        totalMbText = totMb,
                                        stepLabel = "${context.getString(R.string.admin_upload_step_apk_gh)} ($currentStep/$totalSteps)"
                                    )
                                }
                                if (now - lastNotifTime >= 400L || phase.bytesWritten >= phase.totalBytes) {
                                    lastNotifTime = now
                                    GitHubUploadService.updateProgress(
                                        context,
                                        apkFileName,
                                        (frac * 100).toInt(),
                                        upMb,
                                        totMb
                                    )
                                }
                            }
                            is GitHubUploadPhase.Retrying -> {
                                val label = context.getString(R.string.publish_retrying_attempt, phase.attempt, phase.maxAttempts)
                                _state.value = _state.value.copy(stepLabel = label)
                                GitHubUploadService.updateProgress(
                                    context,
                                    apkFileName,
                                    (_state.value.progress * 100).toInt(),
                                    _state.value.uploadedMbText,
                                    _state.value.totalMbText,
                                    statusText = label
                                )
                            }
                            is GitHubUploadPhase.WaitingForNetwork -> {
                                val label = context.getString(R.string.publish_waiting_network)
                                _state.value = _state.value.copy(stepLabel = label)
                                GitHubUploadService.updateProgress(
                                    context,
                                    apkFileName,
                                    (_state.value.progress * 100).toInt(),
                                    _state.value.uploadedMbText,
                                    _state.value.totalMbText,
                                    statusText = context.getString(R.string.gh_service_waiting_network)
                                )
                            }
                        }
                    }
                )

                val ghAsset = ghResult.getOrElse { throw mapUploadThrowable(context, adminEmail, it) }
                finalFileUrl = ghAsset.downloadUrl
                uploadedApkSizeStr = ghAsset.sizeMB
                workingDraft = workingDraft.copy(
                    directFileUrl = finalFileUrl,
                    sizeStr = uploadedApkSizeStr,
                    pickedApkUriStr = null
                )
                saveDraft(context, workingDraft)
            }

            if (finalIconUrl.isBlank() || finalFileUrl.isBlank()) {
                throw Exception(context.getString(R.string.error_empty_file))
            }

            // 4) Refresh session immediately BEFORE saving to apps table (never write DB until all uploads succeed)
            currentStep = totalSteps
            _state.value = _state.value.copy(
                currentStep = currentStep,
                isGitHubPhase = false,
                stepLabel = context.getString(R.string.admin_upload_step_saving),
                progress = 1f
            )

            val freshDbToken = SupabaseClient.getValidUserToken(forceRefresh = true)
                ?: SupabaseClient.getValidUserToken(forceRefresh = false)
                ?: throw SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))

            val rating = workingDraft.ratingStr.toFloatOrNull() ?: 4.5f
            val finalCalculatedSize = if (uploadedApkSizeStr.isNotBlank() && uploadedApkSizeStr != "120 MB") {
                uploadedApkSizeStr
            } else if (workingDraft.sizeStr.isNotBlank() && workingDraft.sizeStr != "120 MB") {
                workingDraft.sizeStr
            } else if (workingDraft.pickedApkSizeBytes > 0) {
                formatSize(workingDraft.pickedApkSizeBytes)
            } else {
                workingDraft.sizeStr
            }
            val features = workingDraft.featuresStr.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            val categoryKey = workingDraft.categoryKey
            val resolvedItemType = StoreCategories.normalizeItemType(workingDraft.itemType)
            val resolvedSha256 = workingDraft.sha256.trim()
            val resolvedSplitUrls = workingDraft.splitApkUrlsText
                .split("\n", ",")
                .map { it.trim() }
                .filter { it.startsWith("http", ignoreCase = true) }
            val resolvedObbUrl = workingDraft.obbUrl.trim()
            val pkgName = "com.primestore.${workingDraft.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"

            val mutationResult = if (workingDraft.editingAppId == null) {
                SupabaseClient.insertApp(
                    accessToken = freshDbToken,
                    name = workingDraft.name,
                    category = categoryKey,
                    rating = rating,
                    size = finalCalculatedSize,
                    description = workingDraft.description,
                    features = features,
                    iconUrl = finalIconUrl,
                    fileUrl = finalFileUrl,
                    screenshots = finalScreenshots,
                    architecture = workingDraft.detectedAbis,
                    version = workingDraft.versionStr.ifBlank { "1.0.0" },
                    packageName = pkgName,
                    downloads = "0",
                    itemType = resolvedItemType,
                    sha256 = resolvedSha256,
                    splitUrls = resolvedSplitUrls,
                    obbUrl = resolvedObbUrl
                )
            } else {
                SupabaseClient.updateApp(
                    accessToken = freshDbToken,
                    id = workingDraft.editingAppId!!,
                    name = workingDraft.name,
                    category = categoryKey,
                    rating = rating,
                    size = finalCalculatedSize,
                    description = workingDraft.description,
                    features = features,
                    iconUrl = finalIconUrl,
                    fileUrl = finalFileUrl,
                    screenshots = finalScreenshots,
                    architecture = workingDraft.detectedAbis,
                    version = workingDraft.versionStr.ifBlank { "1.0.0" },
                    packageName = pkgName,
                    downloads = "0",
                    itemType = resolvedItemType,
                    sha256 = resolvedSha256,
                    splitUrls = resolvedSplitUrls,
                    obbUrl = resolvedObbUrl
                )
            }

            if (!mutationResult.success) {
                val combined = "${mutationResult.message} ${mutationResult.details}"
                when {
                    SupabaseClient.isTokenExpiredError(mutationResult.httpCode, combined) -> {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            sessionExpiredDuringPublish = true,
                            errorMessage = context.getString(R.string.err_session_expired_relogin),
                            canRetry = true
                        )
                    }
                    SupabaseClient.isRlsPermissionError(mutationResult.httpCode, combined) -> {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            errorMessage = context.getString(R.string.err_rls_not_admin),
                            canRetry = false,
                            supabaseError = mutationResult
                        )
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            errorMessage = "${context.getString(R.string.err_upload_failed)}: ${SecureLogger.sanitize(mutationResult.message)}",
                            canRetry = true,
                            supabaseError = mutationResult
                        )
                    }
                }
                return
            }

            // Clear draft ONLY after apps table write succeeds
            clearDraft(context)
            lastApkExtractedIconBytes = null

            val appsRepo = AppsRepository.getInstance(context)
            val syncMgr = SyncManager.getInstance(context)
            syncMgr.invalidateEtag()

            val returnedApp = mutationResult.savedApp?.let { saved ->
                saved.copy(
                    itemType = resolvedItemType,
                    sha256 = saved.sha256 ?: resolvedSha256.ifBlank { null },
                    splitApkUrls = if (saved.splitApkUrls.isNotEmpty()) saved.splitApkUrls else resolvedSplitUrls,
                    obbUrl = saved.obbUrl ?: resolvedObbUrl.ifBlank { null }
                )
            }
            if (returnedApp != null && returnedApp.id.isNotBlank()) {
                appsRepo.upsertLocalApp(returnedApp)
            }
            syncMgr.syncNow(forceFull = false, suppressNewBanner = true)

            withContext(Dispatchers.Main) {
                lastOnCatalogSaved?.invoke()
            }

            val verifiedApp = returnedApp ?: runCatching {
                appsRepo.getAllAppsDirect().find { it.name.trim().equals(workingDraft.name.trim(), ignoreCase = true) }
            }.getOrNull()

            val successText = if (workingDraft.editingAppId != null) {
                context.getString(R.string.toast_app_updated)
            } else {
                context.getString(R.string.toast_app_published)
            }

            _state.value = AdminPublishState(
                isRunning = false,
                progress = 1f,
                verifiedSavedApp = verifiedApp,
                completedSuccessMessage = successText
            )
        } catch (e: SessionExpiredPublishException) {
            _state.value = _state.value.copy(
                isRunning = false,
                sessionExpiredDuringPublish = true,
                errorMessage = e.message ?: context.getString(R.string.err_session_expired_relogin),
                canRetry = true
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "PUBLISH_FAIL", "Admin publish failed", e)
            val errMsg = when (e) {
                is GitHubTokenException -> e.userMessage
                is GitHubApiException -> e.message
                else -> {
                    val raw = SecureLogger.sanitize(e.message.orEmpty())
                    if (!GitHubStorageHelper.isNetworkAvailable(context)) {
                        context.getString(R.string.err_no_internet_retry)
                    } else if (raw.isNotBlank()) {
                        "${context.getString(R.string.err_upload_failed)}: $raw"
                    } else {
                        context.getString(R.string.err_upload_failed)
                    }
                }
            }
            _state.value = _state.value.copy(
                isRunning = false,
                errorMessage = errMsg,
                canRetry = true
            )
        } finally {
            if (startedService) {
                GitHubUploadService.stop(context)
            }
        }
    }

    private suspend fun retryStorageUpload(
        context: Context,
        maxAttempts: Int = 3,
        block: suspend () -> Result<String>
    ): Result<String> {
        var attempt = 1
        var lastResult: Result<String>? = null
        while (attempt <= maxAttempts) {
            if (!GitHubStorageHelper.isNetworkAvailable(context)) {
                delay(1500L)
                if (!GitHubStorageHelper.isNetworkAvailable(context) && attempt == maxAttempts) {
                    return Result.failure(IOException(context.getString(R.string.err_no_internet_retry)))
                }
            }
            val res = block()
            if (res.isSuccess) return res
            lastResult = res
            val err = res.exceptionOrNull()
            val msg = err?.message.orEmpty()
            val isTransient = err is UnknownHostException || err is SocketTimeoutException || err is IOException ||
                msg.contains("HTTP 5", ignoreCase = true) || msg.contains("timeout", ignoreCase = true)
            if (!isTransient || attempt >= maxAttempts) {
                return res
            }
            attempt++
            _state.value = _state.value.copy(
                stepLabel = context.getString(R.string.publish_retrying_attempt, attempt, maxAttempts)
            )
            delay(1800L * (attempt - 1))
        }
        return lastResult ?: Result.failure(Exception(context.getString(R.string.err_upload_failed)))
    }

    private fun mapUploadThrowable(context: Context, adminEmail: String, throwable: Throwable): Throwable {
        if (throwable is GitHubTokenException) {
            return if (throwable.isSessionExpired) {
                SessionExpiredPublishException(throwable.userMessage)
            } else {
                throwable
            }
        }
        if (throwable is GitHubApiException) return throwable
        val msg = throwable.message.orEmpty()
        if (SupabaseClient.isTokenExpiredError(0, msg)) {
            return SessionExpiredPublishException(context.getString(R.string.err_session_expired_relogin))
        }
        if (SupabaseClient.isRlsPermissionError(0, msg)) {
            return Exception(context.getString(R.string.err_rls_not_admin))
        }
        if (!GitHubStorageHelper.isNetworkAvailable(context)) {
            return Exception(context.getString(R.string.err_no_internet_retry))
        }
        return Exception(SecureLogger.sanitize(msg).ifBlank { context.getString(R.string.err_upload_failed) })
    }

    private class SessionExpiredPublishException(message: String) : Exception(message)

    private fun getFileExtension(context: Context, uri: Uri, defaultExt: String): String {
        val mime = context.contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!ext.isNullOrBlank()) return ext
        return defaultExt
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
