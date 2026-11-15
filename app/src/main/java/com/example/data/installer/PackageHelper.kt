package com.example.data.installer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

sealed interface PackageInstallEvent {
    val appId: String
    val packageName: String

    data class Installing(override val appId: String, override val packageName: String) : PackageInstallEvent
    data class Success(override val appId: String, override val packageName: String) : PackageInstallEvent
    data class SignatureConflict(
        override val appId: String,
        override val packageName: String,
        val message: String
    ) : PackageInstallEvent
    data class Failure(
        override val appId: String,
        override val packageName: String,
        val status: Int,
        val message: String
    ) : PackageInstallEvent
}

object PackageEventBus {
    private val _events = MutableSharedFlow<PackageInstallEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: PackageInstallEvent) {
        _events.tryEmit(event)
    }
}

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
    const val EXTRA_APP_ID = "extra_app_id"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    private const val INSTALL_RESULT_CHANNEL_ID = "prime_install_results"
    private const val STREAM_BUFFER_SIZE = 64 * 1024

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

    fun isInstalledBySelf(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            installer == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun sha256File(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            FileInputStream(file).use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun verifySha256(file: File, expectedHex: String?): Boolean {
        val cleanExpected = expectedHex?.trim()?.lowercase(Locale.US)?.replace(":", "")?.replace(" ", "")
        if (cleanExpected.isNullOrBlank()) return true
        val actual = sha256File(file).lowercase(Locale.US)
        return actual.isNotBlank() && actual == cleanExpected
    }

    fun getArchiveInfo(context: Context, file: File): ArchiveAppInfo? {
        if (!file.exists() || file.length() == 0L) return null
        val lower = file.name.lowercase(Locale.US)
        if (lower.endsWith(".apks") || lower.endsWith(".xapk") || lower.endsWith(".zip")) {
            val pkg = ApkInstaller.getApkPackageName(context, file) ?: return null
            return ArchiveAppInfo(
                packageName = pkg,
                versionCode = 1L,
                versionName = "1.0.0",
                signatureSha256 = "",
                appName = null,
                minSdk = 21
            )
        }
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

        if (archive.versionCode <= installed.versionCode && archive.signatureSha256.isNotBlank()) {
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

    fun installApk(
        context: Context,
        file: File,
        appId: String = "",
        expectedPackageName: String = "",
        useSilentRootShizuku: Boolean = false,
        onStatusMessage: ((String) -> Unit)? = null
    ): Boolean {
        return installApkBundleBlocking(
            context = context,
            files = listOf(file),
            appId = appId,
            expectedPackageName = expectedPackageName,
            obbFile = null,
            useSilentRootShizuku = useSilentRootShizuku,
            onStatusMessage = onStatusMessage
        )
    }

    suspend fun installPackageBundle(
        context: Context,
        files: List<File>,
        appId: String = "",
        expectedPackageName: String = "",
        obbFile: File? = null,
        useSilentRootShizuku: Boolean = false,
        onStatusMessage: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        installApkBundleBlocking(
            context = context,
            files = files,
            appId = appId,
            expectedPackageName = expectedPackageName,
            obbFile = obbFile,
            useSilentRootShizuku = useSilentRootShizuku,
            onStatusMessage = onStatusMessage
        )
    }

    private fun installApkBundleBlocking(
        context: Context,
        files: List<File>,
        appId: String,
        expectedPackageName: String,
        obbFile: File?,
        useSilentRootShizuku: Boolean,
        onStatusMessage: ((String) -> Unit)?
    ): Boolean {
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        if (validFiles.isEmpty()) {
            onStatusMessage?.invoke(context.getString(R.string.pkg_file_missing))
            return false
        }

        val primary = validFiles.first()
        val resolvedPkg = expectedPackageName.trim()
            .takeIf { it.isNotBlank() && !it.startsWith("com.primestore.") }
            ?: getArchiveInfo(context, primary)?.packageName
            ?: expectedPackageName.trim()

        // Optional OBB placement (supports scoped storage graceful fallback)
        if (obbFile != null && obbFile.exists() && obbFile.length() > 0L && resolvedPkg.isNotBlank()) {
            copyObbToTarget(context, obbFile, resolvedPkg, obbFile.name, onStatusMessage)
        }

        // Optional Shizuku / Root silent mode when user explicitly enabled it
        if (useSilentRootShizuku && validFiles.size == 1 && primary.name.endsWith(".apk", ignoreCase = true)) {
            val rootSuccess = tryRootSilentInstall(context, primary, appId, resolvedPkg)
            if (rootSuccess) {
                return true
            }
            onStatusMessage?.invoke(context.getString(R.string.silent_install_unavailable))
        }

        if (!canRequestPackageInstalls(context)) {
            onStatusMessage?.invoke(context.getString(R.string.pkg_enable_install_perm))
            return false
        }

        var session: PackageInstaller.Session? = null
        return try {
            val pi = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (resolvedPkg.isNotBlank()) {
                params.setAppPackageName(resolvedPkg)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
                val isSelfUpdate = resolvedPkg == context.packageName
                val canSilentUpdate = isSelfUpdate || (resolvedPkg.isNotBlank() && isInstalledBySelf(context, resolvedPkg))
                if (canSilentUpdate) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = pi.createSession(params)
            session = pi.openSession(sessionId)

            var apkEntriesWritten = 0
            val singleNameLower = primary.name.lowercase(Locale.US)
            val isArchiveBundle = validFiles.size == 1 &&
                (singleNameLower.endsWith(".apks") || singleNameLower.endsWith(".xapk") || singleNameLower.endsWith(".zip"))

            if (isArchiveBundle) {
                ZipInputStream(primary.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    var idx = 0
                    val buf = ByteArray(STREAM_BUFFER_SIZE)
                    while (entry != null) {
                        val entryName = entry.name
                        val lowerName = entryName.lowercase(Locale.US)
                        if (!entry.isDirectory && lowerName.endsWith(".apk")) {
                            val cleanEntryName = entryName.substringAfterLast('/').ifBlank { "split_$idx.apk" }
                            val sizeHint = if (entry.size > 0L) entry.size else -1L
                            session.openWrite("apk_${idx}_$cleanEntryName", 0, sizeHint).use { out ->
                                var read: Int
                                while (zis.read(buf).also { read = it } != -1) {
                                    out.write(buf, 0, read)
                                }
                                session.fsync(out)
                            }
                            apkEntriesWritten++
                            idx++
                        } else if (!entry.isDirectory && lowerName.endsWith(".obb") && resolvedPkg.isNotBlank()) {
                            val obbName = entryName.substringAfterLast('/')
                            streamZipObbToTarget(context, zis, resolvedPkg, obbName, onStatusMessage)
                        }
                        entry = zis.nextEntry
                    }
                }
            } else {
                val buf = ByteArray(STREAM_BUFFER_SIZE)
                validFiles.forEachIndexed { idx, file ->
                    FileInputStream(file).use { input ->
                        val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        session.openWrite("split_${idx}_$safeName", 0, file.length()).use { output ->
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                            }
                            session.fsync(output)
                        }
                    }
                    apkEntriesWritten++
                }
            }

            if (apkEntriesWritten == 0) {
                session.abandon()
                onStatusMessage?.invoke(context.getString(R.string.pkg_file_missing))
                return false
            }

            val intent = Intent(context, PackageInstallStatusReceiver::class.java).apply {
                action = ACTION_INSTALL_STATUS
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_PACKAGE_NAME, resolvedPkg)
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

            PackageEventBus.emit(PackageInstallEvent.Installing(appId, resolvedPkg))
            session.commit(pendingIntent.intentSender)
            session.close()
            true
        } catch (e: Exception) {
            runCatching { session?.abandon() }
            onStatusMessage?.invoke(context.getString(R.string.pkg_open_apk_failed, e.message ?: ""))
            false
        }
    }

    private fun tryRootSilentInstall(
        context: Context,
        apkFile: File,
        appId: String,
        pkg: String
    ): Boolean {
        return try {
            val size = apkFile.length()
            val quotedPath = "\"${apkFile.absolutePath}\""
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r -d $quotedPath"))
            val code = proc.waitFor()
            if (code == 0 && size > 0L) {
                PackageEventBus.emit(PackageInstallEvent.Success(appId, pkg))
                showInstalledNotification(context, pkg)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyObbToTarget(
        context: Context,
        source: File,
        packageName: String,
        fileName: String,
        onStatusMessage: ((String) -> Unit)?
    ) {
        val publicObbDir = File(
            Environment.getExternalStorageDirectory(),
            "Android/obb/$packageName"
        )
        try {
            if (!publicObbDir.exists()) publicObbDir.mkdirs()
            val dest = File(publicObbDir, fileName)
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(STREAM_BUFFER_SIZE)
                    var r: Int
                    while (input.read(buf).also { r = it } != -1) {
                        output.write(buf, 0, r)
                    }
                }
            }
        } catch (_: Exception) {
            runCatching {
                val appScopedDir = context.getExternalFilesDir("obb/$packageName")
                    ?: File(context.filesDir, "obb/$packageName")
                if (!appScopedDir.exists()) appScopedDir.mkdirs()
                val fallbackDest = File(appScopedDir, fileName)
                FileInputStream(source).use { input ->
                    FileOutputStream(fallbackDest).use { output ->
                        val buf = ByteArray(STREAM_BUFFER_SIZE)
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            output.write(buf, 0, r)
                        }
                    }
                }
            }
            onStatusMessage?.invoke(context.getString(R.string.obb_scoped_storage_hint))
        }
    }

    private fun streamZipObbToTarget(
        context: Context,
        zis: ZipInputStream,
        packageName: String,
        fileName: String,
        onStatusMessage: ((String) -> Unit)?
    ) {
        val publicObbDir = File(
            Environment.getExternalStorageDirectory(),
            "Android/obb/$packageName"
        )
        try {
            if (!publicObbDir.exists()) publicObbDir.mkdirs()
            val dest = File(publicObbDir, fileName)
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(STREAM_BUFFER_SIZE)
                var r: Int
                while (zis.read(buf).also { r = it } != -1) {
                    output.write(buf, 0, r)
                }
            }
        } catch (_: Exception) {
            runCatching {
                val appScopedDir = context.getExternalFilesDir("obb/$packageName")
                    ?: File(context.filesDir, "obb/$packageName")
                if (!appScopedDir.exists()) appScopedDir.mkdirs()
                val fallbackDest = File(appScopedDir, fileName)
                FileOutputStream(fallbackDest).use { output ->
                    val buf = ByteArray(STREAM_BUFFER_SIZE)
                    var r: Int
                    while (zis.read(buf).also { r = it } != -1) {
                        output.write(buf, 0, r)
                    }
                }
            }
            onStatusMessage?.invoke(context.getString(R.string.obb_scoped_storage_hint))
        }
    }

    internal fun showInstalledNotification(context: Context, packageName: String) {
        try {
            if (!SettingsRepository.getInstance(context).notificationsEnabled.value) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.getNotificationChannel(INSTALL_RESULT_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        INSTALL_RESULT_CHANNEL_ID,
                        context.getString(R.string.notif_installed_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    nm.createNotificationChannel(channel)
                }
            }

            val pm = context.packageManager
            val appLabel = try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName.ifBlank { context.getString(R.string.dl_service_default_app) }
            }

            val launchIntent = if (packageName.isNotBlank()) pm.getLaunchIntentForPackage(packageName) else null
            val contentIntent = launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(
                    context,
                    packageName.hashCode(),
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val builder = NotificationCompat.Builder(context, INSTALL_RESULT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("$appLabel — ${context.getString(R.string.notif_installed_title)}")
                .setContentText(context.getString(R.string.notif_installed_tap_open, appLabel))
                .setAutoCancel(true)
            if (contentIntent != null) {
                builder.setContentIntent(contentIntent)
            }

            NotificationManagerCompat.from(context)
                .notify((packageName.hashCode() and 0x7FFFFFFF) + 9000, builder.build())
        } catch (_: Exception) {
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
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val appId = intent.getStringExtra(PackageHelper.EXTRA_APP_ID).orEmpty()
        val pkgName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(PackageHelper.EXTRA_PACKAGE_NAME).orEmpty()

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
                PackageHelper.showInstalledNotification(context, pkgName)
                PackageEventBus.emit(PackageInstallEvent.Success(appId, pkgName))
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                PackageEventBus.emit(PackageInstallEvent.SignatureConflict(appId, pkgName, message))
            }
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE -> {
                PackageEventBus.emit(PackageInstallEvent.Failure(appId, pkgName, status, message))
            }
            else -> {}
        }
    }
}
