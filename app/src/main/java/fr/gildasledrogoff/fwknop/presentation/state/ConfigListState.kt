package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.portAccess

@Immutable
data class ConfigSummary(
    val nickname: String,
    val endpoint: String,
    val protocol: Protocol,
    val messageType: MessageType,
    val accessDetail: String,
    val launchesApp: Boolean,
)

@Immutable
data class ConfigListState(
    val isLoading: Boolean = true,
    val configs: List<ConfigSummary> = emptyList(),
    val showHelpOnStart: Boolean = false,
    val nfcKnockEnabled: Boolean = false,
    val suggestLock: Boolean = false,
    val error: UiError? = null,
)

sealed interface ConfigListEvent {
    data class DeleteConfig(val nickname: String) : ConfigListEvent
    data class DuplicateConfig(val source: String, val nickname: String) : ConfigListEvent
    data object DismissLockSuggestion : ConfigListEvent
    data object DismissError : ConfigListEvent
}

fun SpaConfig.toSummary(): ConfigSummary = ConfigSummary(
    nickname = nickname.value,
    endpoint = "${server.host}:${server.port.label()}",
    protocol = server.protocol,
    messageType = access.messageType(),
    accessDetail = (access as? AccessRule.ServerCommand)?.command
        ?: access.portAccess?.ports.orEmpty(),
    launchesApp = externalApp != ExternalApp.None,
)

private fun ServerPort.label(): String = when (this) {
    is ServerPort.Fixed -> value.toString()
    ServerPort.Random -> "*"
}

private fun AccessRule.messageType(): MessageType = when (this) {
    is AccessRule.OpenPort -> MessageType.OPEN_PORT
    is AccessRule.NatAccess -> MessageType.NAT_ACCESS
    is AccessRule.LocalNatAccess -> MessageType.LOCAL_NAT_ACCESS
    is AccessRule.ServerCommand -> MessageType.SERVER_COMMAND
}
