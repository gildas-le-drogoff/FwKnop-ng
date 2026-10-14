package fr.gildasledrogoff.fwknop.presentation.state

import android.content.Intent
import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.AddressFamily
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp

/** The rows of the diagnostic screen, in display order. */
enum class DiagnosticPhase { SERVER_DNS, SOURCE_IP, ADDRESS_FAMILY, PACKET, TRANSPORT }

@Immutable
sealed interface DiagnosticDetail {
    data class Server(val host: String, val addresses: List<String>) : DiagnosticDetail
    data class Source(val source: ResolvedSourceIp) : DiagnosticDetail
    data class Family(val family: AddressFamily, val serverIp: String) : DiagnosticDetail
    data class Packet(val sizeBytes: Int) : DiagnosticDetail
    data class Transport(val target: KnockTarget) : DiagnosticDetail
}

@Immutable
sealed interface StepStatus {
    data object Pending : StepStatus
    data object Running : StepStatus
    data object Done : StepStatus

    /** Not reached, or not needed by this configuration (e.g. no DNS when the source is invalid). */
    data object Skipped : StepStatus
    data class Failed(val error: UiError) : StepStatus
}

@Immutable
data class DiagnosticStep(
    val phase: DiagnosticPhase,
    val status: StepStatus = StepStatus.Pending,
    val detail: DiagnosticDetail? = null,
)

@Immutable
data class DiagnosticState(
    val nickname: String = "",
    val loading: Boolean = true,
    val needsKey: Boolean = false,
    val needsSourceIp: Boolean = false,
    val sourceIpInvalid: Boolean = false,
    val running: Boolean = false,
    val finished: Boolean = false,
    val steps: List<DiagnosticStep> = DiagnosticPhase.entries.map(::DiagnosticStep),
    /** Problem preventing the test from starting at all (unknown config…). */
    val error: UiError? = null,
) {
    val succeeded: Boolean get() = finished && steps.none { it.status is StepStatus.Failed }
}

sealed interface DiagnosticEvent {
    /** [key] / [sourceIp] are only read when the configuration asks for them. */
    data class Run(val key: String = "", val sourceIp: String = "") : DiagnosticEvent
    data class InteractionFinished(val id: InteractionId, val completed: Boolean, val data: Intent?) : DiagnosticEvent

    data object Cancel : DiagnosticEvent
}

sealed interface DiagnosticEffect {
    data class LaunchInteraction(val id: InteractionId) : DiagnosticEffect
}
