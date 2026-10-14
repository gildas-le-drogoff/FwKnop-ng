package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.model.KnockObserver
import fr.gildasledrogoff.fwknop.domain.model.KnockOutcome
import fr.gildasledrogoff.fwknop.domain.model.KnockStep
import fr.gildasledrogoff.fwknop.domain.model.KnockPrompt
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.flatMap
import fr.gildasledrogoff.fwknop.domain.model.map
import fr.gildasledrogoff.fwknop.domain.model.mapError
import fr.gildasledrogoff.fwknop.domain.repository.GpgRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import javax.inject.Inject
import kotlin.random.Random

class GetRequiredPromptsUseCase @Inject constructor() {
    operator fun invoke(config: SpaConfig): List<KnockPrompt> = buildList {
        val encryption = config.crypto.encryption
        if (encryption is Encryption.Rijndael && encryption.key.value.isEmpty()) add(KnockPrompt.KEY)
        if (config.sourceIp is SourceIp.Prompt) add(KnockPrompt.SOURCE_IP)
    }
}

class BuildSpaPacketUseCase @Inject constructor(
    private val packets: SpaPacketRepository,
    private val gpg: GpgRepository,
) {
    suspend operator fun invoke(
        request: KnockRequest,
        sourceIp: String,
        serverIp: String,
    ): DomainResult<String, KnockError> = when (val encryption = request.config.crypto.encryption) {
        is Encryption.Rijndael -> rijndaelKey(request, encryption).flatMap { key ->
            packets.buildPacket(request.toPacketParams(sourceIp, serverIp, key))
        }

        is Encryption.Gpg -> buildGpgPacket(request, encryption, sourceIp, serverIp)
    }

    private fun rijndaelKey(
        request: KnockRequest,
        encryption: Encryption.Rijndael,
    ): DomainResult<SecretKey, KnockError> {
        val key = request.overrides.key?.let { SecretKey(it, base64 = false) } ?: encryption.key
        return if (key.value.isEmpty()) DomainResult.Failure(KnockError.KeyMissing) else DomainResult.Success(
            key
        )
    }

    private suspend fun buildGpgPacket(
        request: KnockRequest,
        keys: Encryption.Gpg,
        sourceIp: String,
        serverIp: String,
    ): DomainResult<String, KnockError> {
        
        if (keys.recipient.value == 0L || keys.signer.value == 0L) {
            return DomainResult.Failure(KnockError.GpgKeysMissing)
        }
        val params = request.toPacketParams(sourceIp, serverIp, SecretKey("", base64 = false))
        return packets.buildGpgPayload(params).flatMap { payload ->
            gpg.encrypt(payload, keys, request.overrides.gpgInteraction)
                .mapError { KnockError.Gpg(it) }
                .flatMap { cipher -> packets.finalizeGpgPacket(params, cipher) }
        }
    }
}

class SendKnockUseCase @Inject constructor(
    private val resolveRoute: ResolveKnockRouteUseCase,
    private val buildPacket: BuildSpaPacketUseCase,
    private val transport: SpaTransportRepository,
    private val random: Random,
    private val history: KnockHistoryRepository,
) {
    suspend operator fun invoke(
        request: KnockRequest,
        observer: KnockObserver = KnockObserver.None,
    ): DomainResult<KnockOutcome, KnockError> {
        var source: ResolvedSourceIp? = null
        val result = send(request, observer) { source = it }
        // A pending GPG prompt is not an outcome: the knock is retried once the user answers it.
        val awaitsUser = result is DomainResult.Failure &&
            (result.error as? KnockError.Gpg)?.error is GpgError.InteractionRequired
        if (!awaitsUser) history.record(KnockLogEvent.of(request.config, source, result))
        return result
    }

    private suspend fun send(
        request: KnockRequest,
        observer: KnockObserver,
        onRoute: (ResolvedSourceIp) -> Unit,
    ): DomainResult<KnockOutcome, KnockError> {
        val server = request.config.server
        return resolveRoute(request, observer).flatMap { route ->
            onRoute(route.source)
            observer.onStep(KnockStep.RouteSelected(route))
            buildPacket(request, route.source.address, route.serverIp)
                .flatMap { packet ->
                    observer.onStep(KnockStep.PacketBuilt(packet.toByteArray(Charsets.UTF_8).size))
                    val target = server.toTarget(route.serverIp, random)
                    observer.onStep(KnockStep.Sending(target))
                    transport.sendPacket(packet, target)
                }
                .map { KnockOutcome(request, route.source) }
        }
    }
}
