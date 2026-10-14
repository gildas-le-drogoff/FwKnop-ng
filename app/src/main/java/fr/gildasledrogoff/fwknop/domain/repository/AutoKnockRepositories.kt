// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface AutoKnockRepository {
    val settings: Flow<AutoKnockSettings>
    suspend fun save(settings: AutoKnockSettings)
}

interface WifiNetworkRepository {

    fun connectedSsids(): Flow<List<String?>>


    suspend fun currentSsid(): String?


    suspend fun awaitInternet(timeout: Duration): Boolean
}


interface AutoKnockMonitor {

    fun refresh()
}
