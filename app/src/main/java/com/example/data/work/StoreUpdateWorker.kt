package com.example.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.github.StoreSelfUpdater
import com.example.data.github.StoreUpdateCheckResult
import com.example.data.installer.PackageHelper
import com.example.data.supabase.SupabaseClient
import java.util.concurrent.TimeUnit

class StoreUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "prime_store_periodic_update_check"
        private const val CHANNEL_ID = "prime_store_updates_channel"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<StoreUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("prime_store_auth", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("ps_notifications_enabled", true)
        if (!notificationsEnabled) return Result.success()

        // 1. Check for Prime Store self update
        val storeResult = StoreSelfUpdater.checkForStoreUpdate(context, force = false)
        if (storeResult is StoreUpdateCheckResult.UpdateAvailable) {
            showNotification(
                title = context.getString(R.string.worker_store_update_title),
                message = context.getString(R.string.worker_store_update_msg, storeResult.info.versionName),
                notificationId = 101
            )
            return Result.success()
        }

        // 2. Check for installed apps updates
        try {
            val allApps = SupabaseClient.fetchApps()
            var updateCount = 0
            for (app in allApps) {
                val pkg = app.packageName ?: continue
                val installedVersion = PackageHelper.getInstalledVersion(context, pkg)
                if (installedVersion != null) {
                    val appCode = app.version.filter { it.isDigit() }.toLongOrNull() ?: 1L
                    if (appCode > installedVersion) {
                        updateCount++
                    }
                }
            }

            if (updateCount > 0) {
                showNotification(
                    title = context.getString(R.string.worker_apps_update_title),
                    message = context.getString(R.string.worker_apps_update_msg, updateCount),
                    notificationId = 102
                )
            } else if (allApps.isNotEmpty()) {
                val newest = allApps.first()
                if (newest.isNew) {
                    showNotification(
                        title = context.getString(R.string.worker_new_app_title),
                        message = context.getString(R.string.worker_new_app_msg, newest.nameAr.ifBlank { newest.name }),
                        notificationId = 103
                    )
                }
            }
        } catch (_: Exception) {}

        return Result.success()
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.worker_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.worker_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.prime_store_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
