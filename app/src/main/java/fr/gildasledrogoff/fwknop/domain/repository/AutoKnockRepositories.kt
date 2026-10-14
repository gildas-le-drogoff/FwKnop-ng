package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface AutoKnockRepository {
    val settings: Flow<AutoKnockSettings>
    suspend fun save(settings: AutoKnockSettings)
}

interface WifiNetworkRepository {
    /** Names of the connected Wi-Fi networks, null for a network whose name Android hides. Emits on change. */
    fun connectedSsids(): Flow<List<String?>>

    /** Name of the current Wi-Fi network, null when not connected or hidden. */
    suspend fun currentSsid(): String?

    /** Waits up to [timeout] for a validated default network; false if none showed up. */
    suspend fun awaitInternet(timeout: Duration): Boolean
}

/** Starts or stops the background monitoring to match the settings and the granted permissions. */
interface AutoKnockMonitor {
    /** After a permission change, which the settings do not reflect. */
    fun refresh()
}
