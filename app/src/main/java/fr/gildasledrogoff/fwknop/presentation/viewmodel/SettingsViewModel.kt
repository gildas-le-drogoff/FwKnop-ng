// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SaveSettingsUseCase
import fr.gildasledrogoff.fwknop.presentation.state.SettingsEffect
import fr.gildasledrogoff.fwknop.presentation.state.SettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.SettingsState
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val saveSettings: SaveSettingsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val current = observeSettings().first()
        _state.update { it.copy(isLoading = false, form = current) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Changed -> _state.update { it.copy(form = event.value) }
            SettingsEvent.ResetDefaults -> _state.update {
                it.copy(
                    form = AppSettings(
                        helpShown = it.form.helpShown,
                        lockSuggestionDismissed = it.form.lockSuggestionDismissed,

                        knockHistoryRetention = it.form.knockHistoryRetention,
                    )
                )
            }

            SettingsEvent.Save -> save()
            SettingsEvent.DismissError -> _state.update { it.copy(error = null) }
            SettingsEvent.Reload -> load()
        }
    }

    private fun save() = viewModelScope.launch {
        when (val result = saveSettings(_state.value.form)) {
            is DomainResult.Success -> _effects.send(SettingsEffect.Saved)
            is DomainResult.Failure -> _state.update { it.copy(error = result.error.toUiError()) }
        }
    }
}
