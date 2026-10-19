// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.QuickAccessCatalog
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessEntry
import kotlinx.coroutines.flow.Flow

interface QuickAccessRepository {
    val catalog: Flow<QuickAccessCatalog>
    suspend fun syncEntries(entries: List<QuickAccessEntry>)
    suspend fun setFavorite(nickname: String?)
    suspend fun recordKnock(nickname: String, sent: Boolean, atMillis: Long)
    suspend fun clear()
}
