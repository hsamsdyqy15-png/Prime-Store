package com.example.data.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

    fun canRequestPackageInstalls(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppSourcesSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    /**
     * Checks if an app with the given package name is already installed on the device.
     */
    fun isAppInstalled(ctx: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            ctx.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Extracts package name from APK file without installing it.
     */
    fun getApkPackageName(ctx: Context, file: File): String? {
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            info?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Uninstalls previous/old version of the app to resolve signature conflicts.
     */
    fun uninstallPreviousVersion(ctx: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun installApk(ctx: Context, file: File): Boolean {
        if (!file.exists()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                openUnknownAppSourcesSettings(ctx)
                return false
            }
        }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    fun getApkFile(ctx: Context, appId: String): File {
        val dir = ctx.getExternalFilesDir("apks") ?: File(ctx.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${appId}.apk")
    }
}
