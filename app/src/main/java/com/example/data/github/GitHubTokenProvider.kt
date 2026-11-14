package com.example.data.github

import android.content.Context
import com.example.R
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GitHubTokenStatusSnapshot(
    val isConfigured: Boolean? = null,
    val updatedAtIso: String? = null,
    val formattedUpdatedAt: String? = null,
    val isExpiringSoon: Boolean = false,
    val isLoading: Boolean = false,
    val statusError: String? = null
)

class GitHubTokenException(
    val userMessage: String,
    val isMissing: Boolean = false,
    val isSessionExpired: Boolean = false,
    val isRlsDenied: Boolean = false
) : Exception(userMessage)

/**
 * Strictly in-memory GitHub token provider.
 * Never persists the GitHub token to SharedPreferences, Room, files, Logcat, errors, or clipboard.
 */
object GitHubTokenProvider {

    private const val TAG = "GitHubTokenProvider"
    private const val PREFS_NON_SECRET_TEST_META = "prime_gh_non_secret_meta"
    private const val KEY_LAST_SUCCESSFUL_TEST_MS = "last_successful_test_ms"
    private const val SIXTY_DAYS_MS = 60L * 24L * 60L * 60L * 1000L

    private val mutex = Mutex()

    @Volatile
    private var inMemoryToken: String? = null

    private val _status = MutableStateFlow(GitHubTokenStatusSnapshot())
    val status: StateFlow<GitHubTokenStatusSnapshot> = _status.asStateFlow()

    fun hasCachedTokenInMemory(): Boolean = !inMemoryToken.isNullOrBlank()

    fun clear() {
        inMemoryToken = null
        _status.value = GitHubTokenStatusSnapshot()
    }

    suspend fun refreshStatusMetadata(context: Context): GitHubTokenStatusSnapshot {
        _status.value = _status.value.copy(isLoading = true, statusError = null)
        val res = SupabaseClient.fetchGitHubTokenStatusMetadata()
        val snapshot = if (res.success) {
            val formatted = formatIsoDate(res.updatedAt)
            val expiring = res.isConfigured && isExpiringSoon(context, res.updatedAt)
            if (!res.isConfigured) {
                inMemoryToken = null
            }
            GitHubTokenStatusSnapshot(
                isConfigured = res.isConfigured,
                updatedAtIso = res.updatedAt,
                formattedUpdatedAt = formatted,
                isExpiringSoon = expiring,
                isLoading = false,
                statusError = null
            )
        } else {
            val errMsg = when {
                res.isSessionExpired -> context.getString(R.string.err_session_expired_relogin)
                res.isRlsDenied -> context.getString(R.string.err_rls_not_admin)
                else -> SecureLogger.sanitize(res.errorMessage.ifBlank { context.getString(R.string.github_network_fail) })
            }
            GitHubTokenStatusSnapshot(
                isConfigured = _status.value.isConfigured,
                updatedAtIso = _status.value.updatedAtIso,
                formattedUpdatedAt = _status.value.formattedUpdatedAt,
                isExpiringSoon = _status.value.isExpiringSoon,
                isLoading = false,
                statusError = errMsg
            )
        }
        _status.value = snapshot
        return snapshot
    }

