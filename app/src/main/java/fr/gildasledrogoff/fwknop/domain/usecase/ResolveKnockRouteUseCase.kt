package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockObserver
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockRoute
import fr.gildasledrogoff.fwknop.domain.model.KnockStep
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.model.flatMap
import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import fr.gildasledrogoff.fwknop.domain.model.map
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
    ): DomainResult<KnockRoute, KnockError> {
        val host = request.config.server.host
        val declared = when (val source = request.config.sourceIp) {
            SourceIp.Resolve -> return resolveFromServer(host, observer)
            SourceIp.Source -> source(SourceIp.ANY_ADDRESS, SourceIpMethod.PacketSource)
            is SourceIp.Fixed -> source(source.address, SourceIpMethod.Fixed)
            SourceIp.Prompt -> request.overrides.sourceIp?.let { source(it, SourceIpMethod.Prompted) }
                ?: DomainResult.Failure(KnockError.SourceIpMissing)
        }
        return declared.flatMap(::requireIp).flatMap { source ->
            observer.onStep(KnockStep.SourceResolved(source))
            transport.resolveHost(host, source.address).map { serverIp ->
                observer.onStep(KnockStep.ServerResolved(host, listOf(serverIp)))
                KnockRoute(source, serverIp)
            }
        }
    }

    
    private suspend fun resolveFromServer(host: String, observer: KnockObserver): DomainResult<KnockRoute, KnockError> =
        transport.resolveHostFamilies(host).flatMap { serverIps ->
            observer.onStep(KnockStep.ServerResolved(host, serverIps))
            firstResolved(serverIps, observer)
        }

    private suspend fun firstResolved(
        serverIps: List<String>,
        observer: KnockObserver,
    ): DomainResult<KnockRoute, KnockError> {
        val failures = mutableListOf<KnockError>()
        for (serverIp in serverIps) {
            when (val source = publicIp.resolvePublicIp(serverIp)) {
                is DomainResult.Success -> {
                    observer.onStep(KnockStep.SourceResolved(source.data))
                    return DomainResult.Success(KnockRoute(source.data, serverIp))
                }
                is DomainResult.Failure -> failures += source.error
            }
        }
        return DomainResult.Failure(failures.merged())
    }

    private fun List<KnockError>.merged(): KnockError = singleOrNull()
        ?: KnockError.SourceIpUnresolved(joinToString(" / ") { (it as? KnockError.SourceIpUnresolved)?.detail ?: "$it" })

    private fun source(address: String, method: SourceIpMethod) =
        DomainResult.Success(ResolvedSourceIp(address, method))

    private fun requireIp(source: ResolvedSourceIp): DomainResult<ResolvedSourceIp, KnockError> =
        if (source.address.isIpAddress()) {
            DomainResult.Success(source)
        } else {
            DomainResult.Failure(KnockError.SourceIpInvalid)
        }
}
