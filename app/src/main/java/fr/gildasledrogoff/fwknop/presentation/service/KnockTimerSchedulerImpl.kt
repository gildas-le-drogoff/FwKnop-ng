package fr.gildasledrogoff.fwknop.presentation.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerPlan
import fr.gildasledrogoff.fwknop.domain.repository.KnockTimerScheduler
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepOpenSessionStore @Inject constructor() {
    private val counter = AtomicInteger(SESSION_ID_BASE)
    private val sessions = ConcurrentHashMap<Int, KnockRequest>()
    fun put(request: KnockRequest): Int = counter.incrementAndGet().also { sessions[it] = request }
    fun get(id: Int): KnockRequest? = sessions[id]
    fun remove(id: Int) = sessions.remove(id)

    private companion object {
        const val SESSION_ID_BASE = 1_000
    }
}

@Singleton
class KnockTimerSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifications: KnockNotifications,
    private val sessions: KeepOpenSessionStore,
) : KnockTimerScheduler {
    override fun canNotify(): Boolean = notifications.canNotify()

    override fun schedule(plan: KnockTimerPlan): Boolean = when (plan) {
        is KnockTimerPlan.Countdown -> {
            notifications.showCountdown(plan.nickname.value, plan.seconds)
            true
        }

        is KnockTimerPlan.KeepOpen -> startKeepOpen(plan)
    }

    
    
    private fun startKeepOpen(plan: KnockTimerPlan.KeepOpen): Boolean {
        val sessionId = sessions.put(plan.request)
        return try {
            ContextCompat.startForegroundService(
                context,
                KeepOpenService.startIntent(context, sessionId, plan.seconds)
            )
            true
        } catch (e: IllegalStateException) {
            DebugLog.failure("keep-open service start", e)
            sessions.remove(sessionId)
            false
        }
    }
}
