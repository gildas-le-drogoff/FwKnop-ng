// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*

interface PublicIpRepository {
    suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError>


    suspend fun resolvePublicIp(
        serverIp: String,
        session: NetworkSession,
    ): DomainResult<ResolvedSourceIp, KnockError> = resolvePublicIp(serverIp)


    suspend fun resolvePublicIp(
        serverIp: String,
        session: NetworkSession,
        observer: KnockObserver,
    ): DomainResult<ResolvedSourceIp, KnockError> = resolvePublicIp(serverIp, session)
}

interface SpaPacketRepository {
    suspend fun buildPacket(params: SpaPacketParams): DomainResult<String, KnockError>
    suspend fun buildGpgPayload(params: SpaPacketParams): DomainResult<String, KnockError>
    suspend fun finalizeGpgPacket(
        params: SpaPacketParams,
        cipher: String
    ): DomainResult<String, KnockError>
}

interface SpaTransportRepository {
    suspend fun resolveHost(host: String, sourceIp: String): DomainResult<String, KnockError>
    suspend fun resolveHostFamilies(host: String): DomainResult<List<String>, KnockError>
    suspend fun sendPacket(packet: String, target: KnockTarget): DomainResult<Unit, KnockError>


    suspend fun knockPort(target: KnockTarget): DomainResult<Unit, KnockError>


    fun openSession(): NetworkSession = NetworkSession.Unpinned

    suspend fun resolveHost(
        host: String,
        sourceIp: String,
        session: NetworkSession,
    ): DomainResult<String, KnockError> = resolveHost(host, sourceIp)

    suspend fun resolveHostFamilies(
        host: String,
        session: NetworkSession,
    ): DomainResult<List<String>, KnockError> = resolveHostFamilies(host)

    suspend fun sendPacket(
        packet: String,
        target: KnockTarget,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> = sendPacket(packet, target)

    suspend fun knockPort(
        target: KnockTarget,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> = knockPort(target)


    suspend fun bypassesVpn(serverIp: String, session: NetworkSession): Boolean = false
}

interface PortProbeRepository {

    suspend fun isTcpReachable(address: String, port: Int): Boolean
}

interface GpgRepository {
    suspend fun encrypt(
        plaintext: String,
        keys: Encryption.Gpg,
        resume: InteractionId?,
    ): DomainResult<String, GpgError>

    suspend fun selectKey(
        role: GpgKeyRole,
        resume: InteractionId?
    ): DomainResult<GpgKeyId, GpgError>
}

interface KnockTimerScheduler {
    fun canNotify(): Boolean
    fun schedule(plan: KnockTimerPlan): Boolean
}
