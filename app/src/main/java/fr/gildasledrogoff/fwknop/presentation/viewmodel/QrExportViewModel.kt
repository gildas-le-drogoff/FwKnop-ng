// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.GetConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.HasQrExportPassphraseUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SealQrContentUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SetQrExportPassphraseUseCase
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.QrExportRoute
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DocumentFiles
import fr.gildasledrogoff.fwknop.utils.helpers.QrBitmapWriter
import fr.gildasledrogoff.fwknop.utils.helpers.QrShareCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class QrExportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getConfig: GetConfigUseCase,
    private val hasExportPassphrase: HasQrExportPassphraseUseCase,
    private val setExportPassphrase: SetQrExportPassphraseUseCase,
    private val sealQr: SealQrContentUseCase,
    private val qrWriter: QrBitmapWriter,
    private val shareCache: QrShareCache,
    private val documents: DocumentFiles,
    private val dispatchers: DispatcherProvider,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val nicknames: List<String> = savedStateHandle.toRoute<QrExportRoute>().nicknames
    private val _state = MutableStateFlow<QrExportState>(QrExportState.AwaitingAuth)
    val state: StateFlow<QrExportState> = _state.asStateFlow()
    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()
    private var qrBitmap: Bitmap? = null
    private var authPrompted = false
    private var configs: List<SpaConfig> = emptyList()
    private var pageJob: Job? = null
    private var page: Int
        get() = savedStateHandle[KEY_PAGE] ?: 0
        set(value) {
            savedStateHandle[KEY_PAGE] = value
        }
    private var encrypted: Boolean
        get() = savedStateHandle[KEY_ENCRYPTED] ?: false
        set(value) {
            savedStateHandle[KEY_ENCRYPTED] = value
        }
    private val sealedPages = mutableMapOf<Int, String>()
    private var shared: Boolean
        get() = savedStateHandle[KEY_SHARED] ?: false
        set(value) {
            savedStateHandle[KEY_SHARED] = value
        }

    fun claimAuthPrompt(): Boolean = !authPrompted.also { authPrompted = true }
    fun onAuthResult(result: AuthResult) {
        if (_state.value != QrExportState.AwaitingAuth) return
        if (!result.allowsSensitiveAction) {
            _state.value = QrExportState.Denied
            return
        }
        _state.value = QrExportState.Loading
        viewModelScope.launch {
            configs =
                nicknames.mapNotNull { (getConfig(Nickname(it)) as? DomainResult.Success)?.data }
            if (configs.isEmpty()) {
                _state.value = QrExportState.Failed(ConfigError.NotFound.toUiError())
            } else {
                showPage(page.coerceIn(configs.indices))
            }
        }
    }

    fun onPageChanged(delta: Int) {
        val target = page + delta
        if (_state.value !is QrExportState.Ready || target !in configs.indices) return
        pageJob?.cancel()
        pageJob = viewModelScope.launch { showPage(target) }
    }

    private fun redraw() {
        pageJob?.cancel()
        pageJob = viewModelScope.launch { showPage(page) }
    }

    private suspend fun showPage(index: Int) {
        page = index
        val config = configs[index]
        val content = if (encrypted) sealedContent(index) else config.toQrContent()
        if (content == null) {
            encrypted = false
            showPage(index)
            (_state.value as? QrExportState.Ready)?.let {
                _state.value = it.copy(askPassphrase = true)
            }
            return
        }
        val bitmap =
            withContext(dispatchers.default) { qrWriter.encode(content, QR_SIZE) }
        qrBitmap = bitmap
        _state.value = when {
            bitmap != null -> QrExportState.Ready(
                config.nickname.value,
                bitmap.asImageBitmap(),
                index,
                configs.size,
                encrypted,
            )

            configs.size == 1 -> QrExportState.Failed(UiError.Message(R.string.error_qr_encode))
            else -> QrExportState.Ready(config.nickname.value, null, index, configs.size, encrypted)
                .also { _messages.send(R.string.error_qr_encode) }
        }
        if (bitmap != null) writePendingPng()
    }

    private suspend fun sealedContent(index: Int): String? {
        sealedPages[index]?.let { return it }
        (_state.value as? QrExportState.Ready)?.let { _state.value = QrExportState.Loading }
        return sealQr(configs[index].toQrContent())?.also { sealedPages[index] = it }
    }

    fun onEncryptedChanged(enabled: Boolean) {
        val ready = _state.value as? QrExportState.Ready ?: return
        if (!enabled) {
            encrypted = false
            return redraw()
        }
        viewModelScope.launch {
            if (hasExportPassphrase()) {
                encrypted = true
                redraw()
            } else {
                _state.value = ready.copy(askPassphrase = true)
            }
        }
    }

    fun onChangePassphrase() {
        val ready = _state.value as? QrExportState.Ready ?: return
        _state.value = ready.copy(askPassphrase = true)
    }

    fun onPassphraseDismissed() {
        val ready = _state.value as? QrExportState.Ready ?: return
        _state.value = ready.copy(askPassphrase = false)
    }

    fun onPassphraseEntered(chars: CharArray) {
        val passphrase = BackupPassphrase(chars)
        val ready = _state.value as? QrExportState.Ready
        if (ready == null || !ready.askPassphrase) return passphrase.wipe()
        _state.value = ready.copy(askPassphrase = false)
        viewModelScope.launch {
            if (setExportPassphrase(passphrase) is DomainResult.Success) {
                sealedPages.clear()
                encrypted = true
                redraw()
            }
        }
    }

    fun savePng(uri: Uri) {
        savedStateHandle[KEY_PENDING_PNG] = uri
        writePendingPng()
    }

    private fun writePendingPng() {
        val bitmap = qrBitmap ?: return
        val uri = savedStateHandle.remove<Uri>(KEY_PENDING_PNG) ?: return
        viewModelScope.launch {
            val saved =
                withContext(dispatchers.io) { documents.write(uri) { bitmap.compressPng(it) } }
            _messages.send(if (saved) R.string.qr_export_saved else R.string.error_qr_write)
        }
    }

    fun pngFileName(): String {
        val nickname = (_state.value as? QrExportState.Ready)?.nickname.orEmpty()
        return "fwknop-" + nickname.filter { it.isLetterOrDigit() || it in "-_." } + ".png"
    }

    suspend fun sharePng(): Uri? = withContext(dispatchers.io) {
        val bitmap = qrBitmap ?: return@withContext null
        val file = shareCache.newFile()
        val written =
            runCatching { file.outputStream().use { bitmap.compressPng(it) } }.getOrDefault(false)
        shared = written
        if (written) shareCache.uriFor(file) else null
    }.also { if (it == null) _messages.send(R.string.error_qr_write) }

    fun onResumed() {
        if (!shared) return
        shared = false
        purgeShareCache()
    }

    override fun onCleared() {
        if (!shared) purgeShareCache()
    }

    private fun purgeShareCache() {
        appScope.launch(dispatchers.io) { shareCache.clear() }
    }

    private fun Bitmap.compressPng(out: OutputStream): Boolean =
        compress(Bitmap.CompressFormat.PNG, 100, out)

    private companion object {
        const val QR_SIZE = 1024
        const val KEY_PENDING_PNG = "pending_png"
        const val KEY_SHARED = "shared"
        const val KEY_PAGE = "page"
        const val KEY_ENCRYPTED = "encrypted"
    }
}
