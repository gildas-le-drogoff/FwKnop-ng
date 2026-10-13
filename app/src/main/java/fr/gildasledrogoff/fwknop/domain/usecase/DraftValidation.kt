package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.AccessDraft
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Failure
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Success
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.ServerDraft
import fr.gildasledrogoff.fwknop.domain.model.ServerEndpoint
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpDraft
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMode
import fr.gildasledrogoff.fwknop.domain.model.flatMap
import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import fr.gildasledrogoff.fwknop.domain.model.isPort
import fr.gildasledrogoff.fwknop.domain.model.map

private val PORTS_PATTERN = Regex("^(tcp|udp)/\\d.*")
internal fun ServerDraft.toEndpoint(): DomainResult<ServerEndpoint, ConfigError> {
    val address = host.trim()
    if (!address.contains('.') && !address.contains(':')) return Failure(ConfigError.ServerInvalid)
    return Success(ServerEndpoint(address, toServerPort(), protocol))
}

private fun ServerDraft.toServerPort(): ServerPort {
    if (randomPort) return ServerPort.Random
    val value = port.trim().takeIf { it.isPort() }?.toInt() ?: ServerPort.DEFAULT
    return ServerPort.Fixed(value)
}

internal fun AccessDraft.toAccessRule(): DomainResult<AccessRule, ConfigError> = when (type) {
    MessageType.SERVER_COMMAND -> toServerCommand()
    MessageType.OPEN_PORT -> toPortAccess().map { AccessRule.OpenPort(it) }
    MessageType.NAT_ACCESS -> toPortAccess().flatMap { toNatAccess(it) }
    MessageType.LOCAL_NAT_ACCESS -> toPortAccess().flatMap { access ->
        toNatPort().map { AccessRule.LocalNatAccess(access, it) }
    }
}

private fun AccessDraft.toServerCommand(): DomainResult<AccessRule, ConfigError> =
    if (command.isBlank()) Failure(ConfigError.CommandBlank) else Success(
        AccessRule.ServerCommand(
            command.trim()
        )
    )

private fun AccessDraft.toPortAccess(): DomainResult<PortAccess, ConfigError> {
    val trimmedPorts = ports.trim()
    if (!PORTS_PATTERN.matches(trimmedPorts)) return Failure(ConfigError.PortsFormat)
    val rawTimeout = timeout.trim()
    val seconds = if (rawTimeout.isEmpty()) {
        null
    } else {
        rawTimeout.toIntOrNull()?.takeIf { it > 0 } ?: return Failure(ConfigError.TimeoutInvalid)
    }
    return Success(PortAccess(trimmedPorts, seconds, keepOpen && seconds != null))
}

private fun AccessDraft.toNatAccess(access: PortAccess): DomainResult<AccessRule, ConfigError> {
    val address = natIp.trim()
    if (!address.isIpAddress()) return Failure(ConfigError.NatIpInvalid)
    return toNatPort().map { AccessRule.NatAccess(access, address, it) }
}

private fun AccessDraft.toNatPort(): DomainResult<Int, ConfigError> {
    val value = natPort.trim()
    return if (value.isPort()) Success(value.toInt()) else Failure(ConfigError.NatPortInvalid)
}

internal fun SourceIpDraft.toSourceIp(): DomainResult<SourceIp, ConfigError> = when (mode) {
    SourceIpMode.RESOLVE -> Success(SourceIp.Resolve)
    SourceIpMode.SOURCE -> Success(SourceIp.Source)
    SourceIpMode.PROMPT -> Success(SourceIp.Prompt)
    SourceIpMode.ALLOW -> address.trim().let {
        if (it.isIpAddress()) Success(SourceIp.Fixed(it)) else Failure(ConfigError.AllowIpInvalid)
    }
}
