package fr.gildasledrogoff.fwknop.presentation.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
import kotlinx.coroutines.isActive
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
        jobs[sessionId] = lifecycleScope.launch { reknockLoop(sessionId, request, seconds) }
    }

    private suspend fun reknockLoop(sessionId: Int, request: KnockRequest, seconds: Int) {
        val nickname = request.config.nickname.value
        while (lifecycleScope.isActive) {
            delay((seconds - REKNOCK_MARGIN_S).coerceAtLeast(MIN_INTERVAL_S) * MILLIS)
            val result = sendKnock(request)
            if (result is DomainResult.Failure) {
                DebugLog.event("keep-open re-knock failed: ${result.error::class.simpleName}")
                notifications.keepOpenFailed(nickname)
                stopSession(sessionId)
                return
            }
            notifications.notify(
                sessionId,
                notifications.keepOpen(nickname, seconds, stopIntent(sessionId))
            )
        }
        stopSession(sessionId)
    }

    private fun stopSession(sessionId: Int) {
        jobs.remove(sessionId)?.cancel()
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
        jobs.keys.forEach(sessions::remove)
        jobs.clear()
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
        fun startIntent(context: Context, sessionId: Int, seconds: Int): Intent =
            Intent(context, KeepOpenService::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_SECONDS, seconds)
    }
}
