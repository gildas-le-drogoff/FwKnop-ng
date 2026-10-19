// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.model.KnockLogOutcome
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.QuickAccessRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingKnockHistory @Inject constructor(
    private val history: KnockHistoryRepositoryImpl,
    private val quickAccess: QuickAccessRepository,
    private val clock: Clock,
) : KnockHistoryRepository by history {
    override suspend fun record(event: KnockLogEvent) {
        history.record(event)
        quickAccess.recordKnock(event.nickname.value, event.outcome == KnockLogOutcome.Sent, clock.millis())
    }
}
