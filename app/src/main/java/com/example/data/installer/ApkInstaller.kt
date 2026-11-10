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
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts package name from APK file without installing it.
     */
    fun getApkPackageName(ctx: Context, file: File): String? {
        if (!file.exists()) return null
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (info != null) {
                info.applicationInfo?.sourceDir = file.absolutePath
                info.applicationInfo?.publicSourceDir = file.absolutePath
                info.packageName
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds installed package for an app by matching package name, apk file, or installed app label.
     */
    fun findInstalledPackage(
        ctx: Context,
        appName: String,
        apkFile: File? = null,
        candidatePackage: String? = null
    ): String? {
        val pm = ctx.packageManager

        // 1. Check candidate package
        if (!candidatePackage.isNullOrBlank() && isAppInstalled(ctx, candidatePackage)) {
            return candidatePackage
        }

        // 2. Check APK file directly
        if (apkFile != null && apkFile.exists()) {
            val pkgFromApk = getApkPackageName(ctx, apkFile)
            if (!pkgFromApk.isNullOrBlank() && isAppInstalled(ctx, pkgFromApk)) {
                return pkgFromApk
            }
        }

        // 3. Search installed packages by matching title/label or package name segment
        val cleanName = appName.trim().lowercase().replace(" ", "")
        try {
            val installed = pm.getInstalledPackages(0)
            for (pkg in installed) {
                val label = pkg.applicationInfo?.loadLabel(pm)?.toString()?.trim()?.lowercase()?.replace(" ", "").orEmpty()
                if (label.isNotEmpty() && (label == cleanName || label.contains(cleanName) || cleanName.contains(label))) {
                    return pkg.packageName
                }
                val pkgLower = pkg.packageName.lowercase()
                if (cleanName.length >= 4 && pkgLower.contains(cleanName)) {
                    return pkg.packageName
                }
            }
        } catch (_: Exception) {}

        return candidatePackage ?: (if (apkFile != null) getApkPackageName(ctx, apkFile) else null)
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
            try {
                val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Opens the system app details settings page where the user can uninstall the app directly.
     */
    fun openAppDetailsSettings(ctx: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the device list of all installed apps in Settings.
     */
    fun openManageApplicationsSettings(ctx: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
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
