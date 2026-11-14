package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.AppItem
import com.example.data.model.StoreCategories
import com.example.data.model.StoreUpdateInfo
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object SupabaseClient {

    private const val TAG = "SupabaseClient"

    const val SUPABASE_URL = "https://koloemxbxrenwqiajijo.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_Ol48kUBezAiwHSYhutTWbQ_ghTohVo6"
    const val BUCKET = "store-files"
    const val GITHUB_TOKEN_SECRET_KEY = "github_token"

    const val OWNER_EMAIL = "zaim9002@gmail.com"
    val ADMIN_EMAILS = listOf("zaim9002@gmail.com", "toprsidar2@gmail.com", "gjhh611@gmail.com")

    private const val REFRESH_BUFFER_SECONDS = 300L

    fun isOwner(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return email.trim().lowercase() == OWNER_EMAIL.lowercase()
    }

    fun isAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        return ADMIN_EMAILS.any { it.lowercase() == clean }
    }

    private val client: OkHttpClient by lazy {
        SecureLogger.attachRedactedLogging(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
        ).build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private var appContext: Context? = null
    private var securePrefs: SharedPreferences? = null
    private val tokenMutex = Mutex()

    var onSessionExpiredListener: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        getEncryptedPrefs()
        // Scrub any legacy unencrypted token remnants from older versions
        appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.edit()
            ?.remove("ps_token")
            ?.remove("ps_rt")
            ?.remove("ps_expires_at")
            ?.apply()
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

    private fun extractJwtExpSeconds(jwt: String): Long {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return 0L
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val payloadJson = JSONObject(String(payloadBytes, Charsets.UTF_8))
            payloadJson.optLong("exp", 0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun saveSession(email: String, accessToken: String, refreshToken: String, expiresAt: Long) {
        val resolvedExp = if (expiresAt > 0L) expiresAt else extractJwtExpSeconds(accessToken)
        getEncryptedPrefs()?.edit()
            ?.putString("user_email", email.trim())
            ?.putString("access_token", accessToken)
            ?.putString("refresh_token", refreshToken)
            ?.putLong("expires_at", resolvedExp)
            ?.apply()

        appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.edit()
            ?.putString("ps_email", email.trim())
            ?.remove("ps_token")
            ?.remove("ps_rt")
            ?.remove("ps_expires_at")
            ?.apply()
    }

    fun clearSession() {
        getEncryptedPrefs()?.edit()?.clear()?.apply()
        appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)?.edit()
            ?.remove("ps_email")
            ?.remove("ps_token")
            ?.remove("ps_rt")
            ?.remove("ps_expires_at")
            ?.apply()
    }

    fun getSavedUserEmail(): String? {
        return getEncryptedPrefs()?.getString("user_email", null)?.ifBlank { null }
            ?: appContext?.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)
                ?.getString("ps_email", null)?.ifBlank { null }
    }

    fun hasStoredSession(): Boolean {
        val prefs = getEncryptedPrefs() ?: return false
        val at = prefs.getString("access_token", null)
        val rt = prefs.getString("refresh_token", null)
        return !at.isNullOrBlank() || !rt.isNullOrBlank()
    }

    fun isTokenExpiredError(code: Int, text: String): Boolean {
        if (code == 401) return true
        val lower = text.lowercase()
        return lower.contains("\"exp\" claim") ||
            lower.contains("exp claim") ||
            lower.contains("jwt expired") ||
            lower.contains("token is expired") ||
            lower.contains("invalid claim") ||
            lower.contains("invalid jwt") ||
            lower.contains("session_expired") ||
            lower.contains("unauthorized")
    }

    fun isRlsPermissionError(code: Int, text: String): Boolean {
        val lower = text.lowercase()
        return code == 403 ||
            lower.contains("row-level security") ||
            lower.contains("42501") ||
            lower.contains("insufficient_privilege")
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

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful && respBody.isNotBlank()) {
                    val json = JSONObject(respBody)
                    val newAccessToken = json.getString("access_token")
                    val newRefreshToken = json.optString("refresh_token", refreshToken)
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                    val userObj = json.optJSONObject("user")
                    val email = userObj?.optString("email")?.ifBlank { null } ?: getSavedUserEmail() ?: ""

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

                    SecureLogger.e(TAG, "AUTH_REFRESH_${response.code}", "Token refresh failed (HTTP ${response.code})")
                    RefreshResponse(
                        success = false,
                        isTerminalFailure = isTerminal,
                        errorMessage = "HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "AUTH_REFRESH_NET", "Token refresh network failure", e)
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
            var expiresAt = prefs?.getLong("expires_at", 0L) ?: 0L
            if (expiresAt == 0L && accessToken != null) {
                expiresAt = extractJwtExpSeconds(accessToken)
            }
            val nowSec = System.currentTimeMillis() / 1000L

            if (!forceRefresh && accessToken != null && expiresAt > 0L && (expiresAt - nowSec) >= REFRESH_BUFFER_SECONDS) {
                return@withContext accessToken
            }

            if (refreshToken.isNullOrBlank()) {
                if (forceRefresh || (accessToken != null && expiresAt > 0L && (expiresAt - nowSec) < 0L)) {
                    clearSession()
                    withContext(Dispatchers.Main) {
                        onSessionExpiredListener?.invoke()
                    }
                    return@withContext null
                }
                return@withContext accessToken
            }

            val result = refreshAccessTokenInternal(refreshToken)
            if (result.success && !result.accessToken.isNullOrBlank()) {
                return@withContext result.accessToken
            } else {
                if (result.isTerminalFailure) {
                    clearSession()
                    withContext(Dispatchers.Main) {
                        onSessionExpiredListener?.invoke()
                    }
                    return@withContext null
                }
                return@withContext if (!forceRefresh && accessToken != null && (expiresAt - nowSec) > 0L) accessToken else null
            }
        }
    }

    suspend fun getValidUserToken(forceRefresh: Boolean = false): String? {
        val token = getValidToken(forceRefresh) ?: return null
        if (token == SUPABASE_KEY || token.isBlank()) return null
        return token
    }

    suspend fun checkAuthUserSession(): Result<String> = withContext(Dispatchers.IO) {
        var token = getValidUserToken(forceRefresh = false)
            ?: return@withContext Result.failure(
                Exception(appContext?.getString(R.string.err_session_expired_relogin) ?: "Session expired")
            )

        fun executeCheck(authToken: String): Triple<Boolean, Int, String> {
            val req = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/user")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $authToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                return Triple(resp.isSuccessful, resp.code, resp.body?.string().orEmpty())
            }
        }

        return@withContext try {
            var (ok, code, body) = executeCheck(token)
            if (!ok && isTokenExpiredError(code, body)) {
                val fresh = getValidUserToken(forceRefresh = true)
                if (!fresh.isNullOrBlank()) {
                    token = fresh
                    val retry = executeCheck(token)
                    ok = retry.first
                    code = retry.second
                    body = retry.third
                }
            }
            if (ok && body.isNotBlank()) {
                val email = JSONObject(body).optString("email", getSavedUserEmail().orEmpty())
                Result.success(email)
            } else {
                SecureLogger.e(TAG, "AUTH_USER_$code", "User session validation failed")
                Result.failure(
                    Exception(appContext?.getString(R.string.err_session_expired_relogin) ?: "Session expired")
                )
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "AUTH_USER_ERR", "Session validation error", e)
            Result.failure(e)
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

    data class SecretFetchResult(
        val success: Boolean,
        val value: String? = null,
        val updatedAt: String? = null,
        val httpCode: Int = 0,
        val errorCode: String = "",
        val errorMessage: String = "",
        val isSessionExpired: Boolean = false,
        val isRlsDenied: Boolean = false
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

            client.newCall(request).execute().use { response ->
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
                    SecureLogger.w(TAG, "SIGN_IN_${response.code}", "Sign in rejected")
                    val errJson = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = errJson?.optString("msg")?.ifBlank { null }
                        ?: errJson?.optString("message")?.ifBlank { null }
                        ?: errJson?.optString("error_description")?.ifBlank { null }
                        ?: appContext?.getString(R.string.toast_admin_wrong_creds)
                        ?: "Invalid login credentials"
                    AuthResponse(success = false, errorMessage = msg)
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "SIGN_IN_NET", "Sign in network error", e)
            AuthResponse(
                success = false,
                errorMessage = appContext?.getString(R.string.github_connection_error, e.localizedMessage ?: "")
                    ?: "Connection error: ${e.localizedMessage}"
            )
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

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(respBody)
                    val token = json.optString("access_token", "")
                    val rt = json.optString("refresh_token", "")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
                    val userObj = json.optJSONObject("user")
                    val userEmail = userObj?.optString("email", email) ?: email

                    if (token.isNotBlank()) {
                        saveSession(userEmail, token, rt, expiresAt)
                    }

                    AuthResponse(
                        success = true,
                        email = userEmail,
                        accessToken = token,
                        refreshToken = rt
                    )
                } else {
                    val errJson = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = errJson?.optString("msg")?.ifBlank { null }
                        ?: errJson?.optString("message")?.ifBlank { null }
                        ?: appContext?.getString(R.string.error_unexpected)
                        ?: "Failed to create account"
                    AuthResponse(success = false, errorMessage = msg)
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "SIGN_UP_NET", "Sign up network error", e)
            AuthResponse(
                success = false,
                errorMessage = appContext?.getString(R.string.github_connection_error, e.localizedMessage ?: "")
                    ?: "Connection error: ${e.localizedMessage}"
            )
        }
    }

    // =========================================================================
    // Central app_secrets Table (key = "github_token")
    // =========================================================================

    private fun rawFetchSecret(token: String, key: String): SecretFetchResult {
        return try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/app_secrets?key=eq.$key&select=value,updated_at")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val arr = try { JSONArray(respBody) } catch (_: Exception) { JSONArray() }
                    if (arr.length() == 0) {
                        SecretFetchResult(success = true, value = null, updatedAt = null, httpCode = response.code)
                    } else {
                        val obj = arr.getJSONObject(0)
                        val value = obj.optString("value", "").trim().ifBlank { null }
                        val updatedAt = obj.optString("updated_at", "").trim().ifBlank { null }
                        SecretFetchResult(success = true, value = value, updatedAt = updatedAt, httpCode = response.code)
                    }
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    val code = json?.optString("code", "${response.code}").orEmpty()
                    val expired = isTokenExpiredError(response.code, "$msg $respBody")
                    val rlsDenied = isRlsPermissionError(response.code, "$msg $respBody")
                    SecureLogger.e(TAG, "SECRET_GET_${response.code}", "Failed to read secret '$key': $msg")
                    SecretFetchResult(
                        success = false,
                        httpCode = response.code,
                        errorCode = code,
                        errorMessage = SecureLogger.sanitize(msg),
                        isSessionExpired = expired,
                        isRlsDenied = rlsDenied
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "SECRET_GET_EXC", "Network error reading secret '$key'", e)
            SecretFetchResult(
                success = false,
                httpCode = 0,
                errorCode = "NETWORK",
                errorMessage = SecureLogger.sanitize(e.localizedMessage ?: e.message ?: "Network error")
            )
        }
    }

    suspend fun fetchGitHubTokenSecret(): SecretFetchResult = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken(forceRefresh = false)
        if (initialToken.isNullOrBlank()) {
            return@withContext SecretFetchResult(
                success = false,
                httpCode = 401,
                errorCode = "NO_SESSION",
                errorMessage = "Session expired",
                isSessionExpired = true
            )
        }

        var result = rawFetchSecret(initialToken, GITHUB_TOKEN_SECRET_KEY)
        if (!result.success && result.isSessionExpired) {
            val freshToken = getValidUserToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                result = rawFetchSecret(freshToken, GITHUB_TOKEN_SECRET_KEY)
            } else {
                return@withContext result.copy(isSessionExpired = true, errorCode = "SESSION_EXPIRED")
            }
        }
        result
    }

    data class SecretStatusResult(
        val success: Boolean,
        val isConfigured: Boolean = false,
        val updatedAt: String? = null,
        val httpCode: Int = 0,
        val errorMessage: String = "",
        val isSessionExpired: Boolean = false,
        val isRlsDenied: Boolean = false
    )

    private fun rawFetchSecretMetadata(token: String, key: String): SecretStatusResult {
        return try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/app_secrets?key=eq.$key&select=key,updated_at")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val arr = try { JSONArray(respBody) } catch (_: Exception) { JSONArray() }
                    if (arr.length() == 0) {
                        SecretStatusResult(success = true, isConfigured = false, updatedAt = null, httpCode = response.code)
                    } else {
                        val obj = arr.getJSONObject(0)
                        val updatedAt = obj.optString("updated_at", "").trim().ifBlank { null }
                        SecretStatusResult(success = true, isConfigured = true, updatedAt = updatedAt, httpCode = response.code)
                    }
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    SecretStatusResult(
                        success = false,
                        httpCode = response.code,
                        errorMessage = SecureLogger.sanitize(msg),
                        isSessionExpired = isTokenExpiredError(response.code, "$msg $respBody"),
                        isRlsDenied = isRlsPermissionError(response.code, "$msg $respBody")
                    )
                }
            }
        } catch (e: Exception) {
            SecretStatusResult(
                success = false,
                httpCode = 0,
                errorMessage = SecureLogger.sanitize(e.localizedMessage ?: "Network error")
            )
        }
    }

    suspend fun fetchGitHubTokenStatusMetadata(): SecretStatusResult = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken(forceRefresh = false)
        if (initialToken.isNullOrBlank()) {
            return@withContext SecretStatusResult(
                success = false,
                httpCode = 401,
                errorMessage = "Session expired",
                isSessionExpired = true
            )
        }
        var result = rawFetchSecretMetadata(initialToken, GITHUB_TOKEN_SECRET_KEY)
        if (!result.success && result.isSessionExpired) {
            val freshToken = getValidUserToken(forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                result = rawFetchSecretMetadata(freshToken, GITHUB_TOKEN_SECRET_KEY)
            } else {
                return@withContext result.copy(isSessionExpired = true)
            }
        }
        result
    }

    private fun currentIsoUtcTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun rawUpsertSecret(token: String, key: String, secretValue: String): SupabaseMutationResult {
        return try {
            val payload = JSONObject().apply {
                put("key", key)
                put("value", secretValue.trim())
                put("updated_at", currentIsoUtcTimestamp())
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/app_secrets")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    SupabaseMutationResult(success = true, httpCode = response.code)
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    val details = json?.optString("details", "").orEmpty()
                    val code = json?.optString("code", "${response.code}").orEmpty()
                    SecureLogger.e(TAG, "SECRET_UPSERT_${response.code}", "Upsert secret '$key' failed: $msg")
                    SupabaseMutationResult(
                        success = false,
                        message = SecureLogger.sanitize(msg),
                        details = SecureLogger.sanitize(details),
                        code = code,
                        httpCode = response.code
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "SECRET_UPSERT_EXC", "Upsert secret '$key' exception", e)
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.localizedMessage ?: "Connection failed"),
                code = "NETWORK",
                httpCode = 0
            )
        }
    }

    suspend fun upsertGitHubTokenSecret(tokenValue: String): SupabaseMutationResult = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken(forceRefresh = false)
        if (initialToken.isNullOrBlank()) {
            return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )
        }

        var res = rawUpsertSecret(initialToken, GITHUB_TOKEN_SECRET_KEY, tokenValue)
        if (!res.success && isTokenExpiredError(res.httpCode, "${res.message} ${res.details}")) {
            val fresh = getValidUserToken(forceRefresh = true)
            if (!fresh.isNullOrBlank()) {
                res = rawUpsertSecret(fresh, GITHUB_TOKEN_SECRET_KEY, tokenValue)
            }
        }
        res
    }

    private fun rawDeleteSecret(token: String, key: String): SupabaseMutationResult {
        return try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/app_secrets?key=eq.$key")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    SupabaseMutationResult(success = true, httpCode = response.code)
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    val code = json?.optString("code", "${response.code}").orEmpty()
                    SecureLogger.e(TAG, "SECRET_DEL_${response.code}", "Delete secret '$key' failed: $msg")
                    SupabaseMutationResult(
                        success = false,
                        message = SecureLogger.sanitize(msg),
                        code = code,
                        httpCode = response.code
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "SECRET_DEL_EXC", "Delete secret '$key' exception", e)
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.localizedMessage ?: "Connection failed"),
                code = "NETWORK",
                httpCode = 0
            )
        }
    }

    suspend fun deleteGitHubTokenSecret(): SupabaseMutationResult = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken(forceRefresh = false)
        if (initialToken.isNullOrBlank()) {
            return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )
        }

        var res = rawDeleteSecret(initialToken, GITHUB_TOKEN_SECRET_KEY)
        if (!res.success && isTokenExpiredError(res.httpCode, "${res.message} ${res.details}")) {
            val fresh = getValidUserToken(forceRefresh = true)
            if (!fresh.isNullOrBlank()) {
                res = rawDeleteSecret(fresh, GITHUB_TOKEN_SECRET_KEY)
            }
        }
        res
    }

    // Safe non-destructive RLS write permission check on apps table
    suspend fun checkAppsWritePermission(): SupabaseMutationResult = withContext(Dispatchers.IO) {
        var token = getValidUserToken(forceRefresh = false)
            ?: return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )

        try {
            val probeGetReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/apps?select=id,name&limit=1")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val (sampleId, sampleName) = client.newCall(probeGetReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext SupabaseMutationResult(
                        success = false,
                        message = "HTTP ${resp.code}",
                        code = "${resp.code}",
                        httpCode = resp.code
                    )
                }
                val arr = try { JSONArray(body) } catch (_: Exception) { JSONArray() }
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    obj.optString("id", "") to obj.optString("name", "")
                } else {
                    "" to ""
                }
            }

            if (sampleId.isBlank()) {
                return@withContext SupabaseMutationResult(success = true, httpCode = 200)
            }

            fun runPatch(authToken: String): SupabaseMutationResult {
                val payload = JSONObject().apply { put("name", sampleName) }
                val req = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/apps?id=eq.$sampleId")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $authToken")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val arr = try { JSONArray(respBody) } catch (_: Exception) { JSONArray() }
                        return if (arr.length() > 0) {
                            SupabaseMutationResult(success = true, httpCode = resp.code)
                        } else {
                            SupabaseMutationResult(
                                success = false,
                                message = "row-level security policy denied write",
                                code = "42501",
                                httpCode = 403
                            )
                        }
                    } else {
                        val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                        val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${resp.code}" }
                        val code = json?.optString("code", "${resp.code}").orEmpty()
                        return SupabaseMutationResult(
                            success = false,
                            message = SecureLogger.sanitize(msg),
                            code = code,
                            httpCode = resp.code
                        )
                    }
                }
            }

            var res = runPatch(token)
            if (!res.success && isTokenExpiredError(res.httpCode, "${res.message} ${res.details}")) {
                val fresh = getValidUserToken(forceRefresh = true)
                if (!fresh.isNullOrBlank()) {
                    res = runPatch(fresh)
                }
            }
            res
        } catch (e: Exception) {
            SecureLogger.e(TAG, "APPS_WRITE_CHECK_EXC", "Apps write permission check exception", e)
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.localizedMessage ?: "Network error"),
                code = "NETWORK",
                httpCode = 0
            )
        }
    }

    // =========================================================================
    // Catalog Parsing & Queries
    // =========================================================================

    fun parseAppItem(obj: JSONObject, i: Int = 0): AppItem {
        val id = obj.optString("id", "")
        val name = obj.optString("name", appContext?.getString(R.string.dl_service_default_app) ?: "App")
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
        val versionCode = obj.optLong("version_code", 1L)
        val changelog = obj.optString("changelog", "").ifBlank { null }
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
        val categoryTitle = appContext?.let { StoreCategories.getTitle(it, categoryStr) }
            ?: StoreCategories.find(categoryStr).titleEn
        val tags = mutableListOf(categoryTitle)
        if (isOpenSource) {
            tags.add(appContext?.getString(R.string.cat_open_source) ?: "FOSS")
        }
        if (supportedAbis.isNotBlank()) tags.add(supportedAbis.substringBefore("(").trim())

        return AppItem(
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
            iconEmoji = if (iconUrl.length <= 4 && !iconUrl.startsWith("http")) iconUrl else StoreCategories.getEmoji(categoryStr),
            accentColor = category.accentHex,
            bannerRes = null,
            descriptionAr = desc,
            descriptionEn = desc,
            featuresAr = featuresList,
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
            version = version,
            categoryKey = categoryStr,
            changelog = changelog,
            versionCode = versionCode
        )
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

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@withContext emptyList()

                val jsonArray = JSONArray(body)
                val result = mutableListOf<AppItem>()
                for (i in 0 until jsonArray.length()) {
                    result.add(parseAppItem(jsonArray.getJSONObject(i), i))
                }
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAppsPaged(
        limit: Int = 20,
        offset: Int = 0,
        categoryKey: String? = null
    ): List<AppItem> = withContext(Dispatchers.IO) {
        try {
            val token = getValidToken() ?: SUPABASE_KEY
            val catParam = if (!categoryKey.isNullOrBlank() && categoryKey != "all") {
                "&category=eq.$categoryKey"
            } else ""

            val url = "$SUPABASE_URL/rest/v1/apps?select=*&order=created_at.desc&limit=$limit&offset=$offset$catParam"
            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@withContext emptyList()

                val jsonArray = JSONArray(body)
                val result = mutableListOf<AppItem>()
                for (i in 0 until jsonArray.length()) {
                    result.add(parseAppItem(jsonArray.getJSONObject(i), offset + i))
                }
                result
            }
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

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    SupabaseMutationResult(success = true, httpCode = response.code)
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    val details = json?.optString("details", "").orEmpty()
                    val code = json?.optString("code", "${response.code}").orEmpty().ifBlank { "${response.code}" }
                    SecureLogger.e(TAG, "INSERT_APP_${response.code}", "Insert app failed: $msg")
                    SupabaseMutationResult(
                        success = false,
                        message = SecureLogger.sanitize(msg),
                        details = SecureLogger.sanitize(details),
                        code = code,
                        httpCode = response.code
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "INSERT_APP_EXC", "Insert app exception", e)
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.message ?: appContext?.getString(R.string.error_unexpected) ?: "Supabase connection failed"),
                details = "",
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
        val initialToken = getValidUserToken() ?: accessToken?.takeIf { it.isNotBlank() && it != SUPABASE_KEY }
        if (initialToken.isNullOrBlank()) {
            return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )
        }
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

        if (!result.success && isTokenExpiredError(result.httpCode, "${result.message} ${result.details}")) {
            val freshToken = getValidUserToken(forceRefresh = true)
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

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    SupabaseMutationResult(success = true, httpCode = response.code)
                } else {
                    val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                    val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP ${response.code}" }
                    val details = json?.optString("details", "").orEmpty()
                    val code = json?.optString("code", "${response.code}").orEmpty().ifBlank { "${response.code}" }
                    SecureLogger.e(TAG, "UPDATE_APP_${response.code}", "Update app failed: $msg")
                    SupabaseMutationResult(
                        success = false,
                        message = SecureLogger.sanitize(msg),
                        details = SecureLogger.sanitize(details),
                        code = code,
                        httpCode = response.code
                    )
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "UPDATE_APP_EXC", "Update app exception", e)
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.message ?: appContext?.getString(R.string.error_unexpected) ?: "Supabase connection failed"),
                details = "",
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
        val initialToken = getValidUserToken() ?: accessToken?.takeIf { it.isNotBlank() && it != SUPABASE_KEY }
        if (initialToken.isNullOrBlank()) {
            return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )
        }
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

        if (!result.success && isTokenExpiredError(result.httpCode, "${result.message} ${result.details}")) {
            val freshToken = getValidUserToken(forceRefresh = true)
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

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Triple(response.isSuccessful, response.code, body)
            }
        } catch (e: Exception) {
            Triple(false, 0, e.message.orEmpty())
        }
    }

    suspend fun deleteApp(accessToken: String?, id: String): Boolean = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken() ?: accessToken?.takeIf { it.isNotBlank() && it != SUPABASE_KEY }
            ?: return@withContext false
        val (success, code, body) = rawDeleteApp(initialToken, id)
        if (!success && isTokenExpiredError(code, body)) {
            val freshToken = getValidUserToken(forceRefresh = true)
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

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Triple(response.isSuccessful, response.code, body)
            }
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                if (array.length() == 0) return@withContext null
                parseAppItem(array.getJSONObject(0))
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchStoreUpdate(): StoreUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val token = getValidToken() ?: SUPABASE_KEY
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/store_update?id=eq.1")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@withContext null

                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) return@withContext null
                val obj = jsonArray.getJSONObject(0)
                StoreUpdateInfo(
                    id = obj.optInt("id", 1),
                    versionCode = obj.optInt("version_code", 1),
                    versionName = obj.optString("version_name", "1.0.0"),
                    notes = obj.optString("notes", ""),
                    apkUrl = obj.optString("apk_url", ""),
                    updatedAt = obj.optString("updated_at", null)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun rawUpdateStoreUpdate(
        token: String,
        versionCode: Int,
        versionName: String,
        notes: String,
        apkUrl: String
    ): SupabaseMutationResult {
        return try {
            val payload = JSONObject().apply {
                put("version_code", versionCode)
                put("version_name", versionName.trim())
                put("notes", notes.trim())
                put("apk_url", apkUrl.trim())
            }

            var request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/store_update?id=eq.1")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            var statusCode: Int
            var respBody: String
            var isSuccessful: Boolean
            client.newCall(request).execute().use { response ->
                statusCode = response.code
                respBody = response.body?.string().orEmpty()
                isSuccessful = response.isSuccessful
            }

            if (isSuccessful && (respBody == "[]" || respBody.isBlank())) {
                payload.put("id", 1)
                request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/store_update")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response2 ->
                    statusCode = response2.code
                    respBody = response2.body?.string().orEmpty()
                    isSuccessful = response2.isSuccessful
                }
            }

            if (isSuccessful) {
                SupabaseMutationResult(success = true, httpCode = statusCode)
            } else {
                val json = try { JSONObject(respBody) } catch (_: Exception) { null }
                val msg = json?.optString("message", respBody).orEmpty().ifBlank { "HTTP $statusCode" }
                val code = json?.optString("code", "$statusCode").orEmpty()
                SupabaseMutationResult(
                    success = false,
                    message = SecureLogger.sanitize(msg),
                    details = SecureLogger.sanitize(respBody),
                    code = code,
                    httpCode = statusCode
                )
            }
        } catch (e: Exception) {
            SupabaseMutationResult(
                success = false,
                message = SecureLogger.sanitize(e.localizedMessage ?: "Update error"),
                code = "EXCEPTION",
                httpCode = 0
            )
        }
    }

    suspend fun updateStoreUpdate(
        versionCode: Int,
        versionName: String,
        notes: String,
        apkUrl: String
    ): SupabaseMutationResult = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken()
            ?: return@withContext SupabaseMutationResult(
                success = false,
                message = "Session expired",
                code = "SESSION_EXPIRED",
                httpCode = 401
            )

        var res = rawUpdateStoreUpdate(initialToken, versionCode, versionName, notes, apkUrl)
        if (!res.success && isTokenExpiredError(res.httpCode, "${res.message} ${res.details}")) {
            val fresh = getValidUserToken(forceRefresh = true)
            if (!fresh.isNullOrBlank()) {
                res = rawUpdateStoreUpdate(fresh, versionCode, versionName, notes, apkUrl)
            }
        }
        res
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

            client.newCall(request).execute().use { response ->
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
                    SecureLogger.e(TAG, "STORAGE_UPLOAD_${response.code}", "Storage upload failed: $parsedMsg")
                    Result.failure(Exception("HTTP ${response.code}: ${SecureLogger.sanitize(parsedMsg)}"))
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "STORAGE_UPLOAD_EXC", "Storage upload exception", e)
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
        val initialToken = getValidUserToken() ?: accessToken?.takeIf { it.isNotBlank() && it != SUPABASE_KEY }
            ?: return@withContext Result.failure(Exception("HTTP 401: Session expired"))
        var attempt = rawUploadStorageFile(context, uri, path, mimeType, initialToken, onProgress)

        if (attempt.isFailure) {
            val err = attempt.exceptionOrNull()?.message.orEmpty()
            if (isTokenExpiredError(0, err)) {
                val freshToken = getValidUserToken(forceRefresh = true)
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

            client.newCall(request).execute().use { response ->
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
                    SecureLogger.e(TAG, "STORAGE_BYTES_${response.code}", "Storage bytes upload failed: $parsedMsg")
                    Result.failure(Exception("HTTP ${response.code}: ${SecureLogger.sanitize(parsedMsg)}"))
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "STORAGE_BYTES_EXC", "Storage bytes upload exception", e)
            Result.failure(e)
        }
    }

    suspend fun uploadStorageBytesDetailed(
        bytes: ByteArray,
        path: String,
        mimeType: String,
        accessToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val initialToken = getValidUserToken() ?: accessToken?.takeIf { it.isNotBlank() && it != SUPABASE_KEY }
            ?: return@withContext Result.failure(Exception("HTTP 401: Session expired"))
        var attempt = rawUploadStorageBytes(bytes, path, mimeType, initialToken)

        if (attempt.isFailure) {
            val err = attempt.exceptionOrNull()?.message.orEmpty()
            if (isTokenExpiredError(0, err)) {
                val freshToken = getValidUserToken(forceRefresh = true)
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
            "games" -> AppCategory.GAMES
            "apps" -> AppCategory.APPS
            "trending", "popular" -> AppCategory.POPULAR
            "entertainment" -> AppCategory.ENTERTAINMENT
            "sports" -> AppCategory.SPORTS
            "business" -> AppCategory.BUSINESS
            "education" -> AppCategory.EDUCATION
            "music", "music_audio" -> AppCategory.MUSIC
            "tools" -> AppCategory.TOOLS
            "productivity" -> AppCategory.PRODUCTIVITY
            "personalization" -> AppCategory.PERSONALIZATION
            "media" -> AppCategory.MEDIA
            "social", "communication" -> AppCategory.SOCIAL
            "action", "game_action" -> AppCategory.ACTION
            "rpg", "game_role_playing" -> AppCategory.RPG
            "simulation", "game_simulation" -> AppCategory.SIMULATION
            "emulators" -> AppCategory.EMULATORS
            "retro" -> AppCategory.RETRO
            "root_tools" -> AppCategory.ROOT_TOOLS
            "open_source" -> AppCategory.OPEN_SOURCE
            "video", "video_players_editors" -> AppCategory.VIDEO
            else -> AppCategory.APPS
        }
    }
}
