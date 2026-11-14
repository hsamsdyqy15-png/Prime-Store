package com.example.data.github

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GitHubConfig(
    val owner: String,
    val repo: String,
    val token: String
)

data class GitHubUploadResult(
    val downloadUrl: String,
    val sizeBytes: Long,
    val fileName: String,
    val releaseId: Long,
    val sizeMB: String
)

object GitHubStorageHelper {

    private const val PREFS_FILE = "prime_github_secure_prefs"
    private const val KEY_OWNER = "gh_owner"
    private const val KEY_REPO = "gh_repo"
    private const val KEY_TOKEN = "gh_token"

    const val DEFAULT_OWNER = "hsamsdyqy15-png"
    const val DEFAULT_REPO = "prime-store-files"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun loadConfig(context: Context): GitHubConfig {
        return try {
            val prefs = getEncryptedPrefs(context)
            val owner = prefs.getString(KEY_OWNER, null)?.ifBlank { null } ?: DEFAULT_OWNER
            val repo = prefs.getString(KEY_REPO, null)?.ifBlank { null } ?: DEFAULT_REPO
            val token = prefs.getString(KEY_TOKEN, "").orEmpty()
            GitHubConfig(owner = owner, repo = repo, token = token)
        } catch (_: Exception) {
            try {
                val fallback = context.getSharedPreferences("prime_github_fallback_prefs", Context.MODE_PRIVATE)
                val owner = fallback.getString(KEY_OWNER, null)?.ifBlank { null } ?: DEFAULT_OWNER
                val repo = fallback.getString(KEY_REPO, null)?.ifBlank { null } ?: DEFAULT_REPO
                val token = fallback.getString(KEY_TOKEN, "").orEmpty()
                GitHubConfig(owner = owner, repo = repo, token = token)
            } catch (_: Exception) {
                GitHubConfig(owner = DEFAULT_OWNER, repo = DEFAULT_REPO, token = "")
            }
        }
    }

    fun saveConfig(context: Context, owner: String, repo: String, token: String) {
        val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
        val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
        val cleanToken = token.trim()

        try {
            val prefs = getEncryptedPrefs(context)
            prefs.edit()
                .putString(KEY_OWNER, cleanOwner)
                .putString(KEY_REPO, cleanRepo)
                .putString(KEY_TOKEN, cleanToken)
                .apply()
        } catch (_: Exception) {
            val fallback = context.getSharedPreferences("prime_github_fallback_prefs", Context.MODE_PRIVATE)
            fallback.edit()
                .putString(KEY_OWNER, cleanOwner)
                .putString(KEY_REPO, cleanRepo)
                .putString(KEY_TOKEN, cleanToken)
                .apply()
        }
    }

    suspend fun testConnection(
        owner: String,
        repo: String,
        token: String,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(
                    Exception(context?.getString(R.string.github_token_missing) ?: "Please enter GitHub Token first")
                )
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$cleanOwner/$cleanRepo")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val repoFullName = json.optString("full_name", "$cleanOwner/$cleanRepo")
                Result.success(
                    context?.getString(R.string.github_repo_connected, repoFullName)
                        ?: "Connected to $repoFullName"
                )
            } else {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                Result.failure(Exception(parseErrorMessage(code, errBody, context)))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    e.message ?: context?.getString(R.string.github_network_fail)
                    ?: "Failed to connect to GitHub servers"
                )
            )
        }
    }

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

    suspend fun createRelease(
        owner: String,
        repo: String,
        token: String,
        appId: String,
        appName: String,
        context: Context? = null
    ): Result<Pair<Long, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(
                    Exception(context?.getString(R.string.github_token_missing) ?: "GitHub Token missing")
                )
            }

            val timestamp = System.currentTimeMillis()
            val cleanAppId = appId.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "app" }
            val tagName = "app-$cleanAppId-$timestamp"

            val createUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases"
            val payload = JSONObject().apply {
                put("tag_name", tagName)
                put("name", appName.ifBlank { "App Release $timestamp" })
                put("draft", false)
                put("prerelease", false)
            }

            val request = Request.Builder()
                .url(createUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val releaseId = json.getLong("id")
                val uploadUrl = json.getString("upload_url").substringBefore("{")
                Result.success(Pair(releaseId, uploadUrl))
            } else {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                Result.failure(Exception(parseErrorMessage(code, errBody, context)))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    e.message ?: context?.getString(R.string.github_create_release_fail)
                    ?: "Failed to create GitHub release"
                )
            )
        }
    }

    suspend fun deleteRelease(
        owner: String,
        repo: String,
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

            client.newCall(request).execute()
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

    suspend fun uploadAppRelease(
        context: Context,
        owner: String,
        repo: String,
        token: String,
        appId: String,
        appName: String,
        uri: Uri,
        originalFileName: String,
        fileSizeBytes: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<GitHubUploadResult> = withContext(Dispatchers.IO) {
        val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
        val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
        val cleanToken = token.trim()

        if (cleanToken.isBlank()) {
            return@withContext Result.failure(Exception(context.getString(R.string.github_token_missing)))
        }

        val validation = validateFileForUpload(originalFileName, fileSizeBytes, context)
        if (validation.isFailure) {
            return@withContext Result.failure(
                validation.exceptionOrNull() ?: Exception(context.getString(R.string.github_invalid_file))
            )
        }

        val releaseResult = createRelease(cleanOwner, cleanRepo, cleanToken, appId, appName, context)
        if (releaseResult.isFailure) {
            return@withContext Result.failure(
                releaseResult.exceptionOrNull() ?: Exception(context.getString(R.string.github_create_release_fail))
            )
        }

        val (releaseId, uploadBaseUrl) = releaseResult.getOrThrow()

        var currentFileName = sanitizeFileName(originalFileName)
        var attempt = 1
        var lastError: Exception? = null

        val contentResolver = context.contentResolver
        val totalBytes = if (fileSizeBytes > 0) fileSizeBytes else {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }

        while (attempt <= 3) {
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
                            onProgress(bytesWritten, totalBytes)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadTargetUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/octet-stream")
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val downloadUrl = json.getString("browser_download_url")
                    val size = json.optLong("size", totalBytes)
                    val sizeMB = formatSize(size)

                    return@withContext Result.success(
                        GitHubUploadResult(
                            downloadUrl = downloadUrl,
                            sizeBytes = size,
                            fileName = currentFileName,
                            releaseId = releaseId,
                            sizeMB = sizeMB
                        )
                    )
                } else if (response.code == 422) {
                    val base = originalFileName.substringBeforeLast(".")
                    val ext = originalFileName.substringAfterLast(".", "apk")
                    currentFileName = "${sanitizeFileName(base)}_$attempt.$ext"
                    attempt++
                    continue
                } else {
                    val code = response.code
                    val errBody = response.body?.string().orEmpty()
                    lastError = Exception(parseErrorMessage(code, errBody, context))
                    break
                }
            } catch (e: Exception) {
                lastError = Exception(
                    context.getString(R.string.github_upload_interrupted, e.message ?: "Network error")
                )
                break
            }
        }

        deleteRelease(cleanOwner, cleanRepo, cleanToken, releaseId)
        return@withContext Result.failure(
            lastError ?: Exception(context.getString(R.string.github_upload_asset_fail))
        )
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
                    context?.getString(R.string.github_err_server, code, serverMsg)
                        ?: "GitHub error $code: $serverMsg"
                } else {
                    context?.getString(R.string.github_err_generic, code)
                        ?: "GitHub request failed (HTTP $code)"
                }
            }
        }
    }
}
