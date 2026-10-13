package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.nfc.Tag
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.usecase.CreateNfcTagDigestUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.OtherNfcTagDigestsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SaveSettingsUseCase
import fr.gildasledrogoff.fwknop.presentation.state.NfcWriteNotices
import fr.gildasledrogoff.fwknop.presentation.state.NfcWriteState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.NfcWriteRoute
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.NfcTagWriter
import fr.gildasledrogoff.fwknop.utils.helpers.NfcWriteResult
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NfcWriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val createDigest: CreateNfcTagDigestUseCase,
    private val otherDigests: OtherNfcTagDigestsUseCase,
    private val writer: NfcTagWriter,
    private val dispatchers: DispatcherProvider,
    private val observeSettings: ObserveSettingsUseCase,
    private val saveSettings: SaveSettingsUseCase,
    private val permissions: RuntimePermissions,
    observeVault: ObserveVaultStatusUseCase,
) : ViewModel() {
    val nickname: String = savedStateHandle.get<String>(NfcWriteRoute.ARG_NICKNAME).orEmpty()
    private val _state = MutableStateFlow<NfcWriteState>(NfcWriteState.Waiting)
    val state: StateFlow<NfcWriteState> = _state.asStateFlow()

    
    val keepOtherConfigs: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_KEEP_OTHERS, true)

    
    
    val notices: StateFlow<NfcWriteNotices> =
        combine(observeSettings(), permissions.snapshot, observeVault()) { settings, snapshot, vault ->
            NfcWriteNotices(
                nfcKnockDisabled = !settings.nfcKnockEnabled,
                localNetworkMissing = snapshot.localNetwork != PermissionStatus.GRANTED,
                localNetworkPermanentlyDenied = snapshot.localNetwork == PermissionStatus.DENIED_PERMANENTLY,
                lockDisabled = !vault.lockEnabled,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NfcWriteNotices())

    fun onNfcUnavailable(present: Boolean) {
        _state.value = if (present) NfcWriteState.Disabled else NfcWriteState.Unsupported
    }

    fun onNfcAvailable() {
        if (_state.value == NfcWriteState.Disabled) _state.value = NfcWriteState.Waiting
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        permissions.onPromptResult(LocalNetworkAccess.PERMISSION, granted, permanentlyDenied)
    }

    fun enableNfcKnock() {
        viewModelScope.launch {
            saveSettings(observeSettings().first().copy(nfcKnockEnabled = true))
        }
    }

    fun setKeepOtherConfigs(keep: Boolean) {
        savedStateHandle[KEY_KEEP_OTHERS] = keep
    }

    fun onTagDiscovered(tag: Tag) {
        
        if (_state.value !is NfcWriteState.Waiting && _state.value !is NfcWriteState.Failed) return
        _state.value = NfcWriteState.Writing
        val keepOthers = keepOtherConfigs.value
        viewModelScope.launch {
            
            val digest = try {
                createDigest(Nickname(nickname))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                DebugLog.failure("nfc tag key", e)
                _state.value = NfcWriteState.Failed(UiError.Message(R.string.nfc_write_key_failed))
                return@launch
            }
            
            val result = withContext(dispatchers.io) {
                writer.write(tag) { existing ->
                    if (keepOthers) otherDigests(existing, Nickname(nickname)) + digest else listOf(digest)
                }
            }
            _state.value = when (result) {
                NfcWriteResult.WRITTEN -> NfcWriteState.Written
                else -> NfcWriteState.Failed(result.toUiError())
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_KEEP_OTHERS = "keep_other_configs"
    }
}
