// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.usecase.GetFwknopServerBlocksUseCase
import fr.gildasledrogoff.fwknop.presentation.state.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FwknopExportViewModel @Inject constructor(
    private val getBlocks: GetFwknopServerBlocksUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<FwknopExportState>(FwknopExportState.Idle)
    val state: StateFlow<FwknopExportState> = _state.asStateFlow()
    private val _effects = Channel<FwknopExportEffect>(Channel.BUFFERED)
    val effects: Flow<FwknopExportEffect> = _effects.receiveAsFlow()

    fun onEvent(event: FwknopExportEvent) {
        when (event) {
            is FwknopExportEvent.Requested -> if (_state.value == FwknopExportState.Idle) {
                _state.value = FwknopExportState.Authenticating(event.nickname)
                viewModelScope.launch { _effects.send(FwknopExportEffect.Authenticate) }
            }

            is FwknopExportEvent.AuthCompleted -> onAuthCompleted(event.result)
            FwknopExportEvent.Dismissed -> _state.value = FwknopExportState.Idle
        }
    }

    private fun onAuthCompleted(result: AuthResult) {
        val pending = _state.value as? FwknopExportState.Authenticating ?: return
        when {
            result.allowsSensitiveAction -> viewModelScope.launch {
                _state.value = when (val blocks = getBlocks(Nickname(pending.nickname))) {
                    is DomainResult.Success -> FwknopExportState.Showing(blocks.data)
                    is DomainResult.Failure -> FwknopExportState.Failed(blocks.error.toUiError())
                }
            }

            result is AuthResult.Error -> _state.value = FwknopExportState.Failed(result.toUiError())
            else -> _state.value = FwknopExportState.Idle
        }
    }
}
