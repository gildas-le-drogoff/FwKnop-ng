package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaPacketEncoder
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaPacketRepositoryImpl @Inject constructor(
    private val encoder: SpaPacketEncoder,
    private val dispatchers: DispatcherProvider,
) : SpaPacketRepository {
    override suspend fun buildPacket(params: SpaPacketParams): DomainResult<String, KnockError> =
        generate { encoder.rijndaelPacket(params) }

    override suspend fun buildGpgPayload(params: SpaPacketParams): DomainResult<String, KnockError> =
        generate { encoder.gpgPayload(params) }

    override suspend fun finalizeGpgPacket(
        params: SpaPacketParams,
        cipher: String
    ): DomainResult<String, KnockError> =
        withContext(dispatchers.default) { encoder.gpgPacket(params, cipher) }

    private suspend fun generate(block: () -> String?): DomainResult<String, KnockError> =
        withContext(dispatchers.default) {
            block()?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(KnockError.PacketGeneration)
        }
}
