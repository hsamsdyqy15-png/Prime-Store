package com.example.data.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.example.data.repository.AppsRepository
import com.example.data.supabase.SecureLogger
import com.example.data.supabase.SupabaseClient
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SyncManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val appsRepository = AppsRepository.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _newAppsAddedCount = MutableStateFlow(0)
    val newAppsAddedCount: StateFlow<Int> = _newAppsAddedCount.asStateFlow()

    private val _lastSyncTimeMillis = MutableStateFlow(0L)
    val lastSyncTimeMillis: StateFlow<Long> = _lastSyncTimeMillis.asStateFlow()

    private val _lastSyncResult = MutableStateFlow("Not synced")
    val lastSyncResult: StateFlow<String> = _lastSyncResult.asStateFlow()

    private val _isForeground = MutableStateFlow(false)
    private var startedActivityCount = 0
    private val lifecycleRegistered = AtomicBoolean(false)

    private var pollingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var webSocket: WebSocket? = null
    private val wsRefCounter = AtomicInteger(1)
    private var wsBackoffMs = 5_000L

    @Volatile
    private var lastEtag: String? = null

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

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
        val wasForeground = _isForeground.getAndSet(true)
        if (!wasForeground) {
            startPolling()
            connectRealtimeWebSocket()
        }
        scope.launch {
            syncNow(forceFull = false, suppressNewBanner = false)
        }
    }

    fun onForegroundResumed() {
        if (!_isForeground.value) {
            onForegroundStarted()
        } else {
            scope.launch {
                syncNow(forceFull = false, suppressNewBanner = false)
            }
        }
    }

    fun onBackgrounded() {
        _isForeground.value = false
        pollingJob?.cancel()
        pollingJob = null
        disconnectRealtimeWebSocket()
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
            while (isActive && _isForeground.value) {
                delay(currentDelayMs)
                if (!isActive || !_isForeground.value) break
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

                // If network failed to fetch expected new/changed rows, keep DB safe and retry later
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
            } catch (e: Exception) {
                _lastSyncResult.value = "Error: ${e.message ?: "unknown"}"
                SecureLogger.w(TAG, "SYNC_ERR", "Delta sync error: ${e.message}")
                false
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun connectRealtimeWebSocket() {
        if (!_isForeground.value) return
        if (webSocket != null) return
        reconnectJob?.cancel()

        val wsHost = SupabaseClient.SUPABASE_URL.replaceFirst("https://", "wss://")
        val wsUrl = "$wsHost/realtime/v1/websocket?apikey=${SupabaseClient.SUPABASE_KEY}&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                wsBackoffMs = 5_000L
                sendPhoenixJoin(ws)
                startRealtimeHeartbeat(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleRealtimeMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                cleanupSocketReference(ws)
                scheduleRealtimeReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                cleanupSocketReference(ws)
                scheduleRealtimeReconnect()
            }
        })
    }

    private fun sendPhoenixJoin(ws: WebSocket) {
        try {
            val pgChange = JSONObject().apply {
                put("event", "*")
                put("schema", "public")
                put("table", "apps")
            }
            val config = JSONObject().apply {
                put("postgres_changes", JSONArray().put(pgChange))
            }
            val payload = JSONObject().apply {
                put("config", config)
            }
            val joinFrame = JSONObject().apply {
                put("topic", REALTIME_TOPIC)
                put("event", "phx_join")
                put("payload", payload)
                put("ref", wsRefCounter.getAndIncrement().toString())
            }
            ws.send(joinFrame.toString())
        } catch (_: Exception) {
        }
    }

    private fun startRealtimeHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _isForeground.value && webSocket === ws) {
                delay(25_000L)
                if (!isActive || !_isForeground.value || webSocket !== ws) break
                try {
                    val hb = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", wsRefCounter.getAndIncrement().toString())
                    }
                    ws.send(hb.toString())
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun handleRealtimeMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val event = msg.optString("event", "")
            if (event == "postgres_changes") {
                lastEtag = null
                scope.launch {
                    syncNow(forceFull = false, suppressNewBanner = false)
                }
            } else if (event == "phx_error") {
                scheduleRealtimeReconnect()
            }
        } catch (_: Exception) {
        }
    }

    private fun cleanupSocketReference(ws: WebSocket) {
        if (webSocket === ws) {
            webSocket = null
        }
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleRealtimeReconnect() {
        if (!_isForeground.value) return
        if (reconnectJob?.isActive == true) return
        val delayMs = wsBackoffMs
        wsBackoffMs = (wsBackoffMs * 2).coerceAtMost(60_000L)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (isActive && _isForeground.value && webSocket == null) {
                connectRealtimeWebSocket()
            }
        }
    }

    private fun disconnectRealtimeWebSocket() {
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        val ws = webSocket
        webSocket = null
        try {
            ws?.close(1000, "background")
        } catch (_: Exception) {
        }
    }

    private fun MutableStateFlow<Boolean>.getAndSet(newValue: Boolean): Boolean {
        val old = value
        value = newValue
        return old
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val BASE_POLL_INTERVAL_MS = 20_000L
        private const val MAX_BACKOFF_INTERVAL_MS = 300_000L
        private const val REALTIME_TOPIC = "realtime:public:apps"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
