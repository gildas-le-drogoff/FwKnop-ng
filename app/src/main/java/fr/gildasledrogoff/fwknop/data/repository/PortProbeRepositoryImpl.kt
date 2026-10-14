package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.domain.repository.PortProbeRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

/** Uses [NetworkPath] so the probe leaves from the address the rule was opened for, even under a VPN. */
@Singleton
class PortProbeRepositoryImpl @Inject constructor(
    private val networkPath: NetworkPath,
    private val dispatchers: DispatcherProvider,
) : PortProbeRepository {
    override suspend fun isTcpReachable(address: String, port: Int): Boolean = withContext(dispatchers.io) {
        try {
            networkPath.current().socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS) }
            true
        } catch (e: IOException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_500
    }
}
