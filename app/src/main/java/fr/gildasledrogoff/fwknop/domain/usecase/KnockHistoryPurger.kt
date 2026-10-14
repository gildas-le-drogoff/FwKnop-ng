// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours


class KnockHistoryPurger @Inject constructor(
    private val vault: VaultRepository,
    private val settings: SettingsRepository,
    private val history: KnockHistoryRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, interval: Duration = INTERVAL): Job = scope.launch {
        val unlocked = vault.status.map { it.state == VaultState.UNLOCKED }.distinctUntilChanged()
        val retention = settings.settings.map { it.knockHistoryRetention }.distinctUntilChanged()
        combine(unlocked, retention) { open, _ -> open }
            .flatMapLatest { open ->
                if (!open) emptyFlow() else flow {
                    while (true) {
                        emit(Unit)
                        delay(interval)
                    }
                }
            }
            .collect { history.purgeExpired() }
    }

    companion object {
        val INTERVAL = 6.hours
    }
}
