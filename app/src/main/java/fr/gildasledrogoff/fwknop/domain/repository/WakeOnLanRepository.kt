package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult

interface WakeOnLanRepository {
    /** [host] may be a unicast address, a hostname or a broadcast address. */
    suspend fun sendMagicPacket(packet: ByteArray, host: String, port: Int): DomainResult<Unit, KnockError>
}
