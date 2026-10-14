// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Ssids
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockMonitor
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.presentation.state.AutoKnockEvent
import fr.gildasledrogoff.fwknop.presentation.state.AutoKnockState
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoKnockViewModel @Inject constructor(
    observeSettings: ObserveAutoKnockSettingsUseCase,
    observeConfigs: ObserveConfigsUseCase,
    private val saveSettings: SaveAutoKnockSettingsUseCase,
    private val autoKnockable: IsAutoKnockableUseCase,
    private val currentSsid: GetCurrentSsidUseCase,
    private val location: LocationAccess,
    private val monitor: AutoKnockMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(AutoKnockState(location = location.status()))
    val state: StateFlow<AutoKnockState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        settings = settings
                    )
                }
            }
        }
        viewModelScope.launch {
            observeConfigs().collect { result ->
                if (result is DomainResult.Success) {
                    val candidates =
                        result.data.filter(autoKnockable::invoke).map { it.nickname.value }
                    _state.update { it.copy(candidates = candidates) }
                }
            }
        }
    }

    fun onEvent(event: AutoKnockEvent) {
        when (event) {

            is AutoKnockEvent.EnableRequested ->
                if (event.enabled) _state.update { it.copy(confirmingEnable = true) } else save {
                    it.copy(
                        enabled = false
                    )
                }

            AutoKnockEvent.EnableConfirmed -> {
                if (!_state.value.confirmingEnable) return
                _state.update { it.copy(confirmingEnable = false) }
                save { it.copy(enabled = true) }
            }

            AutoKnockEvent.EnableDismissed -> _state.update { it.copy(confirmingEnable = false) }
            is AutoKnockEvent.ConfigChosen -> save { it.copy(nickname = event.nickname) }
            is AutoKnockEvent.SsidAdded -> addSsid(event.ssid)
            is AutoKnockEvent.SsidRemoved -> save { it.copy(trustedSsids = it.trustedSsids - event.ssid) }
            AutoKnockEvent.AddCurrentNetwork -> viewModelScope.launch {
                val ssid = currentSsid()
                _state.update { it.copy(currentSsidUnavailable = ssid == null) }
                ssid?.let(::addSsid)
            }

            AutoKnockEvent.PermissionsChanged -> {
                _state.update { it.copy(location = location.status()) }
                monitor.refresh()
            }
        }
    }

    private fun addSsid(raw: String) {
        val ssid = Ssids.normalize(raw) ?: return
        save { it.copy(trustedSsids = it.trustedSsids + ssid) }
    }


    private fun save(transform: (AutoKnockSettings) -> AutoKnockSettings) {
        val next = transform(_state.value.settings)
        _state.update { it.copy(settings = next) }
        viewModelScope.launch { saveSettings(next) }
    }
}
