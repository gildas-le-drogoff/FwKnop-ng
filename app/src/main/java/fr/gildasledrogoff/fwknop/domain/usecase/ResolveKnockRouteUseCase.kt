// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import javax.inject.Inject

class ResolveKnockRouteUseCase @Inject constructor(
    private val publicIp: PublicIpRepository,
    private val transport: SpaTransportRepository,
) {
    suspend operator fun invoke(
        request: KnockRequest,
        observer: KnockObserver = KnockObserver.None,
        session: NetworkSession = transport.openSession(),
    ): DomainResult<KnockRoute, KnockError> {
        val host = request.config.server.host

        val sourceIp =
            if (request.config.method == KnockMethod.Spa) request.config.sourceIp else SourceIp.Source
        val declared = when (val source = sourceIp) {
            SourceIp.Resolve -> return resolveFromServer(host, observer, session)
            SourceIp.Source -> source(SourceIp.ANY_ADDRESS, SourceIpMethod.PacketSource)
            is SourceIp.Fixed -> source(source.address, SourceIpMethod.Fixed)
            SourceIp.Prompt -> request.overrides.sourceIp?.let {
                source(
                    it,
                    SourceIpMethod.Prompted
                )
            }
                ?: DomainResult.Failure(KnockError.SourceIpMissing)
        }
        return declared.flatMap(::requireIpv4).flatMap { source ->
            observer.onStep(KnockStep.SourceResolved(source))
            transport.resolveHost(host, source.address, session).map { serverIp ->
                observer.onStep(KnockStep.ServerResolved(host, listOf(serverIp)))
                KnockRoute(source, serverIp)
            }
        }
    }

    private suspend fun resolveFromServer(
        host: String,
        observer: KnockObserver,
        session: NetworkSession,
    ): DomainResult<KnockRoute, KnockError> =
        transport.resolveHostFamilies(host, session).flatMap { serverIps ->
            observer.onStep(KnockStep.ServerResolved(host, serverIps))

            val ipv4 = serverIps.filter { it.isIpv4Address() }
            if (ipv4.isEmpty()) {
                DomainResult.Failure(KnockError.ServerFamilyMismatch(host, SPA_FAMILY))
            } else {
                firstResolved(ipv4, observer, session)
            }
        }

    private suspend fun firstResolved(
        serverIps: List<String>,
        observer: KnockObserver,
        session: NetworkSession,
    ): DomainResult<KnockRoute, KnockError> {
        val failures = mutableListOf<Pair<String, KnockError>>()
        for (serverIp in serverIps) {
            when (val source = publicIp.resolvePublicIp(serverIp, session, observer).flatMap(::requireIpv4)) {
                is DomainResult.Success -> {
                    observer.onStep(KnockStep.SourceResolved(source.data))
                    return DomainResult.Success(KnockRoute(source.data, serverIp))
                }


                is DomainResult.Failure -> failures += serverIp to source.error
            }
        }
        return DomainResult.Failure(failures.merged())
    }


    private fun List<Pair<String, KnockError>>.merged(): KnockError = singleOrNull()?.second
        ?: KnockError.SourceIpUnresolved(joinToString(" / ") { (serverIp, error) ->
            (error as? KnockError.SourceIpUnresolved)?.detail ?: "towards $serverIp: $error"
        })

    private fun source(address: String, method: SourceIpMethod) =
        DomainResult.Success(ResolvedSourceIp(address, method))


    private fun requireIpv4(source: ResolvedSourceIp): DomainResult<ResolvedSourceIp, KnockError> =
        if (source.address.isIpv4Address()) {
            DomainResult.Success(source)
        } else {
            DomainResult.Failure(KnockError.SourceIpInvalid)
        }

    private companion object {
        const val SPA_FAMILY = "IPv4"
    }
}
