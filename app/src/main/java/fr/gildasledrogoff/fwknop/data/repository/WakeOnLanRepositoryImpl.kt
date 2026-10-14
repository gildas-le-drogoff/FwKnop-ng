// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import android.system.ErrnoException
import android.system.OsConstants
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.isUniqueLocal
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.repository.WakeOnLanRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeOnLanRepositoryImpl @Inject constructor(
    private val networkPath: NetworkPath,
    private val dispatchers: DispatcherProvider,
    private val localNetwork: LocalNetworkAccess,
) : WakeOnLanRepository {
    override suspend fun sendMagicPacket(
        packet: ByteArray,
        host: String,
        port: Int,
    ): DomainResult<Unit, KnockError> = withContext(dispatchers.io) {
        val route = networkPath.current()

        val address = try {
            route.lookup(host)
                .let { all -> all.firstOrNull { it is Inet4Address } ?: all.firstOrNull() }
        } catch (e: IOException) {
            DebugLog.failure("wake-on-lan resolution", e)
            null
        } catch (e: SecurityException) {
            DebugLog.failure("wake-on-lan resolution", e)
            null
        } ?: return@withContext DomainResult.Failure(KnockError.ServerUnresolved(host))
        if (localNetwork.needsRequest() && address.isLocalNetwork()) {
            return@withContext DomainResult.Failure(KnockError.LocalNetworkPermissionRequired)
        }
        try {
            route.datagramSocket().use { socket ->
                socket.broadcast = true

                repeat(SEND_COUNT) {
                    socket.send(
                        DatagramPacket(
                            packet,
                            packet.size,
                            address,
                            port
                        )
                    )
                }
            }
            DomainResult.Success(Unit)
        } catch (e: IOException) {
            transportFailure(e)
        } catch (e: SecurityException) {
            transportFailure(e)
        }
    }

    private fun transportFailure(error: Exception): DomainResult<Unit, KnockError> {
        DebugLog.failure("wake-on-lan send", error)
        val denied = generateSequence<Throwable>(error) { it.cause }
            .any { (it as? ErrnoException)?.errno == OsConstants.EPERM }
        return DomainResult.Failure(
            if (denied) KnockError.LocalNetworkBlocked else KnockError.Transport(
                error
            )
        )
    }

    private fun InetAddress.isLocalNetwork(): Boolean =
        isSiteLocalAddress || isLinkLocalAddress || isMulticastAddress || isUniqueLocal() || isLimitedBroadcast()

    private fun InetAddress.isLimitedBroadcast(): Boolean =
        this is Inet4Address && address.all { it == 0xFF.toByte() }

    private companion object {
        const val SEND_COUNT = 3
    }
}
