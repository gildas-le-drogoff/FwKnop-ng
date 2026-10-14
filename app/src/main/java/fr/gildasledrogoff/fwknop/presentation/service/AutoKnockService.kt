// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.Ssids
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import fr.gildasledrogoff.fwknop.domain.usecase.AutoKnockUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.DetectTrustedNetworkExitUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveAutoKnockSettingsUseCase
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@AndroidEntryPoint
class AutoKnockService : LifecycleService() {
    @Inject
    lateinit var observeSettings: ObserveAutoKnockSettingsUseCase

    @Inject
    lateinit var wifi: WifiNetworkRepository

    @Inject
    lateinit var detectExit: DetectTrustedNetworkExitUseCase

    @Inject
    lateinit var autoKnock: AutoKnockUseCase

    @Inject
    lateinit var notifications: AutoKnockNotifications
    private var monitor: Job? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            AutoKnockNotifications.MONITOR_ID,
            notifications.monitoring(),
            type
        )
        if (monitor == null) monitor = lifecycleScope.launch { monitor() }
        return START_STICKY
    }

    private suspend fun monitor() {
        observeSettings().collectLatest { settings ->
            val nickname = settings.nickname
            if (!settings.armed || nickname == null) {
                stop()
                return@collectLatest
            }
            val presence = wifi.connectedSsids().map { Ssids.presence(it, settings.trustedSsids) }
            detectExit(presence, SETTLE).collect { knock(Nickname(nickname)) }
        }
    }

    private suspend fun knock(nickname: Nickname) {

        if (!wifi.awaitInternet(INTERNET_TIMEOUT)) DebugLog.event("auto-knock: no validated network, trying anyway")
        when (val result = autoKnock(nickname)) {
            is DomainResult.Success -> notifications.sent(nickname.value)
            is DomainResult.Failure -> {
                DebugLog.event("auto-knock failed: ${result.error::class.simpleName}")
                notifications.failed(nickname.value, result.error)
            }
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private val SETTLE = 5.seconds
        private val INTERNET_TIMEOUT = 20.seconds
        fun intent(context: Context): Intent = Intent(context, AutoKnockService::class.java)
    }
}
