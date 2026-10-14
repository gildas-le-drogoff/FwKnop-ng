// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.model.AutoLockDelay
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.usecase.LockVaultUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.utils.helpers.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLock @Inject constructor(
    observeVault: ObserveVaultStatusUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val lockVault: LockVaultUseCase,
    private val clock: MonotonicClock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val vault = observeVault()

    @Volatile
    private var delay = AutoLockDelay.NEVER

    @Volatile
    private var backgroundedAt: Long? = null
    private var timer: Job? = null

    init {
        scope.launch { observeSettings().collect { delay = it.autoLockDelay } }
    }

    fun register(lifecycle: Lifecycle) = lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()
        override fun onStop(owner: LifecycleOwner) = onBackground()
    })

    fun onBackground() {
        backgroundedAt = clock.elapsedMillis()
        val millis = delayMillis() ?: return
        timer?.cancel()
        timer = scope.launch {
            delay(millis)
            lock()
        }
    }

    fun onForeground() {
        lockIfExpired()
        timer?.cancel()
        timer = null
        backgroundedAt = null
    }

    fun lockIfExpired() {
        val since = backgroundedAt ?: return
        val millis = delayMillis() ?: return
        if (clock.elapsedMillis() - since >= millis) lock()
    }

    private fun lock() {
        if (vault.value.state == VaultState.UNLOCKED) lockVault()
    }

    private fun delayMillis(): Long? =
        if (vault.value.lockEnabled) delay.seconds?.times(MILLIS) else null

    private companion object {
        const val MILLIS = 1_000L
    }
}
