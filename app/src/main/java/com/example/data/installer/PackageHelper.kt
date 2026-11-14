package com.example.data.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ArchiveAppInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signatureSha256: String,
    val appName: String?,
    val minSdk: Int
)

data class InstalledAppInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signatureSha256: String
)

sealed class InstallPrecheckResult {
    data object ReadyToInstall : InstallPrecheckResult()
    data class SignatureConflict(
        val packageName: String,
        val installedVersionName: String,
        val installedVersionCode: Long,
        val newVersionName: String,
        val newVersionCode: Long
    ) : InstallPrecheckResult()
    data class DowngradeOrSameVersion(
        val packageName: String,
        val installedVersionName: String,
        val installedVersionCode: Long,
        val newVersionName: String,
        val newVersionCode: Long
    ) : InstallPrecheckResult()
}

object PackageHelper {

    private const val ACTION_INSTALL_STATUS = "com.example.primestore.INSTALL_STATUS"

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun getArchiveInfo(context: Context, file: File): ArchiveAppInfo? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val info = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
            info.applicationInfo?.sourceDir = file.absolutePath
            info.applicationInfo?.publicSourceDir = file.absolutePath

            val packageName = info.packageName ?: return null
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val versionName = info.versionName ?: "1.0.0"
            val appName = info.applicationInfo?.loadLabel(pm)?.toString()
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                info.applicationInfo?.minSdkVersion ?: 21
            } else {
                21
            }

            val sigSha = extractSignatureSha256(info)

            ArchiveAppInfo(
                packageName = packageName,
                versionCode = versionCode,
                versionName = versionName,
                signatureSha256 = sigSha,
                appName = appName,
                minSdk = minSdk
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getInstalledInfo(context: Context, packageName: String): InstalledAppInfo? {
        if (packageName.isBlank()) return null
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = pm.getPackageInfo(packageName, flags)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val versionName = info.versionName ?: "1.0.0"
            val sigSha = extractSignatureSha256(info)
            InstalledAppInfo(
                packageName = packageName,
                versionCode = versionCode,
                versionName = versionName,
                signatureSha256 = sigSha
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return getInstalledInfo(context, packageName) != null
    }

    fun getInstalledVersion(context: Context, packageName: String): Long? {
        return getInstalledInfo(context, packageName)?.versionCode
    }

    fun checkInstallPreconditions(
        context: Context,
        file: File
    ): Pair<ArchiveAppInfo?, InstallPrecheckResult> {
        val archive = getArchiveInfo(context, file)
            ?: return Pair(null, InstallPrecheckResult.ReadyToInstall)

        val installed = getInstalledInfo(context, archive.packageName)
            ?: return Pair(archive, InstallPrecheckResult.ReadyToInstall)

        // Compare signatures
        if (installed.signatureSha256.isNotBlank() && archive.signatureSha256.isNotBlank()) {
            if (!installed.signatureSha256.equals(archive.signatureSha256, ignoreCase = true)) {
                return Pair(
                    archive,
                    InstallPrecheckResult.SignatureConflict(
                        packageName = archive.packageName,
                        installedVersionName = installed.versionName,
                        installedVersionCode = installed.versionCode,
                        newVersionName = archive.versionName,
                        newVersionCode = archive.versionCode
                    )
                )
            }
        }

        // Compare versions if signatures match
        if (archive.versionCode <= installed.versionCode) {
            return Pair(
                archive,
                InstallPrecheckResult.DowngradeOrSameVersion(
                    packageName = archive.packageName,
                    installedVersionName = installed.versionName,
                    installedVersionCode = installed.versionCode,
                    newVersionName = archive.versionName,
                    newVersionCode = archive.versionCode
                )
            )
        }

        return Pair(archive, InstallPrecheckResult.ReadyToInstall)
    }

    fun uninstall(
        context: Context,
        packageName: String,
        launcher: ActivityResultLauncher<Intent>? = null
    ): Boolean {
        if (packageName.isBlank()) return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        return try {
            if (launcher != null) {
                launcher.launch(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Standard installation via system Intent or PackageInstaller session
     */
    fun installApk(
        context: Context,
        file: File,
        onStatusMessage: ((String) -> Unit)? = null
    ): Boolean {
        if (!file.exists() || file.length() == 0L) {
            onStatusMessage?.invoke(context.getString(R.string.pkg_file_missing))
            return false
        }

        if (!canRequestPackageInstalls(context)) {
            openUnknownAppSourcesSettings(context)
            onStatusMessage?.invoke(context.getString(R.string.pkg_enable_install_perm))
            return false
        }

        // Attempt PackageInstaller Session
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val pi = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = pi.createSession(params)
                val session = pi.openSession(sessionId)

                FileInputStream(file).use { input ->
                    session.openWrite("package_install_${System.currentTimeMillis()}", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val intent = Intent(context, PackageInstallStatusReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

                session.commit(pendingIntent.intentSender)
                session.close()
                return true
            } catch (e: Exception) {
                // Fallback to standard ACTION_VIEW intent if session commit encounters restrictions
            }
        }

        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            onStatusMessage?.invoke(
                context.getString(R.string.pkg_open_apk_failed, e.message ?: "")
            )
            false
        }
    }

    private fun extractSignatureSha256(info: PackageInfo): String {
        try {
            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certBytes = signatures[0].toByteArray()
                return sha256Hex(certBytes)
            }
        } catch (_: Exception) {}
        return ""
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02X".format(it) }
    }
}

class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Success handled by package manager broadcast
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                // Signature conflict
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                // Not enough storage
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                // Incompatible ABI or SDK
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                // Invalid file
            }
            else -> {}
        }
    }
}
