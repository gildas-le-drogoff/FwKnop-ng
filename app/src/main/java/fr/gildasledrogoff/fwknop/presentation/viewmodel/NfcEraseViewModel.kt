// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.viewmodel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.presentation.state.NfcEraseReason
import fr.gildasledrogoff.fwknop.presentation.state.NfcEraseState
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.NfcTagWriter
import fr.gildasledrogoff.fwknop.utils.helpers.NfcWriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class NfcEraseViewModel @Inject constructor(
    private val writer: NfcTagWriter,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<NfcEraseState>(NfcEraseState.Idle)
    val state: StateFlow<NfcEraseState> = _state.asStateFlow()
    private val erasing = AtomicBoolean(false)

    // Latest handle Android gave us: it only reports a tag again once it has left the field.
    @Volatile
    private var readTag: Tag? = null
    fun onTagRead(tag: Tag) {
        readTag = tag
    }

    fun offer(reason: NfcEraseReason) {
        if (_state.value != NfcEraseState.Idle) return
        _state.value = NfcEraseState.Confirm(reason)
    }

    fun confirm() {
        val confirm = _state.value as? NfcEraseState.Confirm ?: return
        _state.value = NfcEraseState.Waiting(confirm.reason.wholeTag)
        readTag?.let(::onTagDiscovered)
    }

    fun dismiss() {
        _state.value = NfcEraseState.Idle
    }

    fun onTagDiscovered(tag: Tag) {
        readTag = tag
        val waiting = _state.value as? NfcEraseState.Waiting ?: return
        if (!erasing.compareAndSet(false, true)) return
        viewModelScope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    if (waiting.wholeTag) writer.wipe(tag) else writer.erase(
                        tag
                    )
                }
            } finally {
                erasing.set(false)
            }
            // The tag was re-detected while this attempt ran on a stale handle: retry with it.
            val latest = readTag
            if (result == NfcWriteResult.LOST && latest != null && latest !== tag) {
                return@launch onTagDiscovered(latest)
            }
            _state.value = when (result) {
                NfcWriteResult.WRITTEN -> NfcEraseState.Erased
                NfcWriteResult.LOST -> waiting
                else -> NfcEraseState.Failed(result.toUiError())
            }
        }
    }
}
