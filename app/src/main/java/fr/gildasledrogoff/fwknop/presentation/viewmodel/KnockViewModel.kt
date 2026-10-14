// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnockViewModel @Inject constructor(
    private val getConfig: GetConfigUseCase,
    private val getNfcConfig: GetNfcKnockConfigUseCase,
    private val requiredPrompts: GetRequiredPromptsUseCase,
    private val parseQrClientIp: ParseQrClientIpUseCase,
    private val sendKnock: SendKnockUseCase,
    private val scheduleTimer: ScheduleKnockTimerUseCase,
    private val needsTimerNotification: NeedsTimerNotificationUseCase,
    private val interactions: InteractionRegistry,
    private val runtimePermissions: RuntimePermissions,
    private val observeVault: ObserveVaultStatusUseCase,
    private val externalAppDelay: GetExternalAppDelayUseCase,
    private val verifyPorts: VerifyPortAccessUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<KnockState>(KnockState.Idle)
    val state: StateFlow<KnockState> = _state.asStateFlow()
    private val _effects = Channel<KnockEffect>(Channel.BUFFERED)
    val effects: Flow<KnockEffect> = _effects.receiveAsFlow()
    private var request: KnockRequest? = null
    private val pendingPrompts = ArrayDeque<KnockPrompt>()
    private var choices: List<SpaConfig> = emptyList()
    private var nfcDigests: List<NfcTagDigest>? = null
    private var sendJob: Job? = null
    fun intentSender(id: InteractionId): IntentSender? = interactions.intentSender(id)
    fun onEvent(event: KnockEvent) {
        when (event) {
            is KnockEvent.Knock -> load {
                getConfig(Nickname(event.nickname)).mapError(::toKnockError).map(::listOf)
            }

            is KnockEvent.KnockFromNfc -> knockFromNfc(event.digests)
            is KnockEvent.NfcAuthCompleted -> onNfcAuthCompleted(event.result)
            is KnockEvent.ConfigChosen -> onConfigChosen(event.nickname)
            is KnockEvent.KeySubmitted -> override { it.copy(key = event.key) }
            is KnockEvent.SourceIpSubmitted -> submitSourceIp(event.ip)
            is KnockEvent.SourceIpScanned -> onSourceIpScanned(event.qrContent)
            KnockEvent.PermissionPromptLaunched -> onPermissionPromptLaunched()
            is KnockEvent.PermissionResult -> onPermissionResult(event)
            KnockEvent.PermissionDeclined -> onPermissionDeclined()
            is KnockEvent.InteractionFinished -> onInteractionFinished(event)
            KnockEvent.Retry -> retry()
            KnockEvent.Cancel -> abort()
        }
    }

    private fun knockFromNfc(digests: List<NfcTagDigest>) {
        if (_state.value != KnockState.Idle) return
        if (!observeVault().value.lockEnabled) {
            _state.value = KnockState.Failed(KnockError.NfcLockRequired.toUiError())
            return
        }
        nfcDigests = digests
        _state.value = KnockState.Authenticating
        viewModelScope.launch { _effects.send(KnockEffect.Authenticate) }
    }

    private fun onNfcAuthCompleted(result: AuthResult) {
        val digests = nfcDigests ?: return
        nfcDigests = null
        when (result) {
            AuthResult.Success -> load(alwaysChoose = true) { getNfcConfig(digests) }
            AuthResult.Cancelled -> _state.value = KnockState.Idle
            AuthResult.Unavailable -> _state.value =
                KnockState.Failed(KnockError.NfcLockRequired.toUiError())

            is AuthResult.Error -> _state.value = KnockState.Failed(result.toUiError())
        }
    }

    private fun load(
        alwaysChoose: Boolean = false,
        fetch: suspend () -> DomainResult<List<SpaConfig>, KnockError>,
    ) {
        viewModelScope.launch {
            when (val result = fetch()) {
                is DomainResult.Success -> result.data.singleOrNull()?.takeUnless { alwaysChoose }
                    ?.let(::start)
                    ?: offerChoice(result.data)

                is DomainResult.Failure -> _state.value = KnockState.Failed(
                    result.error.toUiError(),
                    tagErasable = result.error == KnockError.NfcTagUnknown,
                )
            }
        }
    }

    private fun offerChoice(configs: List<SpaConfig>) {
        choices = configs
        _state.value = KnockState.ChooseConfig(configs.map { it.nickname.value })
    }

    private fun onConfigChosen(nickname: String) {
        if (_state.value !is KnockState.ChooseConfig) return
        val config = choices.firstOrNull { it.nickname.value == nickname } ?: return
        choices = emptyList()
        start(config)
    }

    private fun start(config: SpaConfig) {
        request = KnockRequest(config)
        pendingPrompts.clear()
        pendingPrompts.addAll(requiredPrompts(config))
        advance()
    }

    private fun advance() {
        val nickname = request?.config?.nickname?.value ?: return
        when (pendingPrompts.removeFirstOrNull()) {
            KnockPrompt.KEY -> _state.value = KnockState.PromptKey(nickname)
            KnockPrompt.SOURCE_IP -> _state.value = KnockState.PromptSourceIp(nickname)
            null -> viewModelScope.launch { offerTimerNotifications(nickname) }
        }
    }

    private suspend fun offerTimerNotifications(nickname: String) {
        val config = request?.config ?: return
        val ask = runtimePermissions.snapshot.value.canPrompt(RuntimePermissions.NOTIFICATIONS) &&
                needsTimerNotification(config)
        if (ask) {
            _state.value = KnockState.ExplainPermission(nickname, KnockPermission.NOTIFICATIONS)
        } else {
            startSending(nickname)
        }
    }

    private fun onPermissionPromptLaunched() {
        val current = _state.value as? KnockState.ExplainPermission ?: return
        runtimePermissions.onPromptShown(current.permission.manifestName)
        _state.value = KnockState.AwaitingPermission(current.permission)
    }

    private fun onPermissionResult(event: KnockEvent.PermissionResult) {
        val current = _state.value as? KnockState.AwaitingPermission ?: return
        runtimePermissions.onPromptResult(
            current.permission.manifestName,
            event.granted,
            event.permanentlyDenied
        )
        val nickname = request?.config?.nickname?.value ?: return
        val blocked = current.permission == KnockPermission.LOCAL_NETWORK && !event.granted
        if (blocked) failLocalNetwork() else startSending(nickname)
    }

    private fun onPermissionDeclined() {
        val current = _state.value as? KnockState.ExplainPermission ?: return
        when (current.permission) {
            KnockPermission.NOTIFICATIONS -> {
                runtimePermissions.onPromptShown(current.permission.manifestName)
                startSending(current.nickname)
            }

            KnockPermission.LOCAL_NETWORK -> abort()
        }
    }

    private fun onLocalNetworkRequired() {
        val nickname = request?.config?.nickname?.value ?: return
        val status = runtimePermissions.snapshot.value.status(LocalNetworkAccess.PERMISSION)
        if (status == PermissionStatus.DENIED_PERMANENTLY) {
            failLocalNetwork()
        } else {
            _state.value = KnockState.ExplainPermission(nickname, KnockPermission.LOCAL_NETWORK)
        }
    }

    private fun failLocalNetwork() {
        _state.value =
            KnockState.Failed(KnockError.LocalNetworkBlocked.toUiError(), fixInAppSettings = true)
    }

    private fun startSending(nickname: String) {
        _state.value = KnockState.Sending(nickname)
        send()
    }

    private fun retry() {
        val failed = _state.value as? KnockState.Failed ?: return
        val nickname = request?.config?.nickname?.value
        if (failed.retryable && nickname != null) startSending(nickname)
    }

    private fun override(transform: (KnockOverrides) -> KnockOverrides) {
        request = request?.let { it.copy(overrides = transform(it.overrides)) }
        advance()
    }

    private fun onSourceIpScanned(content: String) {
        val current = _state.value as? KnockState.PromptSourceIp ?: return
        parseQrClientIp(content)?.let(::submitSourceIp)
            ?: run { _state.value = current.copy(invalid = false, qrUnrecognized = true) }
    }

    private fun submitSourceIp(ip: String) {
        val current = _state.value as? KnockState.PromptSourceIp ?: return
        if (ip.trim().isIpv4Address()) {
            override { it.copy(sourceIp = ip.trim()) }
        } else {
            _state.value = current.copy(invalid = true, qrUnrecognized = false)
        }
    }

    private fun send() {
        val pending = request ?: return
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            when (val result = sendKnock(pending)) {
                is DomainResult.Success -> onSent(result.data)
                is DomainResult.Failure -> onFailure(result.error)
            }
        }
    }

    private suspend fun onSent(outcome: KnockOutcome) {
        val config = outcome.request.config
        val notice = scheduleTimer(outcome).toNotice()
        reset()
        _effects.send(KnockEffect.Sent(config.nickname.value, outcome.source, notice))
        viewModelScope.launch { checkPorts(outcome) }
        if (config.externalApp != ExternalApp.None) {

            delay(externalAppDelay())
            _effects.send(KnockEffect.LaunchApp(config.externalApp, config.nickname.value))
        }
    }


    private suspend fun checkPorts(outcome: KnockOutcome) {
        val check = verifyPorts(outcome) ?: return
        _effects.send(KnockEffect.PortChecked(outcome.request.config.nickname.value, check))
    }

    private suspend fun onFailure(error: KnockError) {
        val interaction =
            ((error as? KnockError.Gpg)?.error as? GpgError.InteractionRequired)?.interaction
        request =
            request?.let { it.copy(overrides = it.overrides.copy(gpgInteraction = interaction)) }
        when {
            interaction != null -> _effects.send(KnockEffect.LaunchInteraction(interaction))
            error == KnockError.LocalNetworkPermissionRequired -> onLocalNetworkRequired()
            else -> _state.value = KnockState.Failed(
                error.toUiError(),
                fixInAppSettings = error == KnockError.LocalNetworkBlocked,
                retryable = true,
            )
        }
    }

    private fun onInteractionFinished(event: KnockEvent.InteractionFinished) {
        val current = event.id == request?.overrides?.gpgInteraction
        interactions.complete(event.id, event.data.takeIf { current })
        if (!current) return
        if (event.completed) send() else abort()
    }

    private fun abort() {
        sendJob?.cancel()
        reset()
        viewModelScope.launch { _effects.send(KnockEffect.Aborted) }
    }

    private fun reset() {
        sendJob = null
        request = null
        choices = emptyList()
        nfcDigests = null
        pendingPrompts.clear()
        _state.value = KnockState.Idle
    }

    private fun toKnockError(error: ConfigError): KnockError =
        if (error == ConfigError.NotFound) KnockError.ConfigMissing else KnockError.Config(error)
}

private val KnockPermission.manifestName: String
    get() = when (this) {
        KnockPermission.NOTIFICATIONS -> RuntimePermissions.NOTIFICATIONS
        KnockPermission.LOCAL_NETWORK -> LocalNetworkAccess.PERMISSION
    }

private fun KnockTimerOutcome.toNotice(): SentNotice? = when (this) {
    KnockTimerOutcome.NONE, KnockTimerOutcome.SCHEDULED -> null
    KnockTimerOutcome.NOTIFICATIONS_BLOCKED -> SentNotice.KEEP_OPEN_NEEDS_NOTIFICATIONS
    KnockTimerOutcome.KEEP_OPEN_UNAVAILABLE -> SentNotice.KEEP_OPEN_UNAVAILABLE
}
