package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object SecureLogger {

    private val bearerRegex = Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*", RegexOption.IGNORE_CASE)
    private val githubPatRegex = Regex("(gh[pousr]_[A-Za-z0-9_]{10,}|github_pat_[A-Za-z0-9_]{10,})")
    private val jwtRegex = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*")
    private val authHeaderRegex = Regex("(Authorization|apikey|refresh_token|access_token)\\s*[:=]\\s*[^\\s,;]+", RegexOption.IGNORE_CASE)

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(bearerRegex, "Bearer [REDACTED]")
            .replace(githubPatRegex, "[REDACTED_GH_TOKEN]")
            .replace(jwtRegex, "[REDACTED_JWT]")
            .replace(authHeaderRegex, "$1=[REDACTED]")
    }

    data class DiagnosticLogEntry(
        val timestampMillis: Long,
        val level: String,
        val code: String,
        val message: String
    )

    private const val MAX_DIAGNOSTIC_LOGS = 50
    private val logLock = Any()
    private val recentErrorBuffer = ArrayDeque<DiagnosticLogEntry>(MAX_DIAGNOSTIC_LOGS)

    private fun recordError(level: String, code: String, message: String) {
        val clean = sanitize(message).take(240)
        synchronized(logLock) {
            if (recentErrorBuffer.size >= MAX_DIAGNOSTIC_LOGS) {
                recentErrorBuffer.removeFirst()
            }
            recentErrorBuffer.addLast(
                DiagnosticLogEntry(
                    timestampMillis = System.currentTimeMillis(),
                    level = level,
                    code = code,
                    message = clean
                )
            )
        }
    }

    fun getRecentErrors(): List<DiagnosticLogEntry> {
        synchronized(logLock) {
            return recentErrorBuffer.toList()
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(message))
        }
    }

    fun w(tag: String, code: String, message: String) {
        val safe = sanitize(message)
        recordError("WARN", code, safe)
        Log.w(tag, "[$code] $safe")
    }

    fun e(tag: String, code: String, message: String, throwable: Throwable? = null) {
        val safeMsg = "[$code] ${sanitize(message)}"
        val fullMsg = if (throwable != null) {
            "${sanitize(message)} (${sanitize(throwable.message)})"
        } else {
            sanitize(message)
        }
        recordError("ERROR", code, fullMsg)
        if (throwable != null) {
            Log.e(tag, "$safeMsg (${sanitize(throwable.message)})")
        } else {
            Log.e(tag, safeMsg)
        }
    }

    fun attachRedactedLogging(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (BuildConfig.DEBUG) {
            val interceptor = HttpLoggingInterceptor { msg ->
                Log.d("PrimeHttp", sanitize(msg))
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("apikey")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
                redactHeader("X-GitHub-Token")
            }
            builder.addInterceptor(interceptor)
        }
        return builder
    }
}
