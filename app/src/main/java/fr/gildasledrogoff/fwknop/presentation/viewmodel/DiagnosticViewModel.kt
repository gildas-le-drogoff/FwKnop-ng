package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.model.KnockObserver
import fr.gildasledrogoff.fwknop.domain.model.KnockOverrides
import fr.gildasledrogoff.fwknop.domain.model.KnockPrompt
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockStep
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import fr.gildasledrogoff.fwknop.domain.usecase.GetConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetRequiredPromptsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticDetail
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticEffect
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticEvent
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticPhase
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticState
import fr.gildasledrogoff.fwknop.presentation.state.DiagnosticStep
import fr.gildasledrogoff.fwknop.presentation.state.StepStatus
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.DiagnosticRoute
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs a real knock while reporting each step of [SendKnockUseCase]. Unlike [KnockViewModel] it never launches
 * the external app nor schedules timers: it only tells whether, and where, the knock breaks.
 */
@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConfig: GetConfigUseCase,
    private val requiredPrompts: GetRequiredPromptsUseCase,
    private val sendKnock: SendKnockUseCase,
    private val interactions: InteractionRegistry,
) : ViewModel() {
    private val nickname: String = savedStateHandle.get<String>(DiagnosticRoute.ARG_NICKNAME).orEmpty()
    private val _state = MutableStateFlow(DiagnosticState(nickname = nickname))
    val state: StateFlow<DiagnosticState> = _state.asStateFlow()
    private val _effects = Channel<DiagnosticEffect>(Channel.BUFFERED)
    val effects: Flow<DiagnosticEffect> = _effects.receiveAsFlow()
    private var config: SpaConfig? = null
    private var request: KnockRequest? = null
    private var job: Job? = null

    init {
        load()
    }

    fun intentSender(id: InteractionId): IntentSender? = interactions.intentSender(id)

    fun onEvent(event: DiagnosticEvent) {
        when (event) {
            is DiagnosticEvent.Run -> start(event)
            is DiagnosticEvent.InteractionFinished -> onInteractionFinished(event)
            DiagnosticEvent.Cancel -> cancel()
        }
    }

    private fun load() = viewModelScope.launch {
        when (val result = getConfig(Nickname(nickname))) {
            is DomainResult.Success -> {
                config = result.data
                val prompts = requiredPrompts(result.data)
                _state.update {
                    it.copy(
                        loading = false,
                        needsKey = KnockPrompt.KEY in prompts,
                        needsSourceIp = KnockPrompt.SOURCE_IP in prompts,
                    )
                }
            }

            is DomainResult.Failure -> _state.update { it.copy(loading = false, error = result.error.toUiError()) }
        }
    }

    private fun start(event: DiagnosticEvent.Run) {
        val config = config ?: return
        if (_state.value.running) return
        val current = _state.value
        val sourceIp = event.sourceIp.trim().takeIf { current.needsSourceIp }
        if (sourceIp != null && !sourceIp.isIpAddress()) {
            _state.update { it.copy(sourceIpInvalid = true) }
            return
        }
        val key = event.key.takeIf { current.needsKey }
        request = KnockRequest(config, KnockOverrides(key = key, sourceIp = sourceIp))
        _state.update { it.copy(sourceIpInvalid = false) }
        run()
    }

    private fun run() {
        val pending = request ?: return
        job?.cancel()
        _state.update { it.copy(running = true, finished = false, steps = freshSteps().withNextRunning()) }
        job = viewModelScope.launch {
            when (val result = sendKnock(pending, KnockObserver(::onStep))) {
                is DomainResult.Success -> finish(failed = null)
                is DomainResult.Failure -> onFailure(result.error)
            }
        }
    }

    private fun onStep(step: KnockStep) {
        val (phase, detail) = when (step) {
            is KnockStep.ServerResolved -> DiagnosticPhase.SERVER_DNS to DiagnosticDetail.Server(step.host, step.addresses)
            is KnockStep.SourceResolved -> DiagnosticPhase.SOURCE_IP to DiagnosticDetail.Source(step.source)
            is KnockStep.RouteSelected ->
                DiagnosticPhase.ADDRESS_FAMILY to DiagnosticDetail.Family(step.family, step.route.serverIp)
            is KnockStep.PacketBuilt -> DiagnosticPhase.PACKET to DiagnosticDetail.Packet(step.sizeBytes)
            is KnockStep.Sending -> {
                // Known before the send completes: stays running until the use case returns.
                updateStep(DiagnosticPhase.TRANSPORT) {
                    it.copy(status = StepStatus.Running, detail = DiagnosticDetail.Transport(step.target))
                }
                return
            }
        }
        updateStep(phase) { it.copy(status = StepStatus.Done, detail = detail) }
    }

    private suspend fun onFailure(error: KnockError) {
        val interaction = ((error as? KnockError.Gpg)?.error as? GpgError.InteractionRequired)?.interaction
        if (interaction != null) {
            request = request?.let { it.copy(overrides = it.overrides.copy(gpgInteraction = interaction)) }
            _effects.send(DiagnosticEffect.LaunchInteraction(interaction))
            return
        }
        finish(failed = error)
    }

    private fun finish(failed: KnockError?) {
        _state.update { state ->
            val steps = state.steps.map { if (it.status == StepStatus.Running && failed == null) it.done() else it }
            val failedPhase = failed?.let { it.phase() ?: steps.firstOrNull(DiagnosticStep::isOpen)?.phase }
            val error = failed?.toUiError()
            state.copy(
                running = false,
                finished = true,
                steps = steps.map { step ->
                    when {
                        error != null && step.phase == failedPhase -> step.copy(status = StepStatus.Failed(error))
                        step.isOpen() -> step.copy(status = StepStatus.Skipped)
                        else -> step
                    }
                },
            )
        }
        job = null
    }

    private fun onInteractionFinished(event: DiagnosticEvent.InteractionFinished) {
        val current = event.id == request?.overrides?.gpgInteraction
        interactions.complete(event.id, event.data.takeIf { current })
        if (!current) return
        if (event.completed) run() else cancel()
    }

    private fun cancel() {
        job?.cancel()
        job = null
        request = request?.let { it.copy(overrides = it.overrides.copy(gpgInteraction = null)) }
        _state.update { it.copy(running = false, finished = false, steps = freshSteps()) }
    }

    private fun updateStep(phase: DiagnosticPhase, transform: (DiagnosticStep) -> DiagnosticStep) {
        _state.update { state ->
            val steps = state.steps.map { if (it.phase == phase) transform(it) else it }
            state.copy(steps = if (state.running) steps.withNextRunning() else steps)
        }
    }
}

