package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.model.KnockHistoryRetention
import fr.gildasledrogoff.fwknop.domain.usecase.ClearKnockHistoryUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveKnockHistoryUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SetKnockHistoryRetentionUseCase
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryEvent
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryState
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryWipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnockHistoryViewModel @Inject constructor(
    observeHistory: ObserveKnockHistoryUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val clearHistory: ClearKnockHistoryUseCase,
    private val setRetention: SetKnockHistoryRetentionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(KnockHistoryState())
    val state: StateFlow<KnockHistoryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeHistory(), observeSettings()) { entries, settings ->
                entries to settings.knockHistoryRetention
            }.collect { (entries, retention) ->
                _state.update { it.copy(isLoading = false, entries = entries, retention = retention) }
            }
        }
    }

    fun onEvent(event: KnockHistoryEvent) {
        when (event) {
            is KnockHistoryEvent.RetentionSelected -> selectRetention(event.retention)
            KnockHistoryEvent.ClearRequested -> _state.update { it.copy(pendingWipe = KnockHistoryWipe.Clear) }
            KnockHistoryEvent.WipeConfirmed -> confirmWipe()
            KnockHistoryEvent.WipeDismissed -> _state.update { it.copy(pendingWipe = null) }
        }
    }

    // Turning the journal off erases it: confirm first unless there is nothing to lose.
    private fun selectRetention(retention: KnockHistoryRetention) {
        if (retention == KnockHistoryRetention.OFF && _state.value.entries.isNotEmpty()) {
            _state.update { it.copy(pendingWipe = KnockHistoryWipe.Disable) }
        } else {
            viewModelScope.launch { setRetention(retention) }
        }
    }

    private fun confirmWipe() {
        val wipe = _state.value.pendingWipe ?: return
        _state.update { it.copy(pendingWipe = null) }
        viewModelScope.launch {
            when (wipe) {
                KnockHistoryWipe.Clear -> clearHistory()
                KnockHistoryWipe.Disable -> setRetention(KnockHistoryRetention.OFF)
            }
        }
    }
}
