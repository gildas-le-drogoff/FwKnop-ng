package fr.gildasledrogoff.fwknop.data.repository

import android.system.ErrnoException
import android.system.OsConstants
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.remote.api.isUniqueLocal
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaTransportRepositoryImpl @Inject constructor(
    private val api: SpaTransportApi,
    private val networkPath: NetworkPath,
    private val dispatchers: DispatcherProvider,
    private val localNetwork: LocalNetworkAccess,
) : SpaTransportRepository {
    override suspend fun resolveHost(
        host: String,
        sourceIp: String,
    ): DomainResult<String, KnockError> = resolving(host) {
        api.resolve(host, sourceIp.addressFamily(), networkPath.current())?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(KnockError.ServerFamilyMismatch(host, sourceIp))
    }

    override suspend fun resolveHostFamilies(host: String): DomainResult<List<String>, KnockError> =
        resolving(host) {
            api.resolveOnePerFamily(host, networkPath.current()).takeIf { it.isNotEmpty() }?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(KnockError.ServerUnresolved(host))
        }

    private suspend fun <T> resolving(
        host: String,
        block: () -> DomainResult<T, KnockError>,
    ): DomainResult<T, KnockError> = withContext(dispatchers.io) {
        try {
            block()
        } catch (e: IOException) {
            unresolved(host, e)
        } catch (e: IllegalArgumentException) {
            unresolved(host, e)
        } catch (e: SecurityException) {
            unresolved(host, e)
        }
    }

    private fun unresolved(host: String, error: Exception): DomainResult<Nothing, KnockError> {
        DebugLog.failure("server resolution", error)
        return DomainResult.Failure(KnockError.ServerUnresolved(host))
    }

    private fun String.addressFamily(): Class<out InetAddress> = when {
        this == SourceIp.ANY_ADDRESS -> InetAddress::class.java
        
        InetAddress.getByName(this) is Inet6Address -> Inet6Address::class.java
        else -> Inet4Address::class.java
    }

    override suspend fun sendPacket(
        packet: String,
        target: KnockTarget
    ): DomainResult<Unit, KnockError> =
        withContext(dispatchers.io) {
            if (localNetwork.needsRequest() && target.address.isLocalNetwork()) {
                return@withContext DomainResult.Failure(KnockError.LocalNetworkPermissionRequired)
            }
            try {
                send(packet, target)
                delay(SETTLE_DELAY_MS)
                DomainResult.Success(Unit)
            } catch (e: IOException) {
                transportFailure(e)
            } catch (e: IllegalArgumentException) {
                transportFailure(e)
            } catch (e: SecurityException) {
                transportFailure(e)
            }
        }

    private fun transportFailure(error: Exception): DomainResult<Unit, KnockError> {
        DebugLog.failure("packet send", error)
        return DomainResult.Failure(
            if (error.isPermissionDenied()) KnockError.LocalNetworkBlocked else KnockError.Transport(error)
        )
    }

    
    private fun String.isLocalNetwork(): Boolean {
        val ip = runCatching { InetAddress.getByName(this) }.getOrNull() ?: return false
        return ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isMulticastAddress || ip.isUniqueLocal()
    }

    private fun Throwable.isPermissionDenied(): Boolean =
        generateSequence(this) { it.cause }.any { (it as? ErrnoException)?.errno == OsConstants.EPERM }

    private fun send(packet: String, target: KnockTarget) {
        val route = networkPath.current()
        when (target.protocol) {
            Protocol.UDP -> api.sendUdp(packet, target.address, target.port, route)
            Protocol.TCP -> api.sendTcp(packet, target.address, target.port, route)
            Protocol.HTTP -> api.sendHttp(packet, target.address, target.host, route)
        }
    }

    private companion object {
        const val SETTLE_DELAY_MS = 1_000L
    }
}
