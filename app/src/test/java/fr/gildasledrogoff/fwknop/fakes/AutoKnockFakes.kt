package fr.gildasledrogoff.fwknop.fakes

import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockMonitor
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockRepository
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccess
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccessStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class FakeAutoKnockRepository(initial: AutoKnockSettings = AutoKnockSettings()) : AutoKnockRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AutoKnockSettings> = state
    override suspend fun save(settings: AutoKnockSettings) {
        state.value = settings
    }
}

class FakeWifiNetworkRepository(var current: String? = null) : WifiNetworkRepository {
    val ssids = MutableSharedFlow<List<String?>>(replay = 1)
    override fun connectedSsids(): Flow<List<String?>> = ssids
    override suspend fun currentSsid(): String? = current
    override suspend fun awaitInternet(timeout: Duration): Boolean = true
}

class FakeLocationAccess(var status: LocationAccessStatus = LocationAccessStatus.GRANTED) : LocationAccess {
    override fun status() = status
}

class FakeAutoKnockMonitor : AutoKnockMonitor {
    var refreshes = 0
        private set

    override fun refresh() {
        refreshes++
    }
}
