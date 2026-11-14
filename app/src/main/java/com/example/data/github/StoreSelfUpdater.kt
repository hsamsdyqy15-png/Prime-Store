package com.example.data.github

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.installer.PackageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class StoreReleaseInfo(
    val tagName: String,
    val versionCode: Int,
    val versionName: String,
    val bodyNotes: String,
    val apkDownloadUrl: String,
    val assetSize: Long,
    val assetSizeFormatted: String
)

sealed class StoreUpdateCheckResult {
    data class UpdateAvailable(val info: StoreReleaseInfo) : StoreUpdateCheckResult()
    data object UpToDate : StoreUpdateCheckResult()
    data class Error(val message: String, val isRateLimit: Boolean = false) : StoreUpdateCheckResult()
}

object StoreSelfUpdater {
    const val STORE_GITHUB_OWNER = "hsamsdyqy15-png"
    const val STORE_GITHUB_REPO = "Prime-Store"

    private const val PREFS_NAME = "prime_store_self_update"
    private const val KEY_ETAG = "github_release_etag"
    private const val KEY_LAST_BODY = "github_release_body"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_KNOWN_VERSION_CODE = "last_known_version_code"

    fun onAppStarted(context: Context, onUpdatedNotification: (String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKnown = prefs.getInt(KEY_KNOWN_VERSION_CODE, -1)
        val currentCode = BuildConfig.VERSION_CODE

        if (lastKnown != -1 && currentCode > lastKnown) {
            onUpdatedNotification(
                context.getString(R.string.store_update_success_banner, BuildConfig.VERSION_NAME)
            )
        }
        prefs.edit().putInt(KEY_KNOWN_VERSION_CODE, currentCode).apply()
    }

    fun shouldCheckSilently(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        val sixHours = 6 * 60 * 60 * 1000L
        return (now - lastTime) >= sixHours
    }

    suspend fun checkForStoreUpdate(context: Context, force: Boolean = false): StoreUpdateCheckResult {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val apiUrl = "https://api.github.com/repos/$STORE_GITHUB_OWNER/$STORE_GITHUB_REPO/releases/latest"

            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "PrimeStoreApp/${BuildConfig.VERSION_NAME}")

                val cachedEtag = prefs.getString(KEY_ETAG, null)
                if (!force && !cachedEtag.isNullOrBlank()) {
                    conn.setRequestProperty("If-None-Match", cachedEtag)
                }

                val responseCode = conn.responseCode
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val cachedBody = prefs.getString(KEY_LAST_BODY, null)
                    if (!cachedBody.isNullOrBlank()) {
                        val parsed = parseReleaseJson(context, cachedBody)
                        if (parsed != null && parsed.versionCode > BuildConfig.VERSION_CODE) {
                            return@withContext StoreUpdateCheckResult.UpdateAvailable(parsed)
                        }
                    }
                    return@withContext StoreUpdateCheckResult.UpToDate
                }

                if (responseCode == 403) {
                    return@withContext StoreUpdateCheckResult.Error(
                        message = context.getString(R.string.github_rate_limit),
                        isRateLimit = true
                    )
                }

                if (responseCode == 404) {
                    return@withContext StoreUpdateCheckResult.Error(
                        context.getString(R.string.github_no_releases)
                    )
                }

                if (responseCode !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext StoreUpdateCheckResult.Error(
                        context.getString(R.string.github_check_http_error, responseCode, err)
                    )
                }

                val etag = conn.getHeaderField("ETag")
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }

                val editor = prefs.edit().putString(KEY_LAST_BODY, responseBody)
                if (!etag.isNullOrBlank()) {
                    editor.putString(KEY_ETAG, etag)
                }
                editor.apply()

                val release = parseReleaseJson(context, responseBody)
                    ?: return@withContext StoreUpdateCheckResult.Error(
                        context.getString(R.string.github_parse_error)
                    )

                if (release.versionCode > BuildConfig.VERSION_CODE) {
                    StoreUpdateCheckResult.UpdateAvailable(release)
                } else {
                    StoreUpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                StoreUpdateCheckResult.Error(
                    context.getString(
                        R.string.github_connection_error,
                        e.message ?: context.getString(R.string.error_unexpected)
                    )
                )
            }
        }
    }

    private fun parseReleaseJson(context: Context, jsonStr: String): StoreReleaseInfo? {
        return try {
            val root = JSONObject(jsonStr)
            val tagName = if (root.isNull("tag_name")) "" else root.optString("tag_name", "").trim()
            val rawBody = if (root.isNull("body")) "" else root.optString("body", "").trim()
            val bodyNotes = if (rawBody.isBlank() || rawBody.equals("null", ignoreCase = true)) {
                context.getString(R.string.whats_new_default)
            } else {
                rawBody
            }

            val buildCodeStr = if (tagName.contains("build-", ignoreCase = true)) {
                tagName.substringAfter("build-").takeWhile { it.isDigit() }
            } else {
                tagName.filter { it.isDigit() }
            }
            val versionCode = buildCodeStr.toIntOrNull() ?: 1
            val versionName = if (tagName.isNotBlank()) {
                tagName.removePrefix("v").removePrefix("build-")
            } else {
                "1.0.$versionCode"
            }

            val assets = root.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) return null

            StoreReleaseInfo(
                tagName = tagName,
                versionCode = versionCode,
                versionName = versionName,
                bodyNotes = bodyNotes,
                apkDownloadUrl = apkUrl,
                assetSize = apkSize,
                assetSizeFormatted = formatSize(apkSize)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadStoreUpdateApk(
        context: Context,
        info: StoreReleaseInfo,
        onProgress: (Float, String, String) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
                if (!dir.exists()) dir.mkdirs()
                val targetFile = File(dir, "Prime.Store.apk")

                val url = URL(info.apkDownloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "PrimeStoreApp/${BuildConfig.VERSION_NAME}")

                val totalLength = if (info.assetSize > 0) info.assetSize else conn.contentLength.toLong()
                val input = conn.inputStream
                val output = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    val progress = if (totalLength > 0) (totalBytesRead.toFloat() / totalLength).coerceIn(0f, 1f) else 0f
                    onProgress(progress, formatSize(totalBytesRead), formatSize(totalLength))
                }
                output.flush()
                output.close()
                input.close()

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    return@withContext Result.failure(
                        Exception(context.getString(R.string.github_download_failed))
                    )
                }

                val currentAppInfo = PackageHelper.getInstalledInfo(context, context.packageName)
                val downloadedArchive = PackageHelper.getArchiveInfo(context, targetFile)

                if (currentAppInfo != null && downloadedArchive != null) {
                    if (currentAppInfo.signatureSha256.isNotBlank() && downloadedArchive.signatureSha256.isNotBlank()) {
                        if (!currentAppInfo.signatureSha256.equals(downloadedArchive.signatureSha256, ignoreCase = true)) {
                            return@withContext Result.failure(
                                Exception(context.getString(R.string.github_sig_mismatch))
                            )
                        }
                    }
                }

                Result.success(targetFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
