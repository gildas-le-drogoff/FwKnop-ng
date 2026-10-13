package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicIpRepositoryImpl @Inject constructor(
    private val probes: SourceIpProbes,
    private val networkPath: NetworkPath,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) : PublicIpRepository {
    override suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError> {
        val settings = settingsRepository.settings.first()
        return withContext(dispatchers.io) {
            val route = networkPath.current()
            if (route.bypassesVpn) DebugLog.event("public ip lookup bypasses VPN")
            firstAccepted(probes.towards(InetAddress.getByName(serverIp), settings, route))
        }
    }

    private fun firstAccepted(candidates: List<SourceIpProbe>): DomainResult<ResolvedSourceIp, KnockError> {
        val failures = mutableListOf<String>()
        for (probe in candidates) {
            attempt(probe).fold(
                onSuccess = { return DomainResult.Success(ResolvedSourceIp(it.literal(), probe.method)) },
                onFailure = {
                    DebugLog.failure("public ip lookup", it)
                    failures += "${probe.method.describe()}: ${it.message ?: it.javaClass.simpleName}"
                },
            )
        }
        return DomainResult.Failure(KnockError.SourceIpUnresolved(failures.joinToString(" / ")))
    }

    
    private fun attempt(probe: SourceIpProbe): Result<InetAddress> = try {
        val address = probe.run()
        if (probe.accepts(address)) {
            Result.success(address)
        } else {
            Result.failure(IOException("unusable address ${address.literal()}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: SecurityException) {
        Result.failure(e)
    }

    
    private fun InetAddress.literal(): String = hostAddress.orEmpty().substringBefore('%')

    private fun SourceIpMethod.describe(): String = when (this) {
        SourceIpMethod.Kernel -> "kernel"
        is SourceIpMethod.Stun -> "STUN $server"
        is SourceIpMethod.Https -> "HTTPS $host"
        SourceIpMethod.Dns -> "DNS OpenDNS"
        else -> toString()
    }
}
