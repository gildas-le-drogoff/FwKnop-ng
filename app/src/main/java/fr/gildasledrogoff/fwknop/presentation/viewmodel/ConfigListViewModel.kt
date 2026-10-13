package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.usecase.DeleteConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.DismissLockSuggestionUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.DuplicateConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveConfigsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.presentation.state.ConfigListEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigListState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.toSummary
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigListViewModel @Inject constructor(
    observeConfigs: ObserveConfigsUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeVault: ObserveVaultStatusUseCase,
    private val deleteConfig: DeleteConfigUseCase,
    private val duplicateConfig: DuplicateConfigUseCase,
    private val dismissLockSuggestion: DismissLockSuggestionUseCase,
) : ViewModel() {
    private val error = MutableStateFlow<UiError?>(null)
    val state: StateFlow<ConfigListState> =
        combine(
            observeConfigs().map { result ->
                reportFailure(result)
                (result as? DomainResult.Success)?.data.orEmpty()
            },
            observeSettings(),
            observeVault(),
            error,
        ) { configs, settings, vault, error ->
            ConfigListState(
                isLoading = false,
                configs = configs.map { it.toSummary() },
                showHelpOnStart = !settings.helpShown,
                nfcKnockEnabled = settings.nfcKnockEnabled,
                
                suggestLock = !vault.lockEnabled && !settings.lockSuggestionDismissed,
                error = error,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ConfigListState()
        )

    fun onEvent(event: ConfigListEvent) {
        when (event) {
            is ConfigListEvent.DeleteConfig -> viewModelScope.launch {
                reportFailure(deleteConfig(Nickname(event.nickname)))
            }
            is ConfigListEvent.DuplicateConfig -> viewModelScope.launch {
                reportFailure(duplicateConfig(Nickname(event.source), event.nickname))
            }

            ConfigListEvent.DismissLockSuggestion -> viewModelScope.launch { dismissLockSuggestion() }
            ConfigListEvent.DismissError -> error.value = null
        }
    }

    private fun reportFailure(result: DomainResult<*, ConfigError>) {
        if (result is DomainResult.Failure) error.value = result.error.toUiError()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
