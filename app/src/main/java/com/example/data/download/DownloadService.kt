package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.supabase.SecureLogger

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "prime_downloads_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.download.START"
        const val ACTION_UPDATE = "com.example.download.UPDATE"
        const val ACTION_STOP = "com.example.download.STOP"

        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"

        fun start(context: Context, appName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_APP_NAME, appName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                SecureLogger.w("DownloadService", "FGS_START_FALLBACK", "Could not start foreground download service: ${t.message}")
            }
        }

        fun updateProgress(context: Context, appName: String, progress: Int, speed: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speed)
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
            }
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var currentAppName: String = ""

    override fun onCreate() {
        super.onCreate()
        currentAppName = getString(R.string.dl_service_default_app)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> {
                currentAppName = intent.getStringExtra(EXTRA_APP_NAME)
                    ?: getString(R.string.dl_service_default_app)
                val notification = buildNotification(0, "0 MB/s")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (t: Throwable) {
                    SecureLogger.w("DownloadService", "FGS_PROMOTE_ERR", "startForeground skipped: ${t.message}")
                }
            }
            ACTION_UPDATE -> {
                currentAppName = intent.getStringExtra(EXTRA_APP_NAME) ?: currentAppName
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED) ?: ""
                val notification = buildNotification(progress, speed)
                runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
            }
            ACTION_STOP -> {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(progress: Int, speed: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            progress in 1..99 -> getString(R.string.dl_service_progress, progress, speed)
            progress >= 100 -> getString(R.string.dl_service_done)
            else -> getString(R.string.dl_service_starting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentAppName)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(progress < 100)
            .setProgress(100, progress, progress == 0)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dl_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.dl_service_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
