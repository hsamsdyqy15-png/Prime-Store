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
import java.util.concurrent.TimeUnit

data class GitHubConfig(
    val owner: String,
    val repo: String,
    val token: String
)

data class GitHubUploadResult(
    val downloadUrl: String,
    val sizeBytes: Long
)

object GitHubStorageHelper {

    private const val PREFS_FILE = "prime_github_secure_prefs"
    private const val KEY_OWNER = "gh_owner"
    private const val KEY_REPO = "gh_repo"
    private const val KEY_TOKEN = "gh_token"

    const val DEFAULT_OWNER = "hsamsdyqy15-png"
    const val DEFAULT_REPO = "prime-store-files"

    // OkHttpClient with zero timeouts (no limit) as requested for large APK uploads
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
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
        } catch (e: Exception) {
            GitHubConfig(owner = DEFAULT_OWNER, repo = DEFAULT_REPO, token = "")
        }
    }

    fun saveConfig(context: Context, owner: String, repo: String, token: String) {
        try {
            val prefs = getEncryptedPrefs(context)
            prefs.edit()
                .putString(KEY_OWNER, owner.trim().ifBlank { DEFAULT_OWNER })
                .putString(KEY_REPO, repo.trim().ifBlank { DEFAULT_REPO })
                .putString(KEY_TOKEN, token.trim())
                .apply()
        } catch (e: Exception) {
            // Silently handle or fallback to normal prefs if hardware keystore fails on specific devices
            val fallback = context.getSharedPreferences("prime_github_fallback_prefs", Context.MODE_PRIVATE)
            fallback.edit()
                .putString(KEY_OWNER, owner.trim().ifBlank { DEFAULT_OWNER })
                .putString(KEY_REPO, repo.trim().ifBlank { DEFAULT_REPO })
                .putString(KEY_TOKEN, token.trim())
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
                val errorMsg = parseErrorMessage(code, errBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "فشل الاتصال بالإنترنت أو بخوادم GitHub"))
        }
    }

    /**
     * Gets or creates the 'store-files' release and returns its cleaned upload_url
     */
    suspend fun getOrCreateReleaseUploadUrl(
        owner: String,
        repo: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            val cleanToken = token.trim()

            if (cleanToken.isBlank()) {
                return@withContext Result.failure(Exception("مفتاح GitHub Token غير محدد في إعدادات GitHub"))
            }

            // Step 1: Check existing release by tag 'store-files'
            val getUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/tags/store-files"
            val getRequest = Request.Builder()
                .url(getUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val getResponse = client.newCall(getRequest).execute()
            if (getResponse.isSuccessful) {
                val body = getResponse.body?.string().orEmpty()
                val json = JSONObject(body)
                val rawUploadUrl = json.getString("upload_url")
                val cleanUploadUrl = rawUploadUrl.substringBefore("{")
                return@withContext Result.success(cleanUploadUrl)
            }

            if (getResponse.code != 404) {
                val err = parseErrorMessage(getResponse.code, getResponse.body?.string().orEmpty())
                return@withContext Result.failure(Exception(err))
            }

            // Step 2: If 404, create the release
            val createUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases"
            val payload = JSONObject().apply {
                put("tag_name", "store-files")
                put("name", "Prime Store Files")
                put("draft", false)
                put("prerelease", false)
            }

            val postRequest = Request.Builder()
                .url(createUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
                .build()

            val postResponse = client.newCall(postRequest).execute()
            if (postResponse.isSuccessful) {
                val body = postResponse.body?.string().orEmpty()
                val json = JSONObject(body)
                val rawUploadUrl = json.getString("upload_url")
                val cleanUploadUrl = rawUploadUrl.substringBefore("{")
                Result.success(cleanUploadUrl)
            } else {
                val err = parseErrorMessage(postResponse.code, postResponse.body?.string().orEmpty())
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "خطأ أثناء تجهيز إصدار GitHub"))
        }
    }

    /**
     * Sanitizes file name: keeps only English letters, digits, _, and .
     */
    fun sanitizeFileName(originalName: String): String {
        val base = originalName.substringBeforeLast(".")
        val ext = originalName.substringAfterLast(".", "apk")
        val cleanBase = base.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "app" }
        val cleanExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { "apk" }
        return "${System.currentTimeMillis()}_${cleanBase}.$cleanExt"
    }

    /**
     * Streams APK file from Uri in chunks without loading entire file in memory
     */
    suspend fun uploadApkAsset(
        context: Context,
        uploadBaseUrl: String,
        token: String,
        uri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<GitHubUploadResult> = withContext(Dispatchers.IO) {
        try {
            val maxBytes = 2147483648L // 2 GiB
            if (fileSizeBytes > maxBytes) {
                return@withContext Result.failure(Exception("الحد الأقصى 2GB لكل ملف"))
            }

            val uniqueName = sanitizeFileName(fileName)
            val uploadTargetUrl = "$uploadBaseUrl?name=$uniqueName"

            val contentResolver = context.contentResolver
            val totalBytes = if (fileSizeBytes > 0) fileSizeBytes else {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }

            val requestBody = object : RequestBody() {
                override fun contentType() = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength() = totalBytes

                override fun writeTo(sink: BufferedSink) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw Exception("تعذر فتح ملف APK للقراءة")

                    inputStream.use { stream ->
                        val buffer = ByteArray(64 * 1024) // 64KB chunks
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
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/vnd.android.package-archive")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val downloadUrl = json.getString("browser_download_url")
                val size = json.optLong("size", totalBytes)
                Result.success(GitHubUploadResult(downloadUrl = downloadUrl, sizeBytes = size))
            } else {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                val errorMsg = parseErrorMessage(code, errBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "فشل رفع ملف APK إلى GitHub"))
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        val serverMessage = try {
            val json = JSONObject(body)
            json.optString("message", "")
        } catch (e: Exception) {
            ""
        }

        return when (code) {
            401 -> "401: المفتاح غير صالح (Bad credentials / Token invalid)"
            403 -> "403: ليس لديك صلاحية كتابة في هذا المستودع أو تم تجاوز حد الطلبات"
            404 -> "404: المستودع غير موجود أو الحساب غير صحيح"
            422 -> "422: اسم الملف مكرر أو لا يمكن معالجة الملف في GitHub Releases"
            else -> {
                if (serverMessage.isNotBlank()) "خطأ $code: $serverMessage"
                else "فشل طلب GitHub برمز الحالة: $code"
            }
        }
    }
}
