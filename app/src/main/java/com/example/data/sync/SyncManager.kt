package com.example.data.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.example.data.repository.AppsRepository
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class SyncManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val appsRepository = AppsRepository.getInstance(appContext)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) throw throwable
        SecureLogger.e(TAG, "SYNC_SCOPE_EXC", "Unhandled coroutine exception: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _newAppsAddedCount = MutableStateFlow(0)
    val newAppsAddedCount: StateFlow<Int> = _newAppsAddedCount.asStateFlow()

    private val _lastSyncTimeMillis = MutableStateFlow(0L)
    val lastSyncTimeMillis: StateFlow<Long> = _lastSyncTimeMillis.asStateFlow()

    private val _lastSyncResult = MutableStateFlow("Not synced")
    val lastSyncResult: StateFlow<String> = _lastSyncResult.asStateFlow()

    private val isForeground = AtomicBoolean(false)
    private var startedActivityCount = 0
    private val lifecycleRegistered = AtomicBoolean(false)

    private var pollingJob: Job? = null

    @Volatile
    private var lastEtag: String? = null

    @Volatile
    private var lastSyncTriggerMs: Long = 0L

    fun attachToApplication(application: Application) {
        if (!lifecycleRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    onForegroundStarted()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    onBackgrounded()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun onForegroundStarted() {
        val wasForeground = isForeground.getAndSet(true)
        if (!wasForeground) {
            startPolling()
        }
        triggerDebouncedSync()
    }

    fun onForegroundResumed() {
        if (!isForeground.get()) {
            onForegroundStarted()
        } else {
            triggerDebouncedSync()
        }
    }

    private fun triggerDebouncedSync() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSyncTriggerMs < RESUME_DEBOUNCE_MS) return
        lastSyncTriggerMs = now
        scope.launch {
            syncNow(forceFull = false, suppressNewBanner = false)
        }
    }

    fun onBackgrounded() {
        isForeground.set(false)
        pollingJob?.cancel()
        pollingJob = null
    }

    fun consumeNewAppsBanner() {
        _newAppsAddedCount.value = 0
    }

    fun invalidateEtag() {
        lastEtag = null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var currentDelayMs = BASE_POLL_INTERVAL_MS
            while (isActive && isForeground.get()) {
                delay(currentDelayMs)
                if (!isActive || !isForeground.get()) break
                val ok = syncNow(forceFull = false, suppressNewBanner = false)
                currentDelayMs = if (ok) {
                    BASE_POLL_INTERVAL_MS
                } else {
                    (currentDelayMs * 2).coerceAtMost(MAX_BACKOFF_INTERVAL_MS)
                }
            }
        }
    }

    suspend fun syncNow(forceFull: Boolean = false, suppressNewBanner: Boolean = false): Boolean {
        return syncMutex.withLock {
            _isSyncing.value = true
            try {
                val localMeta = appsRepository.getAllMeta()
                val localMap = localMeta.associate { it.id to it.updatedAtMillis }
                val effectiveEtag = if (forceFull || localMeta.isEmpty()) null else lastEtag

                val delta = SupabaseClient.fetchAppMetadataDelta(effectiveEtag)
                if (delta == null) {
                    _lastSyncResult.value = "Delta fetch failed"
                    SecureLogger.w(TAG, "SYNC_DELTA_FAIL", "Delta metadata fetch returned null")
                    return@withLock false
                }

                if (delta.notModified) {
                    _lastSyncTimeMillis.value = System.currentTimeMillis()
                    _lastSyncResult.value = "OK (304 unchanged)"
                    return@withLock true
                }

                lastEtag = delta.etag

                val remoteRecords = delta.records
                val remoteIds = remoteRecords.map { it.id }.toSet()

                val newIds = remoteRecords
                    .filter { it.id !in localMap }
                    .map { it.id }

                val changedIds = if (forceFull) {
                    remoteRecords.map { it.id }
                } else {
                    remoteRecords.filter { rec ->
                        val localUpdated = localMap[rec.id]
                        localUpdated != null && rec.updatedAtMillis != localUpdated
                    }.map { it.id }
                }

                val deleteIds = localMap.keys.filter { it !in remoteIds }

                val idsToFetch = (newIds + changedIds).distinct()
                if (idsToFetch.isEmpty() && deleteIds.isEmpty()) {
                    _lastSyncTimeMillis.value = System.currentTimeMillis()
                    _lastSyncResult.value = "OK (up-to-date)"
                    return@withLock true
                }

                val fetchedApps = if (idsToFetch.isNotEmpty()) {
                    SupabaseClient.fetchAppsByIds(idsToFetch)
                } else {
                    emptyList()
                }

                if (idsToFetch.isNotEmpty() && fetchedApps.isEmpty()) {
                    _lastSyncResult.value = "Failed to fetch changed rows"
                    SecureLogger.w(TAG, "SYNC_ROWS_FAIL", "Failed to fetch ${idsToFetch.size} changed rows")
                    return@withLock false
                }

                appsRepository.applyServerDelta(
                    upserts = fetchedApps,
                    deleteIds = deleteIds
                )

                if (localMeta.isNotEmpty() && newIds.isNotEmpty() && !suppressNewBanner) {
                    _newAppsAddedCount.value = _newAppsAddedCount.value + newIds.size
                }

                _lastSyncTimeMillis.value = System.currentTimeMillis()
                _lastSyncResult.value = "OK (+${newIds.size}/~${changedIds.size}/-${deleteIds.size})"
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastSyncResult.value = "Error: ${e.message ?: "unknown"}"
                SecureLogger.w(TAG, "SYNC_ERR", "Delta sync error: ${e.message}")
                false
            } finally {
                _isSyncing.value = false
            }
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val BASE_POLL_INTERVAL_MS = 20_000L
        private const val MAX_BACKOFF_INTERVAL_MS = 300_000L
        private const val RESUME_DEBOUNCE_MS = 3_000L

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
