// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.QuickAccessRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveQuickAccessUseCase @Inject constructor(private val repository: QuickAccessRepository) {
    operator fun invoke(): Flow<QuickAccessCatalog> = repository.catalog
}

class SetFavoriteConfigUseCase @Inject constructor(private val repository: QuickAccessRepository) {
    suspend operator fun invoke(nickname: String?) = repository.setFavorite(nickname)
}

class SyncQuickAccessUseCase @Inject constructor(
    private val repository: QuickAccessRepository,
    private val autoKnockable: IsAutoKnockableUseCase,
) {
    suspend operator fun invoke(configs: List<SpaConfig>) = repository.syncEntries(
        configs.map { QuickAccessEntry(it.nickname.value, it.kind, autoKnockable(it), it.icon) }
    )
}

class PlanUnattendedTimerUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(request: KnockRequest): KnockTimerPlan? =
        if (settings.settings.first().notificationsEnabled) request.timerPlan() else null
}