private fun freshSteps() = DiagnosticPhase.entries.map(::DiagnosticStep)

private fun DiagnosticStep.isOpen() = status == StepStatus.Pending || status == StepStatus.Running

private fun DiagnosticStep.done() = copy(status = StepStatus.Done)

/** Exactly one row spins while the knock runs: the first one still waiting for its answer. */
private fun List<DiagnosticStep>.withNextRunning(): List<DiagnosticStep> {
    val next = indexOfFirst(DiagnosticStep::isOpen)
    return mapIndexed { index, step ->
        when {
            index == next -> step.copy(status = StepStatus.Running)
            step.status == StepStatus.Running -> step.copy(status = StepStatus.Pending)
            else -> step
        }
    }
}

/** Where an error belongs; `null` lets the first unfinished row take it. */
private fun KnockError.phase(): DiagnosticPhase? = when (this) {
    is KnockError.ServerUnresolved, is KnockError.ServerFamilyMismatch -> DiagnosticPhase.SERVER_DNS
    KnockError.SourceIpMissing, KnockError.SourceIpInvalid, is KnockError.SourceIpUnresolved ->
        DiagnosticPhase.SOURCE_IP
    KnockError.KeyMissing, KnockError.PacketGeneration, KnockError.GpgKeysMissing, is KnockError.Gpg ->
        DiagnosticPhase.PACKET
    KnockError.LocalNetworkBlocked, KnockError.LocalNetworkPermissionRequired, is KnockError.Transport ->
        DiagnosticPhase.TRANSPORT
    else -> null
}
