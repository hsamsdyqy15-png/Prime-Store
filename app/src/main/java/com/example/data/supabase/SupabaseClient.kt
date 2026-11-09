package com.example.data.supabase

import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    data class AuthResponse(
        val success: Boolean,
        val email: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val errorMessage: String? = null
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
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email", email) ?: email
                AuthResponse(
                    success = true,
                    email = userEmail,
                    accessToken = token,
                    refreshToken = rt
                )
            } else {
                val errJson = try { JSONObject(respBody) } catch (e: Exception) { null }
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
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email", email) ?: email
                AuthResponse(
                    success = true,
                    email = userEmail,
                    accessToken = token,
                    refreshToken = rt
                )
            } else {
                val errJson = try { JSONObject(respBody) } catch (e: Exception) { null }
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
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?select=*&order=created_at.desc")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
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
                        supportedAbis = supportedAbis
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun insertApp(
        accessToken: String?,
        name: String,
        category: String,
        rating: Float,
        size: String,
        description: String,
        features: List<String>,
        iconUrl: String,
        fileUrl: String,
        screenshots: List<String>,
        isOpenSource: Boolean = false,
        sourceCodeUrl: String? = null,
        supportedAbis: String = "Universal (All Architectures)"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("name", name)
                put("category", category)
                put("rating", rating)
                put("size", size)
                put("description", description)
                put("features", JSONArray(features))
                put("icon_url", iconUrl)
                put("file_url", fileUrl)
                put("screenshots", JSONArray(screenshots))
                put("is_open_source", isOpenSource)
                put("source_code_url", sourceCodeUrl ?: "")
                put("supported_abis", supportedAbis)
            }

            val token = if (!accessToken.isNullOrBlank()) accessToken else SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateApp(
        accessToken: String?,
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
        isOpenSource: Boolean = false,
        sourceCodeUrl: String? = null,
        supportedAbis: String = "Universal (All Architectures)"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("name", name)
                put("category", category)
                put("rating", rating)
                put("size", size)
                put("description", description)
                put("features", JSONArray(features))
                put("icon_url", iconUrl)
                put("file_url", fileUrl)
                put("screenshots", JSONArray(screenshots))
                put("is_open_source", isOpenSource)
                put("source_code_url", sourceCodeUrl ?: "")
                put("supported_abis", supportedAbis)
            }

            val token = if (!accessToken.isNullOrBlank()) accessToken else SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$id")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteApp(accessToken: String?, id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = if (!accessToken.isNullOrBlank()) accessToken else SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$id")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun incrementDownloads(appId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                val num = appId.toLongOrNull()
                if (num != null) {
                    put("p_app_id", num)
                } else {
                    put("p_app_id", appId)
                }
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/increment_downloads")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
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
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?id=eq.$appId")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
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
                supportedAbis = supportedAbis
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadStorageFileDetailed(
        context: android.content.Context,
        uri: android.net.Uri,
        path: String,
        mimeType: String,
        accessToken: String?,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trimStart('/')
            val contentResolver = context.contentResolver
            val totalBytes = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

            val requestBody = object : okhttp3.RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: okio.BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(8192)
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

            val token = if (!accessToken.isNullOrBlank()) accessToken else SUPABASE_KEY
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
                } catch (e: Exception) {
                    errorBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                }
                Result.failure(Exception(parsedMsg))
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
        try {
            val cleanPath = path.trimStart('/')
            val token = if (!accessToken.isNullOrBlank()) accessToken else SUPABASE_KEY
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
                } catch (e: Exception) {
                    errorBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                }
                Result.failure(Exception(parsedMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadStorageFile(
        context: android.content.Context,
        uri: android.net.Uri,
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
