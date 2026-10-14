// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import android.system.ErrnoException
import android.system.OsConstants
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.remote.api.isUniqueLocal
import fr.gildasledrogoff.fwknop.data.remote.api.routeOf
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
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
    override fun openSession(): NetworkSession = networkPath.current()

    override suspend fun resolveHost(
        host: String,
        sourceIp: String,
    ): DomainResult<String, KnockError> = resolveHost(host, sourceIp, openSession())

    override suspend fun resolveHost(
        host: String,
        sourceIp: String,
        session: NetworkSession,
    ): DomainResult<String, KnockError> = resolving(host) {
        api.resolve(host, sourceIp.addressFamily(), networkPath.routeOf(session))
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(KnockError.ServerFamilyMismatch(host, sourceIp))
    }

    override suspend fun resolveHostFamilies(host: String): DomainResult<List<String>, KnockError> =
        resolveHostFamilies(host, openSession())

    override suspend fun resolveHostFamilies(
        host: String,
        session: NetworkSession,
    ): DomainResult<List<String>, KnockError> = resolving(host) {
        api.resolveOnePerFamily(host, networkPath.routeOf(session)).takeIf { it.isNotEmpty() }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(KnockError.ServerUnresolved(host))
    }

    override suspend fun bypassesVpn(serverIp: String, session: NetworkSession): Boolean =
        withContext(dispatchers.io) {
            runCatching { InetAddress.getByName(serverIp) }.getOrNull()
                ?.let { networkPath.routeOf(session).towards(it).bypassesVpn } ?: false
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
    ): DomainResult<Unit, KnockError> = sendPacket(packet, target, openSession())

    override suspend fun sendPacket(
        packet: String,
        target: KnockTarget,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> =
        transmitting(target) {
            send(packet, target, networkPath.routeOf(session).towards(target))
            delay(SETTLE_DELAY_MS)
        }

    override suspend fun knockPort(target: KnockTarget): DomainResult<Unit, KnockError> =
        knockPort(target, openSession())

    override suspend fun knockPort(
        target: KnockTarget,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> =
        transmitting(target) {
            val route = networkPath.routeOf(session).towards(target)
            when (target.protocol) {
                Protocol.TCP -> api.knockTcp(target.address, target.port, route)
                Protocol.UDP -> api.sendUdp("", target.address, target.port, route)
                Protocol.HTTP -> throw IllegalArgumentException("HTTP is not a knock protocol")
            }
        }

    private suspend fun transmitting(
        target: KnockTarget,
        block: suspend () -> Unit,
    ): DomainResult<Unit, KnockError> = withContext(dispatchers.io) {
        if (localNetwork.needsRequest() && target.address.isLocalNetwork()) {
            return@withContext DomainResult.Failure(KnockError.LocalNetworkPermissionRequired)
        }
        try {
            block()
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
            if (error.isPermissionDenied()) KnockError.LocalNetworkBlocked else KnockError.Transport(
                error
            )
        )
    }

    private fun String.isLocalNetwork(): Boolean {
        val ip = runCatching { InetAddress.getByName(this) }.getOrNull() ?: return false
        return ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isMulticastAddress || ip.isUniqueLocal()
    }


    private fun NetworkPath.Route.towards(target: KnockTarget) =
        towards(InetAddress.getByName(target.address))

    private fun Throwable.isPermissionDenied(): Boolean =
        generateSequence(this) { it.cause }.any { (it as? ErrnoException)?.errno == OsConstants.EPERM }

    private fun send(packet: String, target: KnockTarget, route: NetworkPath.Route) {
        when (target.protocol) {
            Protocol.UDP -> api.sendUdp(packet, target.address, target.port, route)
            Protocol.TCP -> api.sendTcp(packet, target.address, target.port, route)
            Protocol.HTTP -> api.sendHttp(packet, target.address, target.host, target.port, route)
        }
    }

    private companion object {
        const val SETTLE_DELAY_MS = 1_000L
    }
}
