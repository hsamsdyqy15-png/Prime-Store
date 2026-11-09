package com.example.data.github

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    // OkHttpClient with zero timeouts for large streaming chunk uploads (up to 2GB)
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

    /**
     * Test connection to GitHub API for owner/repo
     */
    suspend fun testConnection(owner: String, repo: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(Exception("يرجى إدخال مفتاح GitHub Token أولاً"))
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
                Result.success("تم الاتصال بنجاح بمستودع $repoFullName ✔")
            } else {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                Result.failure(Exception(parseErrorMessage(code, errBody)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "فشل الاتصال بالإنترنت أو بخوادم GitHub"))
        }
    }

    /**
     * Validates file size (max 2GB) and allowed extensions (apk, obb, zip, xapk, apks)
     */
    fun validateFileForUpload(fileName: String, fileSizeBytes: Long): Result<Unit> {
        val max2Gb = 2147483648L // 2 GiB
        if (fileSizeBytes > max2Gb) {
            return Result.failure(Exception("حجم الملف يتجاوز 2 جيجابايت، يرجى تقسيم الملف قبل الرفع"))
        }
        val ext = fileName.substringAfterLast(".", "").lowercase()
        val allowedExtensions = setOf("apk", "obb", "zip", "xapk", "apks", "png", "jpg", "jpeg")
        if (ext.isNotBlank() && ext !in allowedExtensions) {
            return Result.failure(Exception("نوع الملف غير مدعوم ($ext)، لا يمكن رفع إلا ملفات apk أو obb أو zip وما شابهها"))
        }
        return Result.success(Unit)
    }

    /**
     * Creates a new Release for a specific app:
     * POST https://api.github.com/repos/{owner}/{repo}/releases
     * body: { tag_name: "app-{appId}-{timestamp}", name: "{appName}", draft: false, prerelease: false }
     */
    suspend fun createRelease(
        owner: String,
        repo: String,
        token: String,
        appId: String,
        appName: String
    ): Result<Pair<Long, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifBlank { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifBlank { DEFAULT_REPO }
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(Exception("مفتاح GitHub Token غير محدد في إعدادات GitHub"))
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
                Result.failure(Exception(parseErrorMessage(code, errBody)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "فشل إنشاء إصدار GitHub"))
        }
    }

    /**
     * Deletes a release if upload was cancelled or interrupted
     * DELETE https://api.github.com/repos/{owner}/{repo}/releases/{release_id}
     */
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
            // Silently ignore cleanup error
        }
    }

    /**
     * Sanitizes file name: keeps only English letters, digits, _, -, and .
     */
    fun sanitizeFileName(originalName: String): String {
        val base = originalName.substringBeforeLast(".")
        val ext = originalName.substringAfterLast(".", "apk")
        val cleanBase = base.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "app" }
        val cleanExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { "apk" }
        return "${cleanBase}.$cleanExt"
    }

    /**
     * Full Release Upload workflow:
     * 1. Validates file size & type (< 2GB, apk/obb/zip)
     * 2. Creates dedicated release: POST /repos/{owner}/{repo}/releases
     * 3. Streams raw binary file directly without loading in memory or base64:
     *    POST https://uploads.github.com/repos/{owner}/{repo}/releases/{release_id}/assets?name={fileName}
     * 4. If status 422 (duplicate asset name), automatically retries with numbered suffix
     * 5. If network disconnects or upload fails, automatically deletes the incomplete release
     * 6. Returns browser_download_url and sizeMB
     */
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
            return@withContext Result.failure(Exception("مفتاح GitHub Token غير محدد في إعدادات GitHub"))
        }

        // 1. Validation
        val validation = validateFileForUpload(originalFileName, fileSizeBytes)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: Exception("ملف غير صالح"))
        }

        // 2. Create Release
        val releaseResult = createRelease(cleanOwner, cleanRepo, cleanToken, appId, appName)
        if (releaseResult.isFailure) {
            return@withContext Result.failure(releaseResult.exceptionOrNull() ?: Exception("فشل إنشاء إصدار GitHub"))
        }

        val (releaseId, uploadBaseUrl) = releaseResult.getOrThrow()

        // 3. Upload raw binary asset with real-time streaming progress
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
                        ?: throw IOException("تعذر فتح ملف التطبيق للقراءة من ذاكرة الجهاز")

                    inputStream.use { stream ->
                        val buffer = ByteArray(64 * 1024) // 64KB buffer
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
                    // 422: Duplicate asset name in this release - auto rename and retry
                    val base = originalFileName.substringBeforeLast(".")
                    val ext = originalFileName.substringAfterLast(".", "apk")
                    currentFileName = "${sanitizeFileName(base)}_$attempt.$ext"
                    attempt++
                    continue
                } else {
                    val code = response.code
                    val errBody = response.body?.string().orEmpty()
                    lastError = Exception(parseErrorMessage(code, errBody))
                    break
                }
            } catch (e: Exception) {
                lastError = Exception("انقطاع في الاتصال بالإنترنت أثناء الرفع (${e.message ?: "Network error"})")
                break
            }
        }

        // Automatic cleanup: if asset upload fails after creating release, delete the incomplete release
        deleteRelease(cleanOwner, cleanRepo, cleanToken, releaseId)
        return@withContext Result.failure(lastError ?: Exception("فشل رفع الملف كـ asset إلى إصدار GitHub"))
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

    fun parseErrorMessage(code: Int, body: String): String {
        return when (code) {
            401 -> "401: المفتاح غير صالح أو منتهي الصلاحية (Bad credentials / Token expired)"
            403 -> "403: لا توجد صلاحية كتابة في هذا المستودع، أو تم تجاوز حد الطلبات"
            404 -> "404: اسم المستودع أو المالك غير صحيح، تأكد من صحة إعدادات GitHub"
            422 -> "422: اسم الملف مكرر في نفس الـ Release أو الملف غير صالح"
            else -> {
                val serverMsg = try {
                    JSONObject(body).optString("message", "")
                } catch (_: Exception) {
                    ""
                }
                if (serverMsg.isNotBlank()) "خطأ $code من GitHub: $serverMsg"
                else "فشل طلب GitHub برمز الحالة: $code"
            }
        }
    }
}
