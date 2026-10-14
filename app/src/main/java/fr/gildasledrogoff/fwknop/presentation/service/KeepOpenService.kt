package fr.gildasledrogoff.fwknop.presentation.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeepOpenService : LifecycleService() {
    @Inject
    lateinit var sessions: KeepOpenSessionStore
    @Inject
    lateinit var notifications: KnockNotifications
    @Inject
    lateinit var sendKnock: SendKnockUseCase
    private val jobs = mutableMapOf<Int, Job>()
    private val schedules = mutableMapOf<Int, ReknockSchedule>()
    private var networkSettle: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        watchNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val sessionId = intent?.getIntExtra(EXTRA_SESSION, 0) ?: 0
        val request = sessions.get(sessionId)
        when {
            request == null -> stopSessionIfIdle()
            intent?.action == ACTION_STOP -> stopSession(sessionId)
            else -> startSession(sessionId, request, intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0)
        }
        return START_NOT_STICKY
    }

    private fun startSession(sessionId: Int, request: KnockRequest, seconds: Int) {
        val notification =
            notifications.keepOpen(request.config.nickname.value, seconds, stopIntent(sessionId))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, sessionId, notification, type)
        jobs[sessionId]?.cancel()
        val schedule = ReknockSchedule(
            intervalMs = (seconds - REKNOCK_MARGIN_S).coerceAtLeast(MIN_INTERVAL_S) * MILLIS,
            retryMs = NETWORK_RETRY_MS,
        )
        schedules[sessionId] = schedule
        jobs[sessionId] = lifecycleScope.launch { reknockLoop(sessionId, request, schedule, seconds) }
    }

    private suspend fun reknockLoop(
        sessionId: Int,
        request: KnockRequest,
        schedule: ReknockSchedule,
        seconds: Int,
    ) {
        val nickname = request.config.nickname.value
        schedule.run {
            when (val result = sendKnock(request)) {
                is DomainResult.Failure -> {
                    DebugLog.event("keep-open re-knock failed: ${result.error::class.simpleName}")
                    false
                }

                is DomainResult.Success -> {
                    notifications.notify(
                        sessionId,
                        notifications.keepOpen(nickname, seconds, stopIntent(sessionId))
                    )
                    true
                }
            }
        }
        notifications.keepOpenFailed(nickname)
        stopSession(sessionId)
    }

    // A new network means a new source IP: the opened rule no longer matches it.
    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val active = connectivity.activeNetwork
        val detector = NetworkChangeDetector(
            active,
            active?.let(connectivity::getNetworkCapabilities)?.physicalTransports().orEmpty(),
        )
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (detector.onCapabilitiesChanged(network, capabilities.physicalTransports())) onNetworkChanged()
            }

            override fun onLost(network: Network) = detector.onLost(network)
        }
        try {
            // Main looper: callbacks then touch the session maps on the same thread as the service.
            connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            networkCallback = callback
        } catch (e: RuntimeException) {
            DebugLog.failure("keep-open network callback", e)
        }
    }

    private fun onNetworkChanged() {
        networkSettle?.cancel()
        networkSettle = lifecycleScope.launch {
            delay(NETWORK_SETTLE_MS)
            DebugLog.event("keep-open network changed")
            schedules.filterKeys { sessions.get(it)?.followsNetwork == true }
                .values.forEach(ReknockSchedule::onNetworkChanged)
        }
    }

    private fun stopSession(sessionId: Int) {
        jobs.remove(sessionId)?.cancel()
        schedules.remove(sessionId)
        sessions.remove(sessionId)
        notifications.cancel(sessionId)
        stopSessionIfIdle()
    }

    private fun stopSessionIfIdle() {
        if (jobs.isNotEmpty()) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        networkCallback = null
        jobs.keys.forEach(sessions::remove)
        jobs.clear()
        schedules.clear()
        super.onDestroy()
    }

    private fun stopIntent(sessionId: Int): PendingIntent = PendingIntent.getService(
        this,
        sessionId,
        Intent(this, KeepOpenService::class.java).setAction(ACTION_STOP)
            .putExtra(EXTRA_SESSION, sessionId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val ACTION_STOP = "fr.gildasledrogoff.fwknop.action.STOP_KEEP_OPEN"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_SECONDS = "seconds"
        private const val REKNOCK_MARGIN_S = 10L
        private const val MIN_INTERVAL_S = 5L
        private const val MILLIS = 1_000L
        private const val NETWORK_SETTLE_MS = 2_000L
        private const val NETWORK_RETRY_MS = 5_000L
        fun startIntent(context: Context, sessionId: Int, seconds: Int): Intent =
            Intent(context, KeepOpenService::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_SECONDS, seconds)
    }
}

// Under a VPN the default network stays the VPN; its transports reveal a Wi-Fi / mobile switch beneath.
private val PHYSICAL_TRANSPORTS = intArrayOf(
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_BLUETOOTH,
)

private fun NetworkCapabilities.physicalTransports(): Set<Int> =
    PHYSICAL_TRANSPORTS.filter(::hasTransport).toSet()
