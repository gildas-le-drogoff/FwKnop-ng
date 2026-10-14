// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import kotlinx.coroutines.flow.Flow


interface KnockHistoryRepository {

    fun observeHistory(): Flow<List<KnockLogEntry>>


    suspend fun record(event: KnockLogEvent)


    suspend fun purgeExpired()
    suspend fun clear()
}
