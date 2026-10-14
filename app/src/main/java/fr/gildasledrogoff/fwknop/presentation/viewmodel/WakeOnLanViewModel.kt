package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.usecase.SendWakeOnLanUseCase
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanEffect
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanEvent
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanState
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WakeOnLanViewModel @Inject constructor(
    private val sendWakeOnLan: SendWakeOnLanUseCase,
    private val runtimePermissions: RuntimePermissions,
) : ViewModel() {
    private val _state = MutableStateFlow<WakeOnLanState>(WakeOnLanState.Idle)
    val state: StateFlow<WakeOnLanState> = _state.asStateFlow()
    private val _effects = Channel<WakeOnLanEffect>(Channel.BUFFERED)
    val effects: Flow<WakeOnLanEffect> = _effects.receiveAsFlow()

    fun onEvent(event: WakeOnLanEvent) {
        when (event) {
            is WakeOnLanEvent.Wake -> if (_state.value == WakeOnLanState.Idle) send(event.nickname)
            WakeOnLanEvent.PermissionPromptLaunched -> onPermissionPromptLaunched()
            is WakeOnLanEvent.PermissionResult -> onPermissionResult(event)
            WakeOnLanEvent.Retry -> (_state.value as? WakeOnLanState.Failed)?.let { send(it.nickname) }
            WakeOnLanEvent.Dismiss -> _state.value = WakeOnLanState.Idle
        }
    }

    private fun send(nickname: String) {
        _state.value = WakeOnLanState.Sending(nickname)
        viewModelScope.launch {
            when (val result = sendWakeOnLan(Nickname(nickname))) {
                is DomainResult.Success -> {
                    _state.value = WakeOnLanState.Idle
                    _effects.send(WakeOnLanEffect.Sent(nickname))
                }
                is DomainResult.Failure -> onFailure(nickname, result.error)
            }
        }
    }

    private fun onFailure(nickname: String, error: KnockError) {
        val permanentlyDenied =
            runtimePermissions.snapshot.value.status(LocalNetworkAccess.PERMISSION) == PermissionStatus.DENIED_PERMANENTLY
        _state.value = when {
            error == KnockError.LocalNetworkPermissionRequired && !permanentlyDenied ->
                WakeOnLanState.ExplainPermission(nickname)
            error == KnockError.LocalNetworkPermissionRequired || error == KnockError.LocalNetworkBlocked ->
                failLocalNetwork(nickname)
            else -> WakeOnLanState.Failed(nickname, error.toUiError())
        }
    }

    private fun onPermissionPromptLaunched() {
        val current = _state.value as? WakeOnLanState.ExplainPermission ?: return
        runtimePermissions.onPromptShown(LocalNetworkAccess.PERMISSION)
        _state.value = WakeOnLanState.AwaitingPermission(current.nickname)
    }

    private fun onPermissionResult(event: WakeOnLanEvent.PermissionResult) {
        val current = _state.value as? WakeOnLanState.AwaitingPermission ?: return
        runtimePermissions.onPromptResult(LocalNetworkAccess.PERMISSION, event.granted, event.permanentlyDenied)
        if (event.granted) send(current.nickname) else _state.value = failLocalNetwork(current.nickname)
    }

    private fun failLocalNetwork(nickname: String) =
        WakeOnLanState.Failed(nickname, KnockError.LocalNetworkBlocked.toUiError(), fixInAppSettings = true)
}
