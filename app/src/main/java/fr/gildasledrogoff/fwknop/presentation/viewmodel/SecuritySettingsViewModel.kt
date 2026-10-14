// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.usecase.CanEnableAppLockUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.RotateVaultKeysUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SetAppLockUseCase
import fr.gildasledrogoff.fwknop.presentation.state.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    observeVault: ObserveVaultStatusUseCase,
    private val canEnableAppLock: CanEnableAppLockUseCase,
    private val setAppLock: SetAppLockUseCase,
    private val rotateKeys: RotateVaultKeysUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SecuritySettingsState(canEnableLock = canEnableAppLock()))
    val state: StateFlow<SecuritySettingsState> = _state.asStateFlow()
    private val _effects = Channel<SecuritySettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SecuritySettingsEffect> = _effects.receiveAsFlow()
    private var pending: SecureAction? = null

    init {
        viewModelScope.launch {
            observeVault().collect { status -> _state.update { it.copy(lockEnabled = status.lockEnabled) } }
        }
    }

    fun onEvent(event: SecuritySettingsEvent) {
        when (event) {
            is SecuritySettingsEvent.LockToggled -> requestLock(event.enabled)
            SecuritySettingsEvent.RotateKeysRequested -> request(SecureAction.RotateKeys)
            is SecuritySettingsEvent.AuthCompleted -> onAuthCompleted(event.result)
            SecuritySettingsEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun requestLock(enabled: Boolean) {
        if (_state.value.busy) return
        if (enabled && !canEnableAppLock()) {
            _state.update {
                it.copy(
                    canEnableLock = false,
                    error = VaultError.DeviceNotSecure.toUiError()
                )
            }
            return
        }
        request(SecureAction.SetLock(enabled))
    }

    private fun request(action: SecureAction) {
        if (_state.value.busy) return
        pending = action
        viewModelScope.launch { _effects.send(SecuritySettingsEffect.Authenticate(action.purpose)) }
    }

    private fun onAuthCompleted(result: AuthResult) {
        val action = pending ?: return
        pending = null
        when {
            result == AuthResult.Success -> apply(action)
            result == AuthResult.Unavailable && action.allowedWithoutCredential -> apply(action)
            result is AuthResult.Error -> _state.update { it.copy(error = result.toUiError()) }
        }
    }

    private fun apply(action: SecureAction) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        val result = when (action) {
            is SecureAction.SetLock -> setAppLock(action.enabled)
            SecureAction.RotateKeys -> rotateKeys()
        }
        when (result) {
            is DomainResult.Success -> _effects.send(SecuritySettingsEffect.Message(action.doneMessage))
            is DomainResult.Failure -> _state.update { it.copy(error = result.error.toUiError()) }
        }
        _state.update { it.copy(busy = false) }
    }
}

private sealed interface SecureAction {
    val purpose: AuthPurpose
    val allowedWithoutCredential: Boolean
    val doneMessage: Int

    data class SetLock(val enabled: Boolean) : SecureAction {
        override val purpose = AuthPurpose.CHANGE_LOCK
        override val allowedWithoutCredential = !enabled
        override val doneMessage = if (enabled) R.string.lock_enabled else R.string.lock_disabled
    }

    data object RotateKeys : SecureAction {
        override val purpose = AuthPurpose.ROTATE_KEYS
        override val allowedWithoutCredential = true
        override val doneMessage = R.string.keys_rotated
    }
}
