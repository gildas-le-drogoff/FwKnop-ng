// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.ConfigEditRoute
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConfig: GetConfigUseCase,
    private val saveConfig: SaveConfigUseCase,
    private val parseQrConfig: ParseQrConfigUseCase,
    private val openSealedQr: OpenSealedQrUseCase,
    private val selectGpgKey: SelectGpgKeyUseCase,
    private val interactions: InteractionRegistry,
) : ViewModel() {
    private val original: Nickname? =
        savedStateHandle.get<String>(ConfigEditRoute.ARG_NICKNAME)?.let(::Nickname)
    private val newKind: ConfigKind = savedStateHandle.get<String>(ConfigEditRoute.ARG_KIND)
        ?.let { name -> ConfigKind.entries.firstOrNull { it.name == name } } ?: ConfigKind.FWKNOP
    private val _state = MutableStateFlow(
        ConfigEditState(
            isNew = original == null,
            isLoading = original != null,
            draft = ConfigDraft(kind = newKind),
        )
    )
    val state: StateFlow<ConfigEditState> = _state.asStateFlow()
    private val _effects = Channel<ConfigEditEffect>(Channel.BUFFERED)
    val effects: Flow<ConfigEditEffect> = _effects.receiveAsFlow()
    private val pendingKeyRoles = mutableMapOf<InteractionId, GpgKeyRole>()
    private var pendingSealedQr: String? = null

    init {
        original?.let(::load)
    }

    fun intentSender(id: InteractionId): IntentSender? = interactions.intentSender(id)
    fun onEvent(event: ConfigEditEvent) {
        when (event) {
            is ConfigEditEvent.NicknameChanged -> updateDraft { it.copy(nickname = event.value) }
            is ConfigEditEvent.ServerChanged -> updateDraft { it.copy(server = event.value) }
            is ConfigEditEvent.AccessChanged -> updateDraft { it.copy(access = event.value) }
            is ConfigEditEvent.SourceIpChanged -> updateDraft { it.copy(sourceIp = event.value) }
            is ConfigEditEvent.CryptoChanged -> updateDraft { it.copy(crypto = event.value) }
            is ConfigEditEvent.ExternalAppChanged -> updateDraft { it.copy(externalApp = event.value) }
            is ConfigEditEvent.WakeOnLanChanged -> updateDraft { it.copy(wakeOnLan = event.value) }
            is ConfigEditEvent.QrScanned -> onQrScanned(event.content)
            is ConfigEditEvent.QrImportSelected -> applyPendingQr(event.fullConfig)
            ConfigEditEvent.QrImportDismissed -> _state.update { it.copy(pendingQr = null) }
            is ConfigEditEvent.SealedQrPassphraseEntered -> openPendingSealedQr(event.passphrase)
            ConfigEditEvent.SealedQrDismissed -> {
                pendingSealedQr = null
                _state.update { it.copy(sealedQr = null) }
            }

            is ConfigEditEvent.SelectGpgKey -> selectKey(event.role, resume = null)
            is ConfigEditEvent.InteractionFinished -> onInteractionFinished(event)
            ConfigEditEvent.Save -> save()
            ConfigEditEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun load(nickname: Nickname) = viewModelScope.launch {
        when (val result = getConfig(nickname)) {
            is DomainResult.Success -> _state.update {
                it.copy(
                    isLoading = false,
                    draft = result.data.toDraft()
                )
            }

            is DomainResult.Failure -> _state.update {
                it.copy(
                    isLoading = false,
                    error = result.error.toUiError()
                )
            }
        }
    }

    private fun updateDraft(transform: (ConfigDraft) -> ConfigDraft) =
        _state.update { it.copy(draft = transform(it.draft)) }

    private fun onQrScanned(content: String) {
        if (SealedQr.isSealed(content)) {
            pendingSealedQr = content
            _state.update { it.copy(sealedQr = SealedQrPrompt()) }
        } else {
            applyScannedQr(content)
        }
    }

    private fun openPendingSealedQr(chars: CharArray) {
        val passphrase = BackupPassphrase(chars)
        val content = pendingSealedQr
        if (content == null || _state.value.isLoading) return passphrase.wipe()
        _state.update { it.copy(sealedQr = null, isLoading = true) }
        viewModelScope.launch {
            val result = openSealedQr(content, passphrase)
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is DomainResult.Success -> {
                    pendingSealedQr = null
                    applyScannedQr(result.data)
                }

                is DomainResult.Failure -> onSealedQrFailure(result.error)
            }
        }
    }

    private fun onSealedQrFailure(error: BackupError) {
        if (error == BackupError.WrongPassphrase) {
            _state.update { it.copy(sealedQr = SealedQrPrompt(wrongPassphrase = true)) }
        } else {
            pendingSealedQr = null
            _state.update { it.copy(error = error.toUiError()) }
        }
    }

    private fun applyScannedQr(content: String) {
        val parsed = parseQrConfig(content)
        if (parsed.config == null) {
            updateDraft { it.copy(crypto = it.crypto.withQrKeys(parsed.keys)) }
        } else {
            _state.update { it.copy(pendingQr = parsed) }
        }
    }

    private fun applyPendingQr(fullConfig: Boolean) {
        val parsed = _state.value.pendingQr ?: return
        _state.update {
            it.copy(
                pendingQr = null,
                draft = if (fullConfig) it.draft.withQrConfig(parsed) else {
                    it.draft.copy(crypto = it.draft.crypto.withQrKeys(parsed.keys))
                },
            )
        }
    }

    private fun selectKey(role: GpgKeyRole, resume: InteractionId?) = viewModelScope.launch {
        when (val result = selectGpgKey(role, resume)) {
            is DomainResult.Success -> applyGpgKey(role, result.data)
            is DomainResult.Failure -> onGpgFailure(role, result.error)
        }
    }

    private fun applyGpgKey(role: GpgKeyRole, key: GpgKeyId) = updateDraft { draft ->
        val crypto = when (role) {
            GpgKeyRole.RECIPIENT -> draft.crypto.copy(gpgRecipient = key)
            GpgKeyRole.SIGNER -> draft.crypto.copy(gpgSigner = key)
        }
        draft.copy(crypto = crypto)
    }

    private suspend fun onGpgFailure(role: GpgKeyRole, error: GpgError) {
        if (error is GpgError.InteractionRequired) {
            pendingKeyRoles[error.interaction] = role
            _effects.send(ConfigEditEffect.LaunchInteraction(error.interaction))
        } else {
            _state.update { it.copy(error = error.toUiError()) }
        }
    }

    private fun onInteractionFinished(event: ConfigEditEvent.InteractionFinished) {
        interactions.complete(event.id, event.data)
        val role = pendingKeyRoles.remove(event.id) ?: return
        if (event.completed) selectKey(role, resume = event.id)
    }

    private fun save() = viewModelScope.launch {
        when (val result = saveConfig(_state.value.draft, original)) {
            is DomainResult.Success -> _effects.send(ConfigEditEffect.Saved)
            is DomainResult.Failure -> _state.update { it.copy(error = result.error.toUiError()) }
        }
    }
}
