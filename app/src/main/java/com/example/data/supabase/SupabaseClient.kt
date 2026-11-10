package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseClient {

    const val SUPABASE_URL = "https://koloemxbxrenwqiajijo.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_Ol48kUBezAiwHSYhutTWbQ_ghTohVo6"
    const val BUCKET = "store-files"

    val ADMIN_EMAILS = listOf("zaim9002@gmail.com", "toprsidar2@gmail.com", "gjhh611@gmail.com")

    fun isAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        return ADMIN_EMAILS.any { it.lowercase() == clean }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private var appContext: Context? = null
    private var securePrefs: SharedPreferences? = null
    private val tokenMutex = Mutex()

    var onSessionExpiredListener: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        getEncryptedPrefs()
    }

    private fun getEncryptedPrefs(): SharedPreferences? {
        if (securePrefs != null) return securePrefs
        val ctx = appContext ?: return null
        securePrefs = try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                "supabase_encrypted_session_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            ctx.getSharedPreferences("supabase_encrypted_session_store_fallback", Context.MODE_PRIVATE)
        }
        return securePrefs
    }

    fun saveSession(email: String, accessToken: String, refreshToken: String, expiresAt: Long) {
        getEncryptedPrefs()?.edit()
            ?.putString("user_email", email)
            ?.putString("access_token", accessToken)
            ?.putString("refresh_token", refreshToken)
            ?.putLong("expires_at", expiresAt)
            ?.apply()

        // Sync with legacy authPrefs so any old observers stay intact
        appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.edit()
            ?.putString("ps_email", email)
            ?.putString("ps_token", accessToken)
            ?.putString("ps_rt", refreshToken)
            ?.putLong("ps_expires_at", expiresAt)
            ?.apply()
    }

    fun clearSession() {
        getEncryptedPrefs()?.edit()?.clear()?.apply()
        appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.edit()
            ?.remove("ps_token")
            ?.remove("ps_rt")
            ?.remove("ps_expires_at")
            ?.apply()
    }

    fun getSavedUserEmail(): String? {
        return getEncryptedPrefs()?.getString("user_email", null)
            ?: appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.getString("ps_email", null)
    }

    fun isTokenExpiredError(code: Int, text: String): Boolean {
        if (code == 401) return true
        val lower = text.lowercase()
        return lower.contains("\"exp\" claim") ||
                lower.contains("exp claim") ||
                lower.contains("jwt expired") ||
                lower.contains("token is expired") ||
                lower.contains("invalid claim") ||
                lower.contains("unauthorized")
    }

    data class RefreshResponse(
        val success: Boolean,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val isTerminalFailure: Boolean = false,
        val errorMessage: String? = null
    )

    private suspend fun refreshAccessTokenInternal(refreshToken: String): RefreshResponse = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("refresh_token", refreshToken.trim())
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token")
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotBlank()) {
                val json = JSONObject(respBody)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                val userObj = json.optJSONObject("user")
                val email = userObj?.optString("email") ?: getSavedUserEmail() ?: ""

                saveSession(email, newAccessToken, newRefreshToken, expiresAt)
                RefreshResponse(
                    success = true,
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken
                )
            } else {
                val isTerminal = response.code in 400..403 ||
                        respBody.contains("invalid_grant", ignoreCase = true) ||
                        respBody.contains("expired", ignoreCase = true) ||
                        respBody.contains("not found", ignoreCase = true)

                RefreshResponse(
                    success = false,
                    isTerminalFailure = isTerminal,
                    errorMessage = "HTTP ${response.code}: $respBody"
                )
            }
        } catch (e: Exception) {
            RefreshResponse(
                success = false,
                isTerminalFailure = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun getValidToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            val prefs = getEncryptedPrefs()
            val accessToken = prefs?.getString("access_token", null)?.ifBlank { null }
            val refreshToken = prefs?.getString("refresh_token", null)?.ifBlank { null }
            val expiresAt = prefs?.getLong("expires_at", 0L) ?: 0L
            val nowSec = System.currentTimeMillis() / 1000L

            // If not forced, access_token exists, and remaining valid time >= 120s, return it immediately
            if (!forceRefresh && accessToken != null && (expiresAt - nowSec) >= 120) {
                return@withContext accessToken
            }

            // If we have no refresh token, return whatever access token is present (or null)
            if (refreshToken.isNullOrBlank()) {
                return@withContext accessToken
            }

            // Attempt token renewal via POST
            val result = refreshAccessTokenInternal(refreshToken)
            if (result.success && !result.accessToken.isNullOrBlank()) {
                return@withContext result.accessToken
            } else {
                if (result.isTerminalFailure) {
                    clearSession()
                    withContext(Dispatchers.Main) {
                        onSessionExpiredListener?.invoke()
                    }
                }
                return@withContext if (!forceRefresh) accessToken else null
            }
        }
    }

    data class AuthResponse(
        val success: Boolean,
        val email: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val errorMessage: String? = null
    )

    data class SupabaseMutationResult(
        val success: Boolean,
        val message: String = "",
        val details: String = "",
        val code: String = "",
        val httpCode: Int = 200
    )

    suspend fun signInWithPassword(email: String, pass: String): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val token = json.optString("access_token", "")
                val rt = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email", email) ?: email

                saveSession(userEmail, token, rt, expiresAt)

                AuthResponse(
                    success = true,
                    email = userEmail,
                    accessToken = token,
                    refreshToken = rt
                )
            } else {
                val errJson = try { JSONObject(respBody) } catch (_: Exception) { null }
                val msg = errJson?.optString("msg")
                    ?: errJson?.optString("message")
                    ?: errJson?.optString("error_description")
                    ?: "بيانات تسجيل الدخول غير صحيحة"
                AuthResponse(success = false, errorMessage = msg)
            }
        } catch (e: Exception) {
            AuthResponse(success = false, errorMessage = "تعذر الاتصال بالخادم: ${e.localizedMessage}")
        }
    }

    suspend fun signUp(email: String, pass: String): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/signup")
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val token = json.optString("access_token", "")
                val rt = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email", email) ?: email

                saveSession(userEmail, token, rt, expiresAt)

                AuthResponse(
                    success = true,
                    email = userEmail,
                    accessToken = token,
                    refreshToken = rt
                )
            } else {
                val errJson = try { JSONObject(respBody) } catch (_: Exception) { null }
                val msg = errJson?.optString("msg")
                    ?: errJson?.optString("message")
                    ?: "تعذر إنشاء الحساب"
                AuthResponse(success = false, errorMessage = msg)
            }
        } catch (e: Exception) {
            AuthResponse(success = false, errorMessage = "خطأ في الاتصال: ${e.localizedMessage}")
        }
    }

    suspend fun fetchApps(): List<AppItem> = withContext(Dispatchers.IO) {
        try {
            val token = getValidToken() ?: SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?select=*&order=created_at.desc")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return@withContext emptyList()

            val jsonArray = JSONArray(body)
            val result = mutableListOf<AppItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "تطبيق")
                val categoryStr = obj.optString("category", "apps").lowercase()
                val rating = obj.optDouble("rating", 4.5).toFloat()
                val downloads = obj.optString("downloads", "+100")
                val size = obj.optString("size", "25 MB")
                val desc = obj.optString("description", "")
                val iconUrl = obj.optString("icon_url", "")
                val fileUrl = obj.optString("file_url", "")
                val isOpenSource = obj.optBoolean("is_open_source", false)
                val sourceCodeUrl = obj.optString("source_code_url", "").ifBlank { null }
                val supportedAbis = obj.optString("supported_abis", "Universal (All Architectures)")
                val version = obj.optString("version", "1.0.0")
                val packageName = obj.optString("package_name", "").ifBlank { null }

                val featuresList = mutableListOf<String>()
                val featArray = obj.optJSONArray("features")
                if (featArray != null) {
                    for (f in 0 until featArray.length()) {
                        featuresList.add(featArray.getString(f))
                    }
                }

                val screenList = mutableListOf<String>()
                val screenArray = obj.optJSONArray("screenshots")
                if (screenArray != null) {
                    for (s in 0 until screenArray.length()) {
                        screenList.add(screenArray.getString(s))
                    }
                }

                val category = if (isOpenSource) AppCategory.OPEN_SOURCE else mapCategory(categoryStr)
                val tags = mutableListOf(category.titleAr)
                if (isOpenSource) tags.add("مفتوح المصدر")
                if (supportedAbis.isNotBlank()) tags.add(supportedAbis.substringBefore("(").trim())

                result.add(
                    AppItem(
                        id = id,
                        name = name,
                        nameAr = name,
                        category = category,
                        tags = tags,
                        rating = rating,
                        ratingCount = "2.5K",
                        downloadCount = downloads,
                        size = size,
                        sizeBytes = 25_000_000L,
                        iconEmoji = if (iconUrl.length <= 4 && !iconUrl.startsWith("http")) iconUrl else category.iconEmoji,
                        accentColor = category.accentHex,
                        bannerRes = null,
                        descriptionAr = desc.ifBlank { "تطبيق رائع متاح للتحميل المجاني." },
                        descriptionEn = desc,
                        featuresAr = featuresList.ifEmpty { listOf("تحديثات مستمرة", "دعم كامل للغة العربية") },
                        screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95),
                        isFeatured = i < 2,
                        isTrending = i % 2 == 0,
                        isNew = true,
                        iconUrl = iconUrl.ifBlank { null },
                        fileUrl = fileUrl.ifBlank { null },
                        screenshotUrls = screenList,
                        isOpenSource = isOpenSource,
                        sourceCodeUrl = sourceCodeUrl,
                        supportedAbis = supportedAbis,
                        packageName = packageName,
                        version = version
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseDownloads(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val s = raw.trim().uppercase().removePrefix("+").removePrefix("~").replace(",", "")
        if (s.isBlank()) return 0L
        return try {
            when {
                s.endsWith("K") -> ((s.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000).toLong()
                s.endsWith("M") -> ((s.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000000).toLong()
                s.endsWith("B") -> ((s.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000000000).toLong()
                else -> s.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun rawInsertApp(
        token: String,
        name: String,
        category: String,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        architecture: String,
        version: String,
        packageName: String,
        downloads: String
    ): SupabaseMutationResult {
        return try {
            val downloadsLong = parseDownloads(downloads)
            val cleanPkg = packageName.ifBlank {
                val clean = name.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (clean.isNotBlank()) "com.primestore.$clean" else "com.primestore.app"
            }

            // Exactly the 13 columns present in Supabase apps table
            val payload = JSONObject().apply {
                put("name", name.trim())
                put("category", category.trim())
                put("rating", rating)
                put("downloads", downloadsLong)
                put("size", size.trim())
                put("description", description.trim())
                put("features", JSONArray(features))
                put("icon_url", iconUrl.trim())
                put("file_url", fileUrl.trim())
                put("screenshots", JSONArray(screenshots))
                put("architecture", architecture.ifBlank { "Universal" }.trim())
                put("version", version.ifBlank { "1.0.0" }.trim())
                put("package_name", cleanPkg.trim())
            }

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                SupabaseMutationResult(success = true, httpCode = response.code)
            } else {
                val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                val details = json?.optString("details", "").orEmpty()
                val code = json?.optString("code", "${response.code}").orEmpty().ifBlank { "${response.code}" }
                SupabaseMutationResult(
                    success = false,
                    message = msg,
                    details = details,
                    code = code,
                    httpCode = response.code
                )
            }
        } catch (e: Exception) {
            SupabaseMutationResult(
                success = false,
                message = e.message ?: "فشل الاتصال بخادم Supabase",
                details = e.stackTraceToString().take(200),
                code = "EXCEPTION",
                httpCode = 0
            )
        }
    }

    suspend fun insertApp(
        accessToken: String? = null,
        name: String,
        category: String,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        architecture: String = "Universal",
        version: String = "1.0.0",
        packageName: String = "",
        downloads: String = "0"
    ): SupabaseMutationResult = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: accessToken ?: SUPABASE_KEY
        var result = rawInsertApp(
            token = initialToken,
            name = name,
            category = category,
            rating = rating,
            size = size,
            description = description,
            features = features,
            iconUrl = iconUrl,
            fileUrl = fileUrl,
            screenshots = screenshots,
            architecture = architecture,
            version = version,
            packageName = packageName,
            downloads = downloads
        )

        // Automatic retry once if 401 or "exp" claim expired token
        if (!result.success && isTokenExpiredError(result.httpCode, "${result.message} ${result.details}")) {
            val freshToken = getValidToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                result = rawInsertApp(
                    token = freshToken,
                    name = name,
                    category = category,
                    rating = rating,
                    size = size,
                    description = description,
                    features = features,
                    iconUrl = iconUrl,
                    fileUrl = fileUrl,
                    screenshots = screenshots,
                    architecture = architecture,
                    version = version,
                    packageName = packageName,
                    downloads = downloads
                )
            }
        }
        result
    }

    private fun rawUpdateApp(
        token: String,
        id: String,
        name: String,
        category: String,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        architecture: String,
        version: String,
        packageName: String,
        downloads: String
    ): SupabaseMutationResult {
        return try {
            val downloadsLong = parseDownloads(downloads)
            val cleanPkg = packageName.ifBlank {
                val clean = name.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (clean.isNotBlank()) "com.primestore.$clean" else "com.primestore.app"
            }

            val payload = JSONObject().apply {
                put("name", name.trim())
                put("category", category.trim())
                put("rating", rating)
                put("downloads", downloadsLong)
                put("size", size.trim())
                put("description", description.trim())
                put("features", JSONArray(features))
                put("icon_url", iconUrl.trim())
                put("file_url", fileUrl.trim())
                put("screenshots", JSONArray(screenshots))
                put("architecture", architecture.ifBlank { "Universal" }.trim())
                put("version", version.ifBlank { "1.0.0" }.trim())
                put("package_name", cleanPkg.trim())
            }

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$id")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                SupabaseMutationResult(success = true, httpCode = response.code)
            } else {
                val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                val details = json?.optString("details", "").orEmpty()
                val code = json?.optString("code", "${response.code}").orEmpty().ifBlank { "${response.code}" }
                SupabaseMutationResult(
                    success = false,
                    message = msg,
                    details = details,
                    code = code,
                    httpCode = response.code
                )
            }
        } catch (e: Exception) {
            SupabaseMutationResult(
                success = false,
                message = e.message ?: "فشل الاتصال بخادم Supabase",
                details = e.stackTraceToString().take(200),
                code = "EXCEPTION",
                httpCode = 0
            )
        }
    }

    suspend fun updateApp(
        accessToken: String? = null,
        id: String,
        name: String,
        category: String,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        architecture: String = "Universal",
        version: String = "1.0.0",
        packageName: String = "",
        downloads: String = "0"
    ): SupabaseMutationResult = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: accessToken ?: SUPABASE_KEY
        var result = rawUpdateApp(
            token = initialToken,
            id = id,
            name = name,
            category = category,
            rating = rating,
            size = size,
            description = description,
            features = features,
            iconUrl = iconUrl,
            fileUrl = fileUrl,
            screenshots = screenshots,
            architecture = architecture,
            version = version,
            packageName = packageName,
            downloads = downloads
        )

        // Automatic retry once if 401 or "exp" claim expired token
        if (!result.success && isTokenExpiredError(result.httpCode, "${result.message} ${result.details}")) {
            val freshToken = getValidToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                result = rawUpdateApp(
                    token = freshToken,
                    id = id,
                    name = name,
                    category = category,
                    rating = rating,
                    size = size,
                    description = description,
                    features = features,
                    iconUrl = iconUrl,
                    fileUrl = fileUrl,
                    screenshots = screenshots,
                    architecture = architecture,
                    version = version,
                    packageName = packageName,
                    downloads = downloads
                )
            }
        }
        result
    }

    private fun rawDeleteApp(token: String, id: String): Triple<Boolean, Int, String> {
        return try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$id")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            Triple(response.isSuccessful, response.code, body)
        } catch (e: Exception) {
            Triple(false, 0, e.message.orEmpty())
        }
    }

    suspend fun deleteApp(accessToken: String?, id: String): Boolean = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: accessToken ?: SUPABASE_KEY
        val (success, code, body) = rawDeleteApp(initialToken, id)
        if (!success && isTokenExpiredError(code, body)) {
            val freshToken = getValidToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                return@withContext rawDeleteApp(freshToken, id).first
            }
        }
        success
    }

    private fun rawIncrementDownloads(token: String, appId: String): Triple<Boolean, Int, String> {
        return try {
            val payload = JSONObject().apply {
                val num = appId.toLongOrNull()
                if (num != null) put("p_app_id", num) else put("p_app_id", appId)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/increment_downloads")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            Triple(response.isSuccessful, response.code, body)
        } catch (e: Exception) {
            Triple(false, 0, e.message.orEmpty())
        }
    }

    suspend fun incrementDownloads(appId: String): Boolean = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: SUPABASE_KEY
        val (success, code, body) = rawIncrementDownloads(initialToken, appId)
        if (!success && isTokenExpiredError(code, body)) {
            val freshToken = getValidToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                return@withContext rawIncrementDownloads(freshToken, appId).first
            }
        }
        success
    }

    fun getPublicUrl(path: String): String {
        val cleanPath = path.trimStart('/')
        return "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$cleanPath"
    }

    fun isValidDownloadLink(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.trim()
        val lower = clean.lowercase()
        if (lower.contains("mediafire.com/file") ||
            lower.contains("drive.google.com") ||
            lower.contains("mega.nz") ||
            lower.contains("dropbox.com/s/")
        ) {
            return false
        }
        val pathWithoutQuery = clean.substringBefore("?")
        return lower.contains("supabase.co/storage/v1/object/public") ||
                pathWithoutQuery.endsWith(".apk", ignoreCase = true) ||
                (lower.contains("github.com") && lower.contains("/releases/"))
    }

    suspend fun fetchAppById(appId: String): AppItem? = withContext(Dispatchers.IO) {
        try {
            val token = getValidToken() ?: SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$appId")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            val array = JSONArray(body)
            if (array.length() == 0) return@withContext null
            val obj = array.getJSONObject(0)
            val id = obj.optString("id", appId)
            val name = obj.optString("name", "")
            val categoryStr = obj.optString("category", "apps")
            val rating = obj.optDouble("rating", 4.5).toFloat()
            val size = obj.optString("size", "25 MB")
            val desc = obj.optString("description", "")
            val iconUrl = obj.optString("icon_url", "")
            val fileUrl = obj.optString("file_url", "")
            val isOpenSource = obj.optBoolean("is_open_source", false)
            val sourceCodeUrl = obj.optString("source_code_url", "").ifBlank { null }
            val supportedAbis = obj.optString("supported_abis", "Universal (All Architectures)")
            val version = obj.optString("version", "1.0.0")
            val packageName = obj.optString("package_name", "").ifBlank { null }

            val featuresList = mutableListOf<String>()
            val featArray = obj.optJSONArray("features")
            if (featArray != null) {
                for (f in 0 until featArray.length()) {
                    featuresList.add(featArray.getString(f))
                }
            }

            val screenList = mutableListOf<String>()
            val screenArray = obj.optJSONArray("screenshots")
            if (screenArray != null) {
                for (s in 0 until screenArray.length()) {
                    screenList.add(screenArray.getString(s))
                }
            }

            val category = if (isOpenSource) AppCategory.OPEN_SOURCE else mapCategory(categoryStr)
            val tags = mutableListOf(category.titleAr)
            if (isOpenSource) tags.add("مفتوح المصدر")

            AppItem(
                id = id,
                name = name,
                nameAr = name,
                category = category,
                tags = tags,
                rating = rating,
                ratingCount = "2.5K",
                downloadCount = "+1K",
                size = size,
                sizeBytes = 25_000_000L,
                iconEmoji = if (iconUrl.length <= 4 && !iconUrl.startsWith("http")) iconUrl else category.iconEmoji,
                accentColor = category.accentHex,
                bannerRes = null,
                descriptionAr = desc.ifBlank { "تطبيق رائع متاح للتحميل المجاني." },
                descriptionEn = desc,
                featuresAr = featuresList.ifEmpty { listOf("تحديثات مستمرة", "دعم كامل للغة العربية") },
                screenshotsColors = listOf(0xFF2E1065, 0xFF4C1D95),
                isFeatured = false,
                isTrending = false,
                isNew = true,
                iconUrl = iconUrl.ifBlank { null },
                fileUrl = fileUrl.ifBlank { null },
                screenshotUrls = screenList,
                isOpenSource = isOpenSource,
                sourceCodeUrl = sourceCodeUrl,
                supportedAbis = supportedAbis,
                packageName = packageName,
                version = version
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun rawUploadStorageFile(
        context: Context,
        uri: Uri,
        path: String,
        mimeType: String,
        token: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<String> {
        return try {
            val cleanPath = path.trimStart('/')
            val contentResolver = context.contentResolver
            val totalBytes = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            written += read
                            onProgress(written, totalBytes)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/$BUCKET/$cleanPath")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", mimeType)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(getPublicUrl(cleanPath))
            } else {
                val errorBody = response.body?.string().orEmpty()
                val parsedMsg = try {
                    val json = JSONObject(errorBody)
                    json.optString("message", json.optString("error", errorBody))
                } catch (_: Exception) {
                    errorBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                }
                Result.failure(Exception("HTTP ${response.code}: $parsedMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadStorageFileDetailed(
        context: Context,
        uri: Uri,
        path: String,
        mimeType: String,
        accessToken: String?,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: accessToken ?: SUPABASE_KEY
        var attempt = rawUploadStorageFile(context, uri, path, mimeType, initialToken, onProgress)

        if (attempt.isFailure) {
            val err = attempt.exceptionOrNull()?.message.orEmpty()
            if (isTokenExpiredError(0, err)) {
                val freshToken = getValidToken(forceRefresh = true)
                if (!freshToken.isNullOrBlank()) {
                    attempt = rawUploadStorageFile(context, uri, path, mimeType, freshToken, onProgress)
                }
            }
        }
        attempt
    }

    private fun rawUploadStorageBytes(
        bytes: ByteArray,
        path: String,
        mimeType: String,
        token: String
    ): Result<String> {
        return try {
            val cleanPath = path.trimStart('/')
            val request = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/$BUCKET/$cleanPath")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", mimeType)
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(getPublicUrl(cleanPath))
            } else {
                val errorBody = response.body?.string().orEmpty()
                val parsedMsg = try {
                    val json = JSONObject(errorBody)
                    json.optString("message", json.optString("error", errorBody))
                } catch (_: Exception) {
                    errorBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                }
                Result.failure(Exception("HTTP ${response.code}: $parsedMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadStorageBytesDetailed(
        bytes: ByteArray,
        path: String,
        mimeType: String,
        accessToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val initialToken = getValidToken() ?: accessToken ?: SUPABASE_KEY
        var attempt = rawUploadStorageBytes(bytes, path, mimeType, initialToken)

        if (attempt.isFailure) {
            val err = attempt.exceptionOrNull()?.message.orEmpty()
            if (isTokenExpiredError(0, err)) {
                val freshToken = getValidToken(forceRefresh = true)
                if (!freshToken.isNullOrBlank()) {
                    attempt = rawUploadStorageBytes(bytes, path, mimeType, freshToken)
                }
            }
        }
        attempt
    }

    suspend fun uploadStorageFile(
        context: Context,
        uri: Uri,
        path: String,
        mimeType: String,
        accessToken: String?,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String? = uploadStorageFileDetailed(context, uri, path, mimeType, accessToken, onProgress).getOrNull()

    suspend fun uploadStorageBytes(
        bytes: ByteArray,
        path: String,
        mimeType: String,
        accessToken: String?
    ): String? = uploadStorageBytesDetailed(bytes, path, mimeType, accessToken).getOrNull()

    private fun mapCategory(cat: String): AppCategory {
        return when (cat.lowercase()) {
            "games", "ألعاب" -> AppCategory.GAMES
            "apps", "تطبيقات" -> AppCategory.APPS
            "trending", "popular", "الأكثر تحميلاً" -> AppCategory.POPULAR
            "entertainment", "الترفيه" -> AppCategory.ENTERTAINMENT
            "sports", "الرياضة" -> AppCategory.SPORTS
            "business", "الإنتاجية والعمل" -> AppCategory.BUSINESS
            "education", "التعليم" -> AppCategory.EDUCATION
            "music", "موسيقى وصوتيات" -> AppCategory.MUSIC
            "tools", "أدوات النظام" -> AppCategory.TOOLS
            "productivity", "الإنتاجية" -> AppCategory.PRODUCTIVITY
            "personalization", "التخصيص" -> AppCategory.PERSONALIZATION
            "media", "الوسائط" -> AppCategory.MEDIA
            "social", "التواصل الاجتماعي" -> AppCategory.SOCIAL
            "action", "أكشن ومغامرات" -> AppCategory.ACTION
            "rpg", "تقمص الأدوار rpg" -> AppCategory.RPG
            "simulation", "محاكاة" -> AppCategory.SIMULATION
            "emulators", "المحاكيات" -> AppCategory.EMULATORS
            "retro", "ريترو وكلاسيك" -> AppCategory.RETRO
            "root_tools", "أدوات الروت" -> AppCategory.ROOT_TOOLS
            "open_source", "مفتوح المصدر" -> AppCategory.OPEN_SOURCE
            "video", "فيديو" -> AppCategory.VIDEO
            else -> AppCategory.APPS
        }
    }
}
