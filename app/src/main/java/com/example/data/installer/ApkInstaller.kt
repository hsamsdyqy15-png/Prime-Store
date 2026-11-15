package com.example.data.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

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
     * Extracts package name from APK or APK bundle (.apks/.xapk/.zip) without installing it.
     */
    fun getApkPackageName(ctx: Context, file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        val lower = file.name.lowercase(Locale.US)
        if (lower.endsWith(".apks") || lower.endsWith(".xapk") || lower.endsWith(".zip")) {
            return extractPackageNameFromBundle(ctx, file)
        }
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

    private fun extractPackageNameFromBundle(ctx: Context, bundleFile: File): String? {
        return try {
            var resolved: String? = null
            val tempBase = File(ctx.cacheDir, "inspect_${System.currentTimeMillis()}.apk")
            ZipInputStream(bundleFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.US)
                    if (!entry.isDirectory && name == "manifest.json") {
                        val bytes = zis.readBytes()
                        val json = runCatching { JSONObject(String(bytes)) }.getOrNull()
                        val pkg = json?.optString("package_name", "")?.trim()
                        if (!pkg.isNullOrBlank()) {
                            resolved = pkg
                            break
                        }
                    } else if (!entry.isDirectory && name.endsWith(".apk") && (name.contains("base") || resolved == null)) {
                        FileOutputStream(tempBase).use { out ->
                            val buf = ByteArray(16 * 1024)
                            var r: Int
                            while (zis.read(buf).also { r = it } != -1) {
                                out.write(buf, 0, r)
                            }
                        }
                        val info = ctx.packageManager.getPackageArchiveInfo(tempBase.absolutePath, 0)
                        if (!info?.packageName.isNullOrBlank()) {
                            resolved = info?.packageName
                            if (name.contains("base")) break
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            tempBase.delete()
            resolved
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves installed package strictly by exact packageName or APK archive packageName.
     * Never performs fuzzy label matching which can misidentify unrelated installed apps.
     */
    fun findInstalledPackage(
        ctx: Context,
        appName: String,
        apkFile: File? = null,
        candidatePackage: String? = null
    ): String? {
        val cleanCandidate = candidatePackage?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (cleanCandidate != null && isAppInstalled(ctx, cleanCandidate)) {
            return cleanCandidate
        }

        if (apkFile != null && apkFile.exists() && apkFile.length() > 0L) {
            val pkgFromApk = getApkPackageName(ctx, apkFile)?.trim()?.takeIf { it.isNotBlank() }
            if (pkgFromApk != null && isAppInstalled(ctx, pkgFromApk)) {
                return pkgFromApk
            }
        }

        return null
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
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
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

    /**
     * Session-based installation via PackageInstaller.Session (no raw ACTION_VIEW).
     */
    fun installApk(ctx: Context, file: File): Boolean {
        return PackageHelper.installApk(ctx, file)
    }

    fun getApksRootDir(ctx: Context): File {
        val dir = ctx.getExternalFilesDir("apks") ?: File(ctx.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getApkFile(ctx: Context, appId: String, extension: String = "apk"): File {
        val dir = getApksRootDir(ctx)
        val cleanExt = extension.trim().trimStart('.').lowercase(Locale.US).ifBlank { "apk" }
        return File(dir, "$appId.$cleanExt")
    }

    fun getBundleDir(ctx: Context, appId: String): File {
        val dir = File(getApksRootDir(ctx), "${appId}_bundle")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getObbFile(ctx: Context, appId: String): File {
        val dir = getApksRootDir(ctx)
        return File(dir, "$appId.obb")
    }

    fun resolveExtensionFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return "apk"
        val path = url.trim().substringBefore("?").lowercase(Locale.US)
        return when {
            path.endsWith(".apks") -> "apks"
            path.endsWith(".xapk") -> "xapk"
            path.endsWith(".zip") -> "zip"
            else -> "apk"
        }
    }

    fun findExistingPrimaryInstallFile(ctx: Context, appId: String): File? {
        val dir = getApksRootDir(ctx)
        val candidates = listOf("apk", "apks", "xapk", "zip").map { File(dir, "$appId.$it") }
        val direct = candidates.firstOrNull { it.exists() && it.length() > 0L }
        if (direct != null) return direct

        val bundleDir = File(dir, "${appId}_bundle")
        if (bundleDir.exists() && bundleDir.isDirectory) {
            val apks = bundleDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", true) && f.length() > 0L }
                ?.sortedBy { if (it.name.contains("base", true)) "" else it.name }
            if (!apks.isNullOrEmpty()) {
                return apks.first()
            }
        }
        return null
    }

    fun getBundleApkFiles(ctx: Context, appId: String): List<File> {
        val dir = getApksRootDir(ctx)
        val bundleDir = File(dir, "${appId}_bundle")
        if (bundleDir.exists() && bundleDir.isDirectory) {
            val splitFiles = bundleDir.listFiles { f ->
                f.isFile && f.name.endsWith(".apk", true) && f.length() > 0L
            }?.sortedBy { if (it.name.contains("base", true)) "" else it.name }
            if (!splitFiles.isNullOrEmpty()) return splitFiles
        }
        val single = findExistingPrimaryInstallFile(ctx, appId)
        return if (single != null) listOf(single) else emptyList()
    }

    fun cleanupAppInstallFiles(ctx: Context, appId: String) {
        val dir = getApksRootDir(ctx)
        listOf("apk", "apks", "xapk", "zip", "obb", "apk.part", "apks.part", "xapk.part", "zip.part", "obb.part").forEach { ext ->
            runCatching {
                val f = File(dir, "$appId.$ext")
                if (f.exists()) f.delete()
            }
        }
        runCatching {
            val bundleDir = File(dir, "${appId}_bundle")
            if (bundleDir.exists()) bundleDir.deleteRecursively()
        }
    }
}
