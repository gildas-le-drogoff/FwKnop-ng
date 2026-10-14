package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockOutcome
import fr.gildasledrogoff.fwknop.domain.model.PortCheck
import fr.gildasledrogoff.fwknop.domain.model.portAccess
import fr.gildasledrogoff.fwknop.domain.repository.PortProbeRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Tries TCP connections to the ports the knock opened, for a few seconds, to tell whether the rule
 * took effect. Returns null when disabled or when nothing can be probed (UDP only, server command).
 */
class VerifyPortAccessUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val transport: SpaTransportRepository,
    private val probe: PortProbeRepository,
) {
    suspend operator fun invoke(outcome: KnockOutcome): PortCheck? {
        if (!settings.settings.first().portCheckEnabled) return null
        val config = outcome.request.config
        val ports = config.access.probedTcpPorts().takeIf { it.isNotEmpty() } ?: return null
        val serverIp = when (val resolved = transport.resolveHost(config.server.host, outcome.source.address)) {
            is DomainResult.Success -> resolved.data
            is DomainResult.Failure -> return null
        }
        var closed = ports
        withTimeoutOrNull(WINDOW_MS) {
            while (true) {
                closed = closed.filterNot { probe.isTcpReachable(serverIp, it) }
                if (closed.isEmpty()) break
                delay(RETRY_MS)
            }
        }
        return if (closed.isEmpty()) PortCheck.Reachable(ports) else PortCheck.Closed(closed)
    }

    private companion object {
        const val WINDOW_MS = 6_000L
        const val RETRY_MS = 1_000L
    }
}

private val TCP_PORT = Regex("^tcp/(\\d{1,5})$", RegexOption.IGNORE_CASE)

/** With NAT access the client reaches the forwarded service through the NAT port of the server. */
internal fun AccessRule.probedTcpPorts(): List<Int> {
    val access = portAccess ?: return emptyList()
    val tcp = access.ports.split(',')
        .mapNotNull { TCP_PORT.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .filter { it in 1..MAX_PORT }
        .distinct()
    if (tcp.isEmpty()) return emptyList()
    return when (this) {
        is AccessRule.NatAccess -> listOf(natPort)
        is AccessRule.LocalNatAccess -> listOf(natPort)
        else -> tcp
    }
}

private const val MAX_PORT = 65_535
