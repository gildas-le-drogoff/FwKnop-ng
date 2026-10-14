package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.KnockHistoryRetention
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveKnockHistoryUseCase @Inject constructor(private val repository: KnockHistoryRepository) {
    operator fun invoke(): Flow<List<KnockLogEntry>> = repository.observeHistory()
}

class ClearKnockHistoryUseCase @Inject constructor(private val repository: KnockHistoryRepository) {
    suspend operator fun invoke() = repository.clear()
}

/** The purge follows on its own: [KnockHistoryPurger] watches the retention setting. */
class SetKnockHistoryRetentionUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(retention: KnockHistoryRetention) {
        val current = settings.settings.first()
        if (current.knockHistoryRetention != retention) {
            settings.saveSettings(current.copy(knockHistoryRetention = retention))
        }
    }
}
