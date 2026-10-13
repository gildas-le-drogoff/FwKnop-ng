package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyRole
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerPlan
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams

interface PublicIpRepository {
    
    suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError>
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
