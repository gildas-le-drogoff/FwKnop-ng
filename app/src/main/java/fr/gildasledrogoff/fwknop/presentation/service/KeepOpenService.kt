// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockOutcome
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerPlan
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.usecase.AutoKnockUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.PlanUnattendedTimerUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.presentation.quickaccess.OpenPortTracker
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.CoroutineStart
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

    @Inject
    lateinit var unattendedKnock: AutoKnockUseCase

    @Inject
    lateinit var planTimer: PlanUnattendedTimerUseCase

    @Inject
    lateinit var openPorts: OpenPortTracker
    private val jobs = mutableMapOf<Int, Job>()
    private val schedules = mutableMapOf<Int, ReknockSchedule>()
    private val foregroundNotifications = linkedMapOf<Int, Notification>()
    private val quickKnocks = linkedMapOf<String, Job>()
    private var networkSettle: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    override fun onCreate() {
        super.onCreate()
        watchNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_QUICK_KNOCK) {
            intent.getStringExtra(EXTRA_NICKNAME)?.let(::quickKnock) ?: stopSessionIfIdle()
            return START_NOT_STICKY
        }
        val sessionId = intent?.getIntExtra(EXTRA_SESSION, 0) ?: 0
        val request = sessions.get(sessionId)
        when {
            request == null -> stopSessionIfIdle()
            intent?.action == ACTION_STOP -> stopSession(sessionId)
            intent?.action == ACTION_EXTEND -> schedules[sessionId]?.extend()
            else -> startSession(sessionId, request, intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0)
        }
        return START_NOT_STICKY
    }

    private fun quickKnock(nickname: String) {
        ServiceCompat.startForeground(this, QUICK_KNOCK_ID, notifications.knocking(nickname), foregroundType())
        if (quickKnocks[nickname]?.isActive == true) return
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            when (val result = unattendedKnock(Nickname(nickname))) {
                is DomainResult.Success -> onQuickKnockSent(result.data)
                is DomainResult.Failure -> {
                    DebugLog.event("quick knock failed: ${result.error::class.simpleName}")
                    notifications.quickKnockFailed(nickname, result.error)
                }
            }
            quickKnocks.remove(nickname)
            stopSessionIfIdle()
        }
        quickKnocks[nickname] = job
        job.start()
    }

    private suspend fun onQuickKnockSent(outcome: KnockOutcome) {
        val nickname = outcome.request.config.nickname.value
        when (val plan = planTimer(outcome.request)) {
            is KnockTimerPlan.KeepOpen -> startSession(sessions.put(plan.request), plan.request, plan.seconds)
            is KnockTimerPlan.Countdown -> {
                notifications.showCountdown(nickname, plan.seconds)
                openPorts.opened(nickname, plan.seconds, keptOpen = false)
            }

            null -> notifications.quickKnockSent(nickname)
        }
    }

    private fun startSession(sessionId: Int, request: KnockRequest, seconds: Int) {
        val nickname = request.config.nickname.value
        val notification = notifications.keepOpen(nickname, seconds, actions(sessionId))
        foregroundNotifications[sessionId] = notification
        ServiceCompat.startForeground(this, sessionId, notification, foregroundType())
        openPorts.opened(nickname, seconds, keptOpen = true)
        jobs[sessionId]?.cancel()
        val schedule = ReknockSchedule(
            intervalMs = (seconds - REKNOCK_MARGIN_S).coerceAtLeast(MIN_INTERVAL_S) * MILLIS,
            retryMs = NETWORK_RETRY_MS,
        )
        schedules[sessionId] = schedule
        jobs[sessionId] =
            lifecycleScope.launch { reknockLoop(sessionId, request, schedule, seconds) }
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
                    val notification = notifications.keepOpen(nickname, seconds, actions(sessionId))
                    foregroundNotifications[sessionId] = notification
                    notifications.notify(sessionId, notification)
                    openPorts.opened(nickname, seconds, keptOpen = true)
                    true
                }
            }
        }
        notifications.keepOpenFailed(nickname)
        stopSession(sessionId)
    }


    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val active = connectivity.activeNetwork
        val detector = NetworkChangeDetector(
            active,
            active?.let(connectivity::getNetworkCapabilities)?.physicalTransports().orEmpty(),
        )
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                if (detector.onCapabilitiesChanged(
                        network,
                        capabilities.physicalTransports()
                    )
                ) onNetworkChanged()
            }

            override fun onLost(network: Network) = detector.onLost(network)
        }
        try {

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
        foregroundNotifications.remove(sessionId)
        sessions.remove(sessionId)?.let { openPorts.closed(it.config.nickname.value) }
        stopSessionIfIdle()
        notifications.cancel(sessionId)
    }

    private fun stopSessionIfIdle() {
        val knocking = quickKnocks.keys.lastOrNull()
        val session = foregroundNotifications.entries.lastOrNull()
        when {
            knocking != null -> ServiceCompat.startForeground(
                this, QUICK_KNOCK_ID, notifications.knocking(knocking), foregroundType(),
            )

            session != null -> {
                ServiceCompat.startForeground(this, session.key, session.value, foregroundType())
                notifications.cancel(QUICK_KNOCK_ID)
            }

            else -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                notifications.cancel(QUICK_KNOCK_ID)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        networkCallback?.let {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(
                it
            )
        }
        networkCallback = null
        jobs.keys.forEach { id -> sessions.remove(id)?.let { openPorts.closed(it.config.nickname.value) } }
        jobs.clear()
        schedules.clear()
        foregroundNotifications.clear()
        quickKnocks.clear()
        super.onDestroy()
    }

    private fun actions(sessionId: Int) = KeepOpenActions(
        stop = sessionIntent(sessionId, ACTION_STOP),
        extend = sessionIntent(sessionId, ACTION_EXTEND),
    )

    private fun sessionIntent(sessionId: Int, action: String): PendingIntent = PendingIntent.getService(
        this,
        sessionId * 2 + if (action == ACTION_EXTEND) 1 else 0,
        Intent(this, KeepOpenService::class.java).setAction(action)
            .putExtra(EXTRA_SESSION, sessionId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    companion object {
        private const val ACTION_STOP = "fr.gildasledrogoff.fwknop.action.STOP_KEEP_OPEN"
        private const val ACTION_EXTEND = "fr.gildasledrogoff.fwknop.action.EXTEND_KEEP_OPEN"
        private const val ACTION_QUICK_KNOCK = "fr.gildasledrogoff.fwknop.action.QUICK_KNOCK"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_NICKNAME = "nickname"
        private const val QUICK_KNOCK_ID = 0x0B0C
        private const val REKNOCK_MARGIN_S = 10L
        private const val MIN_INTERVAL_S = 5L
        private const val MILLIS = 1_000L
        private const val NETWORK_SETTLE_MS = 2_000L
        private const val NETWORK_RETRY_MS = 5_000L
        fun startIntent(context: Context, sessionId: Int, seconds: Int): Intent =
            Intent(context, KeepOpenService::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_SECONDS, seconds)

        fun quickKnockIntent(context: Context, nickname: String): Intent =
            Intent(context, KeepOpenService::class.java)
                .setAction(ACTION_QUICK_KNOCK)
                .putExtra(EXTRA_NICKNAME, nickname)
                .setData(Uri.fromParts("fwknop", nickname, null))
    }
}


private val PHYSICAL_TRANSPORTS = intArrayOf(
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_BLUETOOTH,
)

private fun NetworkCapabilities.physicalTransports(): Set<Int> =
    PHYSICAL_TRANSPORTS.filter(::hasTransport).toSet()
