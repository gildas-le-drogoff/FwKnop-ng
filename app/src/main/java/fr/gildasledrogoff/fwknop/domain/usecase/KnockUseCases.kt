// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.GpgRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.random.Random

class GetRequiredPromptsUseCase @Inject constructor() {
    operator fun invoke(config: SpaConfig): List<KnockPrompt> = buildList {
        if (config.method != KnockMethod.Spa) return@buildList
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

        val session = transport.openSession()
        return resolveRoute(request, observer, session).flatMap { route ->
            onRoute(route.source)
            observer.onStep(
                KnockStep.RouteSelected(route, transport.bypassesVpn(route.serverIp, session))
            )
            when (val method = request.config.method) {
                KnockMethod.Spa -> sendSpa(request, route, observer, session)
                is KnockMethod.PortSequence -> knockSequence(
                    request,
                    method,
                    route.serverIp,
                    observer,
                    session,
                )
            }.map { KnockOutcome(request, route.source) }
        }
    }

    private suspend fun sendSpa(
        request: KnockRequest,
        route: KnockRoute,
        observer: KnockObserver,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> =
        buildPacket(request, route.source.address, route.serverIp).flatMap { packet ->
            observer.onStep(KnockStep.PacketBuilt(packet.toByteArray(Charsets.UTF_8).size))
            val target = request.config.server.toTarget(route.serverIp, random)
            observer.onStep(KnockStep.Sending(target))
            transport.sendPacket(packet, target, session)
        }

    private suspend fun knockSequence(
        request: KnockRequest,
        method: KnockMethod.PortSequence,
        serverIp: String,
        observer: KnockObserver,
        session: NetworkSession,
    ): DomainResult<Unit, KnockError> {
        method.ports.forEachIndexed { index, port ->
            if (index > 0) delay(method.delayMs.toLong())
            val target = KnockTarget(serverIp, request.config.server.host, port.port, port.protocol)
            observer.onStep(KnockStep.Sending(target))
            val knocked = transport.knockPort(target, session)
            if (knocked is DomainResult.Failure) return knocked
        }

        delay(SEQUENCE_SETTLE_MS)
        return DomainResult.Success(Unit)
    }

    private companion object {
        const val SEQUENCE_SETTLE_MS = 1_000L
    }
}
