package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SealedBackup
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.uniqueNickname
import fr.gildasledrogoff.fwknop.domain.usecase.AddConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ExportBackupUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ExportConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveConfigsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.OpenBackupFileUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.RestoreBackupUseCase
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.BackupDialog
import fr.gildasledrogoff.fwknop.presentation.state.BackupEffect
import fr.gildasledrogoff.fwknop.presentation.state.BackupEvent
import fr.gildasledrogoff.fwknop.presentation.state.BackupMode
import fr.gildasledrogoff.fwknop.presentation.state.BackupState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.allowsSensitiveAction
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DocumentFiles
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val exportBackup: ExportBackupUseCase,
    private val exportConfig: ExportConfigUseCase,
    private val openBackupFile: OpenBackupFileUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val addConfig: AddConfigUseCase,
    private val observeConfigs: ObserveConfigsUseCase,
    private val documents: DocumentFiles,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private val _effects = Channel<BackupEffect>(Channel.BUFFERED)
    val effects: Flow<BackupEffect> = _effects.receiveAsFlow()
    private var pendingMode: BackupMode?
        get() = savedStateHandle.get<String>(KEY_MODE)?.let(BackupMode::valueOf)
        set(value) {
            savedStateHandle[KEY_MODE] = value?.name
        }
    private var pendingUri: Uri?
        get() = savedStateHandle[KEY_URI]
        set(value) {
            savedStateHandle[KEY_URI] = value
        }

    
    private var pendingNickname: String?
        get() = savedStateHandle[KEY_NICKNAME]
        set(value) {
            savedStateHandle[KEY_NICKNAME] = value
        }

    
    private var pendingRestore: Backup? = null
    private val pendingImports = ArrayDeque<SpaConfig>()
    private var importedCount = 0

    init {
        if (pendingUri != null) proceed()
    }

    fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.ExportAllRequested -> start(BackupMode.EXPORT_ALL)
            is BackupEvent.ExportConfigRequested -> if (idle()) {
                pendingNickname = event.nickname
                start(BackupMode.EXPORT_CONFIG)
            }

            BackupEvent.ImportRequested -> start(BackupMode.IMPORT)
            BackupEvent.RestoreConfirmed -> onRestoreConfirmed()
            is BackupEvent.ImportRenamed -> onImportRenamed(event.nickname)
            is BackupEvent.DocumentPicked -> onDocumentPicked(event.uri)
            is BackupEvent.AuthCompleted -> onAuthCompleted(event.result)
            is BackupEvent.PassphraseEntered -> onPassphrase(event.passphrase)
            BackupEvent.DialogDismissed -> onDialogDismissed()
            BackupEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun idle(): Boolean =
        !_state.value.busy && pendingMode == null && _state.value.dialog == BackupDialog.None

    private fun start(mode: BackupMode) {
        if (!idle()) return
        pendingMode = mode
        val date = LocalDate.now(clock)
        val effect = when (mode) {
            BackupMode.EXPORT_ALL -> BackupEffect.PickExportTarget("fwknop-$date.$EXTENSION")
            BackupMode.EXPORT_CONFIG -> BackupEffect.PickExportTarget(
                "fwknop-${pendingNickname.orEmpty().toFileNamePart()}-$date.$EXTENSION"
            )

            BackupMode.IMPORT -> BackupEffect.PickImportSource
        }
        send(effect)
    }

    private fun onDocumentPicked(uri: Uri?) {
        if (pendingMode == null) return
        if (uri == null) return abandon()
        pendingUri = uri
        proceed()
    }

    private fun proceed() {
        when (pendingMode) {
            BackupMode.EXPORT_ALL -> send(BackupEffect.Authenticate(AuthPurpose.EXPORT_BACKUP))
            BackupMode.EXPORT_CONFIG -> send(BackupEffect.Authenticate(AuthPurpose.EXPORT_CONFIG))
            BackupMode.IMPORT -> _state.update { it.copy(dialog = BackupDialog.Passphrase(BackupMode.IMPORT)) }
            null -> finish()
        }
    }

    private fun onAuthCompleted(result: AuthResult) {
        val mode = pendingMode
        if (mode == null || !mode.isExport || pendingUri == null) return
        when {
            result.allowsSensitiveAction ->
                _state.update { it.copy(dialog = BackupDialog.Passphrase(mode)) }

            result is AuthResult.Error -> abandon(result.toUiError())
            else -> abandon()
        }
    }

    private fun onPassphrase(chars: CharArray) {
        val passphrase = BackupPassphrase(chars)
        val uri = pendingUri
        val mode = pendingMode
        if (uri == null || mode == null || _state.value.busy) return passphrase.wipe()
        _state.update { it.copy(dialog = BackupDialog.None, busy = true) }
        viewModelScope.launch {
            when (mode) {
                BackupMode.EXPORT_ALL -> export(uri, R.plurals.backup_exported) { exportBackup(passphrase) }
                BackupMode.EXPORT_CONFIG -> export(uri, R.plurals.configs_exported) {
                    exportConfig(Nickname(pendingNickname.orEmpty()), passphrase)
                }

                BackupMode.IMPORT -> open(uri, passphrase)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun export(
        uri: Uri,
        @PluralsRes done: Int,
        seal: suspend () -> DomainResult<SealedBackup, BackupError>,
    ) {
        when (val result = seal()) {
            is DomainResult.Failure -> abandon(result.error.toUiError())
            is DomainResult.Success -> {
                val written = withContext(dispatchers.io) { documents.write(uri, result.data.file) }
                if (written) {
                    finish()
                    send(BackupEffect.Done(done, result.data.configCount))
                } else {
                    abandon(UiError.Message(R.string.error_backup_write))
                }
            }
        }
    }

    
    private suspend fun open(uri: Uri, passphrase: BackupPassphrase) {
        val file = withContext(dispatchers.io) { documents.read(uri, MAX_FILE_BYTES) }
        if (file == null) {
            passphrase.wipe()
            return abandon(UiError.Message(R.string.error_backup_read))
        }
        when (val result = openBackupFile(file, passphrase)) {
            is DomainResult.Failure -> onOpenFailure(result.error)
            is DomainResult.Success -> {
                finish()
                val backup = result.data
                when {
                    backup.settings != null -> {
                        pendingRestore = backup
                        _state.update { it.copy(dialog = BackupDialog.ConfirmRestore(backup.configs.size)) }
                    }

                    backup.configs.isEmpty() ->
                        _state.update { it.copy(error = UiError.Message(R.string.error_import_no_config)) }

                    else -> {
                        pendingImports.addAll(backup.configs)
                        importedCount = 0
                        importNext()
                    }
                }
            }
        }
    }

    private fun onOpenFailure(error: BackupError) {
        if (error == BackupError.WrongPassphrase) {
            _state.update { it.copy(dialog = BackupDialog.Passphrase(BackupMode.IMPORT, wrongPassphrase = true)) }
        } else {
            abandon(error.toUiError())
        }
    }

    private fun onRestoreConfirmed() {
        val backup = pendingRestore ?: return
        if (_state.value.busy) return
        pendingRestore = null
        _state.update { it.copy(dialog = BackupDialog.None, busy = true) }
        viewModelScope.launch {
            when (val result = restoreBackup(backup)) {
                is DomainResult.Success -> {
                    send(BackupEffect.Restored)
                    send(BackupEffect.Done(R.plurals.backup_imported, result.data))
                }

                is DomainResult.Failure -> _state.update { it.copy(error = result.error.toUiError()) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    
    private suspend fun importNext() {
        val current = observeConfigs().first()
        val taken = when (current) {
            is DomainResult.Success -> current.data.mapTo(mutableSetOf()) { it.nickname.value }
            is DomainResult.Failure -> return abandonImports(current.error.toUiError())
        }
        while (true) {
            val config = pendingImports.firstOrNull() ?: return finishImports()
            val nickname = config.nickname.value
            if (nickname in taken) {
                val dialog = BackupDialog.RenameImport(nickname, uniqueNickname(nickname, taken), taken)
                _state.update { it.copy(dialog = dialog) }
                return
            }
            pendingImports.removeFirst()
            if (addConfig(config, nickname) is DomainResult.Success) {
                importedCount++
                taken += nickname
            }
        }
    }

    private fun onImportRenamed(nickname: String) {
        val dialog = _state.value.dialog as? BackupDialog.RenameImport ?: return
        if (_state.value.busy) return
        val config = pendingImports.firstOrNull() ?: return
        _state.update { it.copy(dialog = BackupDialog.None, busy = true) }
        viewModelScope.launch {
            when (val result = addConfig(config, nickname)) {
                is DomainResult.Success -> {
                    pendingImports.removeFirst()
                    importedCount++
                    importNext()
                }

                is DomainResult.Failure ->
                    _state.update { it.copy(dialog = dialog, error = result.error.toUiError()) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    
    private fun skipImport() {
        pendingImports.removeFirstOrNull()
        closeDialog()
        viewModelScope.launch { importNext() }
    }

    private fun abandonImports(error: UiError) {
        pendingImports.clear()
        closeDialog()
        _state.update { it.copy(error = error) }
    }

    private fun finishImports() {
        closeDialog()
        send(BackupEffect.Done(R.plurals.configs_imported, importedCount))
        importedCount = 0
    }

    private fun onDialogDismissed() {
        when (_state.value.dialog) {
            BackupDialog.None -> Unit
            is BackupDialog.Passphrase -> abandon()
            is BackupDialog.ConfirmRestore -> {
                pendingRestore = null
                closeDialog()
            }

            is BackupDialog.RenameImport -> if (!_state.value.busy) skipImport()
        }
    }

    private fun closeDialog() = _state.update { it.copy(dialog = BackupDialog.None) }
    private fun abandon(error: UiError? = null) {
        val uri = pendingUri
        if (pendingMode?.isExport == true && uri != null) {
            viewModelScope.launch(dispatchers.io) { documents.delete(uri) }
        }
        finish()
        _state.update { it.copy(dialog = BackupDialog.None, error = error ?: it.error) }
    }

    private fun finish() {
        pendingMode = null
        pendingUri = null
        pendingNickname = null
    }

    private fun send(effect: BackupEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        pendingRestore = null
        pendingImports.clear()
    }

    companion object {
        const val MIME_TYPE = "application/octet-stream"
        private const val EXTENSION = "fwknop"
        private const val MAX_FILE_BYTES = 1024 * 1024
        private const val KEY_MODE = "backup_mode"
        private const val KEY_URI = "backup_uri"
        private const val KEY_NICKNAME = "backup_nickname"

        
        private fun String.toFileNamePart(): String =
            replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "config" }
    }
}
