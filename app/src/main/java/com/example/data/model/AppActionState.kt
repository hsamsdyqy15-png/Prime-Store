package com.example.data.model

sealed interface AppActionState {
    data object Open : AppActionState
    data class Update(val currentVersionCode: Long, val newVersionCode: Long) : AppActionState
    data class Downloading(
        val progress: Float,
        val isPaused: Boolean,
        val speed: String
    ) : AppActionState
    data object Installing : AppActionState
    data class InstallReady(val hasSignatureConflict: Boolean = false) : AppActionState
    data object Download : AppActionState
    data class Retry(val errorMessage: String?) : AppActionState
}

fun resolveAppActionState(
    app: AppItem,
    downloadItem: DownloadItem?,
    isApkReady: Boolean,
    isInstalling: Boolean,
    hasSignatureConflict: Boolean,
    installedInfo: InstalledPackageSummary?
): AppActionState {
    if (isInstalling) {
        return AppActionState.Installing
    }

    if (downloadItem != null &&
        (downloadItem.status == DownloadStatus.DOWNLOADING || downloadItem.status == DownloadStatus.PAUSED)
    ) {
        return AppActionState.Downloading(
            progress = downloadItem.progress,
            isPaused = downloadItem.status == DownloadStatus.PAUSED || downloadItem.isPaused,
            speed = downloadItem.speed
        )
    }

    if (downloadItem?.status == DownloadStatus.FAILED) {
        return AppActionState.Retry(downloadItem.errorMessage)
    }

    val apkAvailable = isApkReady || downloadItem?.status == DownloadStatus.COMPLETED

    if (installedInfo != null) {
        val hasUpdate = app.versionCode > installedInfo.versionCode
        return when {
            apkAvailable && (hasUpdate || hasSignatureConflict) ->
                AppActionState.InstallReady(hasSignatureConflict = hasSignatureConflict)
            hasUpdate ->
                AppActionState.Update(
                    currentVersionCode = installedInfo.versionCode,
                    newVersionCode = app.versionCode
                )
            else ->
                AppActionState.Open
        }
    }

    if (apkAvailable) {
        return AppActionState.InstallReady(hasSignatureConflict = hasSignatureConflict)
    }

    return AppActionState.Download
}
