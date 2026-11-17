package com.example.crash

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.data.repository.SettingsRepository
import com.example.data.supabase.SecureLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object CrashReporter {
    private const val CRASH_FILE_NAME = "crash_report.txt"
    private const val PREFS_NAME = "prime_store_crash_prefs"
    private const val KEY_UNREAD_CRASH = "has_unread_crash"
    private const val ENTRY_DELIMITER = "\n=== PRIME_CRASH_SEPARATOR ===\n"
    private const val MAX_REPORTS = 5

    private val installed = AtomicBoolean(false)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun installUncaughtHandler(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never throw inside uncaught exception handler
            } finally {
                val prev = previousHandler
                if (prev != null) {
                    prev.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    private fun crashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

    internal fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val report = formatCrashReport(context, thread, throwable)
        val file = crashFile(context)
        val existingEntries = readReportsList(file).toMutableList()
        existingEntries.add(0, report)
        val trimmed = existingEntries.take(MAX_REPORTS)
        file.parentFile?.mkdirs()
        file.writeText(trimmed.joinToString(ENTRY_DELIMITER), Charsets.UTF_8)

        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNREAD_CRASH, true)
                .commit()
        }
    }

    internal fun formatCrashReport(context: Context, thread: Thread, throwable: Throwable): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val effectiveLang = runCatching {
            SettingsRepository.getInstance(context).getEffectiveLanguageCode()
        }.getOrElse { Locale.getDefault().toLanguageTag() }
        val abis = runCatching { Build.SUPPORTED_ABIS.joinToString(", ") }.getOrDefault("unknown")
        val stack = runCatching { SecureLogger.sanitize(throwable.stackTraceToString()) }
            .getOrElse { throwable.javaClass.name }

        return buildString {
            appendLine("Time: ${dateFmt.format(Date())}")
            appendLine("App: Prime Store v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs: $abis")
            appendLine("Language: $effectiveLang (device: ${Locale.getDefault().toLanguageTag()})")
            appendLine("Thread: ${thread.name}")
            appendLine("Exception: ${SecureLogger.sanitize(throwable.javaClass.name + ": " + (throwable.message ?: ""))}")
            appendLine("Stacktrace:")
            append(stack.trimEnd())
        }
    }

    private fun readReportsList(file: File): List<String> {
        if (!file.exists() || !file.isFile) return emptyList()
        return runCatching {
            val raw = file.readText(Charsets.UTF_8).trim()
            if (raw.isBlank()) {
                emptyList()
            } else {
                raw.split(ENTRY_DELIMITER).map { it.trim() }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun getAllCrashReports(context: Context): List<String> =
        readReportsList(crashFile(context.applicationContext))

    fun getLastCrashReport(context: Context): String? =
        getAllCrashReports(context).firstOrNull()

    fun hasUnreadCrash(context: Context): Boolean {
        val appContext = context.applicationContext
        val flagged = runCatching {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNREAD_CRASH, false)
        }.getOrDefault(false)
        return flagged && !getLastCrashReport(appContext).isNullOrBlank()
    }

    fun markCrashAcknowledged(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UNREAD_CRASH, false)
                .apply()
        }
    }

    fun clearCrashReports(context: Context) {
        runCatching { crashFile(context.applicationContext).delete() }
        markCrashAcknowledged(context)
    }
}
