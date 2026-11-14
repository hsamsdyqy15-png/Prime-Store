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

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(message))
        }
    }

    fun w(tag: String, code: String, message: String) {
        Log.w(tag, "[$code] ${sanitize(message)}")
    }

    fun e(tag: String, code: String, message: String, throwable: Throwable? = null) {
        val safeMsg = "[$code] ${sanitize(message)}"
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