    suspend fun getToken(
        context: Context,
        userEmail: String?,
        forceRefresh: Boolean = false
    ): Result<String> = mutex.withLock {
        if (!forceRefresh) {
            val cached = inMemoryToken
            if (!cached.isNullOrBlank()) {
                return@withLock Result.success(cached)
            }
        }

        val res = SupabaseClient.fetchGitHubTokenSecret()
        if (!res.success) {
            if (res.isSessionExpired) {
                clear()
                return@withLock Result.failure(
                    GitHubTokenException(
                        userMessage = context.getString(R.string.err_session_expired_relogin),
                        isSessionExpired = true
                    )
                )
            }
            if (res.isRlsDenied) {
                return@withLock Result.failure(
                    GitHubTokenException(
                        userMessage = context.getString(R.string.err_rls_not_admin),
                        isRlsDenied = true
                    )
                )
            }
            val msg = SecureLogger.sanitize(res.errorMessage).ifBlank {
                context.getString(R.string.err_no_internet_retry)
            }
            return@withLock Result.failure(GitHubTokenException(userMessage = msg))
        }

        val token = res.value?.trim().orEmpty()
        if (token.isBlank()) {
            inMemoryToken = null
            _status.value = GitHubTokenStatusSnapshot(
                isConfigured = false,
                updatedAtIso = null,
                formattedUpdatedAt = null,
                isExpiringSoon = false,
                isLoading = false,
                statusError = null
            )
            val missingMsg = if (SupabaseClient.isOwner(userEmail)) {
                context.getString(R.string.err_gh_token_missing_owner)
            } else {
                context.getString(R.string.err_gh_token_missing_admin)
            }
            return@withLock Result.failure(GitHubTokenException(userMessage = missingMsg, isMissing = true))
        }

        inMemoryToken = token
        _status.value = GitHubTokenStatusSnapshot(
            isConfigured = true,
            updatedAtIso = res.updatedAt,
            formattedUpdatedAt = formatIsoDate(res.updatedAt),
            isExpiringSoon = isExpiringSoon(context, res.updatedAt),
            isLoading = false,
            statusError = null
        )
        Result.success(token)
    }

    suspend fun invalidateAndRefetch(context: Context, userEmail: String?): Result<String> {
        inMemoryToken = null
        SecureLogger.w(TAG, "GH_TOKEN_INVALIDATED", "Cleared in-memory GitHub token after 401 and refetching from Supabase")
        return getToken(context, userEmail, forceRefresh = true)
    }

    fun markSavedInMemory(context: Context, newToken: String) {
        val clean = newToken.trim()
        inMemoryToken = clean.ifBlank { null }
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        recordSuccessfulTestTimestamp(context)
        _status.value = GitHubTokenStatusSnapshot(
            isConfigured = clean.isNotBlank(),
            updatedAtIso = nowIso,
            formattedUpdatedAt = formatIsoDate(nowIso),
            isExpiringSoon = false,
            isLoading = false,
            statusError = null
        )
    }

    fun markDeleted(context: Context) {
        inMemoryToken = null
        context.getSharedPreferences(PREFS_NON_SECRET_TEST_META, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SUCCESSFUL_TEST_MS)
            .apply()
        _status.value = GitHubTokenStatusSnapshot(
            isConfigured = false,
            updatedAtIso = null,
            formattedUpdatedAt = null,
            isExpiringSoon = false,
            isLoading = false,
            statusError = null
        )
    }

    fun recordSuccessfulTestTimestamp(context: Context) {
        context.getSharedPreferences(PREFS_NON_SECRET_TEST_META, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SUCCESSFUL_TEST_MS, System.currentTimeMillis())
            .apply()
        val cur = _status.value
        _status.value = cur.copy(isExpiringSoon = false)
    }

    fun isExpiringSoon(context: Context, updatedAtIso: String?): Boolean {
        val lastTestMs = context.getSharedPreferences(PREFS_NON_SECRET_TEST_META, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SUCCESSFUL_TEST_MS, 0L)
        val updatedAtMs = parseIsoMillis(updatedAtIso)
        val referenceMs = maxOf(lastTestMs, updatedAtMs)
        if (referenceMs <= 0L) return false
        return (System.currentTimeMillis() - referenceMs) > SIXTY_DAYS_MS
    }

    fun mapGitHubHttpError(context: Context, httpCode: Int, userEmail: String?, fallback: String? = null): String {
        return when (httpCode) {
            401 -> if (SupabaseClient.isOwner(userEmail)) {
                context.getString(R.string.err_gh_401_owner)
            } else {
                context.getString(R.string.err_gh_401_admin)
            }
            403 -> context.getString(R.string.err_gh_403)
            404 -> context.getString(R.string.err_gh_404, GitHubStorageHelper.DEFAULT_OWNER, GitHubStorageHelper.DEFAULT_REPO)
            else -> SecureLogger.sanitize(fallback?.takeIf { it.isNotBlank() } ?: context.getString(R.string.github_err_generic, httpCode))
        }
    }

    private fun parseIsoMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val clean = iso.trim()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun formatIsoDate(iso: String?): String? {
        val ms = parseIsoMillis(iso)
        if (ms <= 0L) return iso?.takeIf { it.isNotBlank() }?.substringBefore("T")
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }
}
