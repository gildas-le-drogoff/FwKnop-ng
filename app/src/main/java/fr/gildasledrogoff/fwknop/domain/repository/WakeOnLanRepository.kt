package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.MacAddress

interface WakeOnLanRepository {
    /** [host] may be a unicast address, a hostname or a broadcast address. */
    suspend fun sendMagicPacket(mac: MacAddress, host: String, port: Int): DomainResult<Unit, KnockError>
}
