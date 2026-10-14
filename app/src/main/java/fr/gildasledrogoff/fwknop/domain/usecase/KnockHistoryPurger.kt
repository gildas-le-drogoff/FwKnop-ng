package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Scheduled purge of the knock history. Not a WorkManager job: the history lives in the vault
 * database, unreachable while the vault is locked, so a background worker could not run it without
 * weakening the lock. Purges instead on unlock, on retention change, then periodically while unlocked
 * (record() also purges after each write).
 */
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
