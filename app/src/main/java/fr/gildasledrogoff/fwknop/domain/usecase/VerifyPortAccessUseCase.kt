// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.PortProbeRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject


class VerifyPortAccessUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val transport: SpaTransportRepository,
    private val probe: PortProbeRepository,
) {
    suspend operator fun invoke(outcome: KnockOutcome): PortCheck? {
        if (!settings.settings.first().portCheckEnabled) return null
        val config = outcome.request.config
        val ports = config.access.probedTcpPorts().takeIf { it.isNotEmpty() } ?: return null
        val serverIp = when (val resolved =
            transport.resolveHost(config.server.host, outcome.source.address)) {
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
