package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.model.VaultStatus
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResetVaultUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.UnlockVaultUseCase
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.LockGateEffect
import fr.gildasledrogoff.fwknop.presentation.state.LockGateEvent
import fr.gildasledrogoff.fwknop.presentation.state.LockGateState
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockGateViewModel @Inject constructor(
    private val observeVault: ObserveVaultStatusUseCase,
    private val unlockVault: UnlockVaultUseCase,
    private val resetVault: ResetVaultUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<LockGateState>(LockGateState.Opening)
    val state: StateFlow<LockGateState> = _state.asStateFlow()
    private val _effects = Channel<LockGateEffect>(Channel.BUFFERED)
    val effects: Flow<LockGateEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { observeVault().collect(::onStatus) }
    }

    fun onEvent(event: LockGateEvent) {
        when (event) {
            is LockGateEvent.AuthCompleted -> onAuthCompleted(event.result)
            LockGateEvent.UnlockRequested -> requestAuth()
            LockGateEvent.ResetConfirmed -> reset()
        }
    }

    private fun onStatus(status: VaultStatus) {
        when {
            status.state == VaultState.UNLOCKED -> _state.value = LockGateState.Unlocked
            _state.value == LockGateState.Unlocked -> requestAuth()
            _state.value != LockGateState.Opening -> Unit
            status.lockEnabled -> requestAuth()
            else -> unlock(afterAuth = false)
        }
    }

    private fun onAuthCompleted(result: AuthResult) {
        when (result) {
            AuthResult.Success, AuthResult.Unavailable -> unlock(afterAuth = true)
            AuthResult.Cancelled -> _state.value = LockGateState.Locked()
            is AuthResult.Error -> _state.value = LockGateState.Locked(result.toUiError())
        }
    }

    private fun requestAuth() {
        _state.value = LockGateState.Locked()
        viewModelScope.launch { _effects.send(LockGateEffect.Authenticate) }
    }

    private fun unlock(afterAuth: Boolean) {
        _state.value = LockGateState.Opening
        viewModelScope.launch {
            val result = unlockVault() as? DomainResult.Failure ?: return@launch
            when {
                result.error == VaultError.AuthenticationRequired && !afterAuth -> requestAuth()
                result.error == VaultError.KeyInvalidated -> _state.value =
                    LockGateState.Invalidated

                else -> _state.value = LockGateState.Locked(result.error.toUiError())
            }
        }
    }

    private fun reset() {
        if (_state.value != LockGateState.Invalidated) return
        _state.value = LockGateState.Opening
        viewModelScope.launch {
            val result = resetVault() as? DomainResult.Failure
            when {
                result != null -> _state.value = LockGateState.Locked(result.error.toUiError())
                
                observeVault().value.lockEnabled -> requestAuth()
                else -> unlock(afterAuth = false)
            }
        }
    }
}
