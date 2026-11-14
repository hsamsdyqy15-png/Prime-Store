package com.example.data.github

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.example.R
import com.example.data.supabase.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class GitHubUploadResult(
    val downloadUrl: String,
    val sizeBytes: Long,
    val fileName: String,
    val releaseId: Long,
    val sizeMB: String
)

class GitHubApiException(
    val httpCode: Int,
    override val message: String,
    val isTransient: Boolean = false
) : Exception(message)

sealed class GitHubUploadPhase {
    data class Progress(val bytesWritten: Long, val totalBytes: Long) : GitHubUploadPhase()
    data class Retrying(val attempt: Int, val maxAttempts: Int) : GitHubUploadPhase()
    data object WaitingForNetwork : GitHubUploadPhase()
}

object GitHubStorageHelper {

    private const val TAG = "GitHubStorageHelper"

    const val DEFAULT_OWNER = "hsamsdyqy15-png"
    const val DEFAULT_REPO = "prime-store-files"

    private const val LEGACY_SECURE_PREFS = "prime_github_secure_prefs"
    private const val LEGACY_FALLBACK_PREFS = "prime_github_fallback_prefs"

    private val client: OkHttpClient by lazy {
        SecureLogger.attachRedactedLogging(
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
        ).build()
    }

    /**
     * Removes any previously stored local GitHub tokens/settings from older app versions.
     */
    fun purgeLegacyLocalToken(context: Context) {
        try {
            context.getSharedPreferences(LEGACY_SECURE_PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
            context.getSharedPreferences(LEGACY_FALLBACK_PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(LEGACY_SECURE_PREFS)
                context.deleteSharedPreferences(LEGACY_FALLBACK_PREFS)
            } else {
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(prefsDir, "$LEGACY_SECURE_PREFS.xml").delete()
                File(prefsDir, "$LEGACY_FALLBACK_PREFS.xml").delete()
            }
        } catch (_: Exception) {
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun waitForNetworkIfOffline(
        context: Context,
        maxWaitMs: Long = 15_000L,
        onWaiting: () -> Unit
    ): Boolean {
        if (isNetworkAvailable(context)) return true
        onWaiting()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            delay(1500L)
            if (isNetworkAvailable(context)) return true
        }
        return isNetworkAvailable(context)
    }

    suspend fun testConnection(
        token: String,
        userEmail: String? = null,
        context: Context? = null,
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                val msg = context?.getString(R.string.err_gh_token_missing_owner)
                    ?: "GitHub token is empty"
                return@withContext Result.failure(GitHubApiException(401, msg))
            }

            if (context != null && !isNetworkAvailable(context)) {
                return@withContext Result.failure(
                    GitHubApiException(0, context.getString(R.string.err_no_internet_retry), isTransient = true)
                )
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$cleanOwner/$cleanRepo")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val repoFullName = json.optString("full_name", "$cleanOwner/$cleanRepo")
                    if (context != null) {
                        GitHubTokenProvider.recordSuccessfulTestTimestamp(context)
                    }
                    Result.success(
                        context?.getString(R.string.github_repo_connected, repoFullName)
                            ?: "Connected to $repoFullName"
                    )
                } else {
                    val mapped = if (context != null) {
                        GitHubTokenProvider.mapGitHubHttpError(context, response.code, userEmail)
                    } else {
                        parseErrorMessage(response.code, body, null)
                    }
                    SecureLogger.e(TAG, "GH_TEST_${response.code}", "GitHub connection test failed (HTTP ${response.code})")
                    Result.failure(
                        GitHubApiException(
                            httpCode = response.code,
                            userMessageOrNull(mapped, response.code),
                            isTransient = response.code >= 500
                        )
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "GH_TEST_EXC", "GitHub connection test exception", e)
            val msg = if (context != null && !isNetworkAvailable(context)) {
                context.getString(R.string.err_no_internet_retry)
            } else {
                SecureLogger.sanitize(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: context?.getString(R.string.github_network_fail)
                        ?: "Failed to connect to GitHub"
                )
            }
            Result.failure(GitHubApiException(0, msg, isTransient = true))
        }
    }

    private fun userMessageOrNull(mapped: String, code: Int): String =
        mapped.ifBlank { "HTTP $code" }

    fun validateFileForUpload(
        fileName: String,
        fileSizeBytes: Long,
        context: Context? = null
    ): Result<Unit> {
        val max2Gb = 2147483648L // 2 GiB
        if (fileSizeBytes > max2Gb) {
            return Result.failure(
                Exception(context?.getString(R.string.github_file_over_2gb) ?: "File exceeds 2GB limit.")
            )
        }
        val ext = fileName.substringAfterLast(".", "").lowercase()
        val allowedExtensions = setOf("apk", "obb", "zip", "xapk", "apks", "png", "jpg", "jpeg")
        if (ext.isNotBlank() && ext !in allowedExtensions) {
            return Result.failure(
                Exception(
                    context?.getString(R.string.github_unsupported_ext, ext)
                        ?: "Unsupported file type ($ext)"
                )
            )
        }
        return Result.success(Unit)
    }

    private fun executeCreateRelease(
        owner: String,
        repo: String,
        token: String,
        appId: String,
        appName: String,
        userEmail: String?,
        context: Context
    ): Result<Pair<Long, String>> {
        val timestamp = System.currentTimeMillis()
        val cleanAppId = appId.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "app" }
        val tagName = "app-$cleanAppId-$timestamp-${Random.nextInt(100, 999)}"

        val createUrl = "https://api.github.com/repos/$owner/$repo/releases"
        val payload = JSONObject().apply {
            put("tag_name", tagName)
            put("name", appName.ifBlank { "App Release $timestamp" })
            put("draft", false)
            put("prerelease", false)
        }

        val request = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val json = JSONObject(body)
                val releaseId = json.getLong("id")
                val uploadUrl = json.getString("upload_url").substringBefore("{")
                Result.success(Pair(releaseId, uploadUrl))
            } else {
                val mapped = GitHubTokenProvider.mapGitHubHttpError(context, response.code, userEmail)
                SecureLogger.e(TAG, "GH_REL_${response.code}", "Create release failed (HTTP ${response.code})")
                Result.failure(
                    GitHubApiException(
                        httpCode = response.code,
                        message = mapped,
                        isTransient = response.code >= 500 || response.code == 429
                    )
                )
            }
        }
    }

    suspend fun deleteRelease(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        token: String,
        releaseId: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()
            if (cleanToken.isBlank() || releaseId <= 0) return@withContext

            val deleteUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/$releaseId"
            val request = Request.Builder()
                .url(deleteUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .delete()
                .build()

            client.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }

    fun sanitizeFileName(originalName: String): String {
        val base = originalName.substringBeforeLast(".")
        val ext = originalName.substringAfterLast(".", "apk")
        val cleanBase = base.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "app" }
        val cleanExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { "apk" }
        return "${cleanBase}.$cleanExt"
    }

    private fun buildRenamedAssetFileName(originalName: String): String {
        val base = originalName.substringBeforeLast(".")
        val ext = originalName.substringAfterLast(".", "apk")
        val cleanBase = base.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "app" }
        val cleanExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { "apk" }
        val suffix = Random.nextInt(1000, 99999)
        return "${cleanBase}_$suffix.$cleanExt"
    }

    suspend fun uploadAppRelease(
        context: Context,
        userEmail: String?,
        appId: String,
        appName: String,
        uri: Uri,
        originalFileName: String,
        fileSizeBytes: Long,
        resolveToken: suspend (forceRefresh: Boolean) -> Result<String>,
        onPhase: (GitHubUploadPhase) -> Unit
    ): Result<GitHubUploadResult> = withContext(Dispatchers.IO) {
        val validation = validateFileForUpload(originalFileName, fileSizeBytes, context)
        if (validation.isFailure) {
            return@withContext Result.failure(
                validation.exceptionOrNull() ?: Exception(context.getString(R.string.github_invalid_file))
            )
        }

        val tokenResult = resolveToken(false)
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: Exception(context.getString(R.string.err_gh_token_missing_admin))
            )
        }
        var activeToken = tokenResult.getOrThrow()
        var hasRefetchedOn401 = false

        val cleanOwner = DEFAULT_OWNER
        val cleanRepo = DEFAULT_REPO

        // Create Release with 401 refetch & transient retry
        var releaseId = -1L
        var uploadBaseUrl = ""
        var relAttempt = 1
        val maxAttempts = 3
        var relLastError: Throwable? = null

        while (relAttempt <= maxAttempts) {
            if (!waitForNetworkIfOffline(context) { onPhase(GitHubUploadPhase.WaitingForNetwork) }) {
                relLastError = GitHubApiException(0, context.getString(R.string.err_no_internet_retry), isTransient = true)
                break
            }

            try {
                val res = executeCreateRelease(cleanOwner, cleanRepo, activeToken, appId, appName, userEmail, context)
                if (res.isSuccess) {
                    val pair = res.getOrThrow()
                    releaseId = pair.first
                    uploadBaseUrl = pair.second
                    break
                } else {
                    val err = res.exceptionOrNull()
                    if (err is GitHubApiException && err.httpCode == 401 && !hasRefetchedOn401) {
                        hasRefetchedOn401 = true
                        val refreshed = resolveToken(true)
                        if (refreshed.isSuccess) {
                            activeToken = refreshed.getOrThrow()
                            continue
                        } else {
                            return@withContext Result.failure(
                                refreshed.exceptionOrNull() ?: err
                            )
                        }
                    }
                    relLastError = err
                    if (err is GitHubApiException && !err.isTransient) {
                        break
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "GH_REL_EXC", "Create release attempt $relAttempt failed", e)
                relLastError = GitHubApiException(
                    httpCode = 0,
                    message = if (!isNetworkAvailable(context)) {
                        context.getString(R.string.err_no_internet_retry)
                    } else {
                        context.getString(R.string.github_upload_interrupted, SecureLogger.sanitize(e.localizedMessage ?: "Network error"))
                    },
                    isTransient = true
                )
            }

            if (relAttempt < maxAttempts) {
                relAttempt++
                onPhase(GitHubUploadPhase.Retrying(relAttempt, maxAttempts))
                delay(2000L * (relAttempt - 1))
            } else {
                break
            }
        }

        if (releaseId <= 0L || uploadBaseUrl.isBlank()) {
            return@withContext Result.failure(
                relLastError ?: Exception(context.getString(R.string.github_create_release_fail))
            )
        }

        val contentResolver = context.contentResolver
        val totalBytes = if (fileSizeBytes > 0) fileSizeBytes else {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }

        var currentFileName = sanitizeFileName(originalFileName)
        var transientAttempt = 1
        var rename422Count = 0
        var lastError: Throwable? = null

        while (transientAttempt <= maxAttempts && rename422Count <= 3) {
            if (!waitForNetworkIfOffline(context) { onPhase(GitHubUploadPhase.WaitingForNetwork) }) {
                lastError = GitHubApiException(0, context.getString(R.string.err_no_internet_retry), isTransient = true)
                break
            }

            val uploadTargetUrl = "$uploadBaseUrl?name=$currentFileName"
            val requestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IOException(context.getString(R.string.github_open_file_fail))

                    inputStream.use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesWritten = 0L
                        var bytesRead: Int

                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                            onPhase(GitHubUploadPhase.Progress(bytesWritten, totalBytes))
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadTargetUrl)
                .header("Authorization", "Bearer $activeToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/octet-stream")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val respBody = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> {
                            val json = JSONObject(respBody)
                            val downloadUrl = json.getString("browser_download_url")
                            val size = json.optLong("size", totalBytes)
                            return@withContext Result.success(
                                GitHubUploadResult(
                                    downloadUrl = downloadUrl,
                                    sizeBytes = size,
                                    fileName = currentFileName,
                                    releaseId = releaseId,
                                    sizeMB = formatSize(size)
                                )
                            )
                        }
                        response.code == 401 -> {
                            if (!hasRefetchedOn401) {
                                hasRefetchedOn401 = true
                                val refreshed = resolveToken(true)
                                if (refreshed.isSuccess) {
                                    activeToken = refreshed.getOrThrow()
                                    return@use
                                } else {
                                    lastError = refreshed.exceptionOrNull()
                                        ?: GitHubApiException(401, GitHubTokenProvider.mapGitHubHttpError(context, 401, userEmail))
                                    deleteRelease(cleanOwner, cleanRepo, activeToken, releaseId)
                                    return@withContext Result.failure(lastError!!)
                                }
                            } else {
                                val mapped = GitHubTokenProvider.mapGitHubHttpError(context, 401, userEmail)
                                deleteRelease(cleanOwner, cleanRepo, activeToken, releaseId)
                                return@withContext Result.failure(GitHubApiException(401, mapped))
                            }
                        }
                        response.code == 422 -> {
                            rename422Count++
                            currentFileName = buildRenamedAssetFileName(originalFileName)
                            SecureLogger.w(TAG, "GH_422_RENAME", "Asset 422 collision; retrying with renamed filename")
                            return@use
                        }
                        response.code >= 500 || response.code == 429 -> {
                            lastError = GitHubApiException(
                                httpCode = response.code,
                                message = parseErrorMessage(response.code, respBody, context),
                                isTransient = true
                            )
                        }
                        else -> {
                            val mapped = GitHubTokenProvider.mapGitHubHttpError(
                                context,
                                response.code,
                                userEmail,
                                parseErrorMessage(response.code, respBody, context)
                            )
                            deleteRelease(cleanOwner, cleanRepo, activeToken, releaseId)
                            return@withContext Result.failure(GitHubApiException(response.code, mapped))
                        }
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "GH_UPLOAD_EXC", "Upload asset attempt $transientAttempt interrupted", e)
                val isNet = e is UnknownHostException || e is SocketTimeoutException || e is IOException
                val userMsg = if (!isNetworkAvailable(context)) {
                    context.getString(R.string.err_no_internet_retry)
                } else {
                    context.getString(
                        R.string.github_upload_interrupted,
                        SecureLogger.sanitize(e.localizedMessage ?: "Network error")
                    )
                }
                lastError = GitHubApiException(0, userMsg, isTransient = isNet)
            }

            if (hasRefetchedOn401 && lastError == null && rename422Count == 0) {
                // Retry immediately after 401 refetch
                continue
            }
            if (rename422Count in 1..3 && lastError == null) {
                continue
            }

            if (transientAttempt < maxAttempts) {
                transientAttempt++
                onPhase(GitHubUploadPhase.Retrying(transientAttempt, maxAttempts))
                delay(2500L * (transientAttempt - 1))
            } else {
                break
            }
        }

        deleteRelease(cleanOwner, cleanRepo, activeToken, releaseId)
        Result.failure(lastError ?: Exception(context.getString(R.string.github_upload_asset_fail)))
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    fun parseErrorMessage(code: Int, body: String, context: Context? = null): String {
        return when (code) {
            401 -> context?.getString(R.string.github_err_401) ?: "401: Invalid or expired GitHub Token"
            403 -> context?.getString(R.string.github_err_403) ?: "403: No write permission or rate limit exceeded"
            404 -> context?.getString(R.string.github_err_404) ?: "404: Repository owner or name not found"
            422 -> context?.getString(R.string.github_err_422) ?: "422: Duplicate asset name or invalid file"
            else -> {
                val serverMsg = try {
                    JSONObject(body).optString("message", "")
                } catch (_: Exception) {
                    ""
                }
                if (serverMsg.isNotBlank()) {
                    context?.getString(R.string.github_err_server, code, SecureLogger.sanitize(serverMsg))
                        ?: "GitHub error $code: ${SecureLogger.sanitize(serverMsg)}"
                } else {
                    context?.getString(R.string.github_err_generic, code)
                        ?: "GitHub request failed (HTTP $code)"
                }
            }
        }
    }
}
