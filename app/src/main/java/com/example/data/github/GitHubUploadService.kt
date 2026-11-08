package com.example.data.github

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

class GitHubUploadService : Service() {

    companion object {
        const val CHANNEL_ID = "prime_github_upload_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.example.github.upload.START"
        const val ACTION_UPDATE = "com.example.github.upload.UPDATE"
        const val ACTION_STOP = "com.example.github.upload.STOP"

        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_UPLOADED_MB = "extra_uploaded_mb"
        const val EXTRA_TOTAL_MB = "extra_total_mb"

        fun start(context: Context, fileName: String) {
            val intent = Intent(context, GitHubUploadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, fileName: String, progress: Int, uploadedMb: String, totalMb: String) {
            val intent = Intent(context, GitHubUploadService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_UPLOADED_MB, uploadedMb)
                putExtra(EXTRA_TOTAL_MB, totalMb)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GitHubUploadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var currentFileName: String = "ملف APK"

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentFileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "ملف APK"
                startForeground(NOTIFICATION_ID, buildNotification(0, "0 MB", "0 MB"))
            }
            ACTION_UPDATE -> {
                val fName = intent.getStringExtra(EXTRA_FILE_NAME) ?: currentFileName
                currentFileName = fName
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val upMb = intent.getStringExtra(EXTRA_UPLOADED_MB) ?: "0 MB"
                val totMb = intent.getStringExtra(EXTRA_TOTAL_MB) ?: "0 MB"
                notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, upMb, totMb))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "رفع الملفات إلى GitHub Releases",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تقدم رفع ملفات APK في الخلفية"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, uploadedMb: String, totalMb: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("جاري رفع $currentFileName إلى GitHub")
            .setContentText("$uploadedMb / $totalMb ($progress%)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
