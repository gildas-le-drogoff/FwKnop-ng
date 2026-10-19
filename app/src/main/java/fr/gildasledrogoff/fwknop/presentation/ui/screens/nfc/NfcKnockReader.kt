// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc

import android.nfc.Tag
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.viewmodel.KnockViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.NfcEraseViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.FwknopNdef
import fr.gildasledrogoff.fwknop.utils.helpers.FwknopTagContent
import kotlinx.coroutines.launch

@Composable
fun NfcKnockReader(
    enabled: Boolean,
    knockViewModel: KnockViewModel,
    eraseViewModel: NfcEraseViewModel
) {
    val erase by eraseViewModel.state.collectAsStateWithLifecycle()
    var unsupported by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val knockOnTag by rememberUpdatedState(enabled)
    // Reader mode stays on while busy and busy-time tags are ignored: Android reports a tag only
    // once while it stays in the field, so toggling reader mode would read a resting tag again
    // (a second knock, or a just-erased tag offered for erasing again) and drop its connection.
    NfcReaderMode(enabled = enabled || erase != NfcEraseState.Idle) { tag ->
        when (eraseViewModel.state.value) {
            is NfcEraseState.Waiting -> eraseViewModel.onTagDiscovered(tag)
            is NfcEraseState.Confirm -> eraseViewModel.onTagRead(tag)
            else -> scope.launch {
                if (!knockOnTag || unsupported) return@launch
                when (onTagRead(tag, knockViewModel, eraseViewModel)) {
                    FwknopTagContent.Unsupported -> unsupported = true
                    FwknopTagContent.Empty ->
                        Toast.makeText(context, R.string.nfc_read_blank, Toast.LENGTH_LONG).show()

                    else -> Unit
                }
            }
        }
    }
    NfcEraseContent(erase, eraseViewModel)
    if (unsupported) ErrorDialog(UiError.Message(R.string.nfc_read_unsupported)) {
        unsupported = false
    }
}

private fun onTagRead(
    tag: Tag,
    knockViewModel: KnockViewModel,
    eraseViewModel: NfcEraseViewModel
): FwknopTagContent? {
    if (knockViewModel.state.value != KnockState.Idle || eraseViewModel.state.value != NfcEraseState.Idle) {
        return null
    }
    eraseViewModel.onTagRead(tag)
    val content = FwknopNdef.read(tag)
    when (content) {
        is FwknopTagContent.Found -> knockViewModel.onEvent(KnockEvent.KnockFromNfc(content.digests))
        FwknopTagContent.Legacy -> eraseViewModel.offer(NfcEraseReason.LEGACY)
        FwknopTagContent.Foreign -> eraseViewModel.offer(NfcEraseReason.FOREIGN)
        FwknopTagContent.Empty, FwknopTagContent.Unsupported -> Unit
    }
    return content
}

@Composable
private fun NfcEraseContent(state: NfcEraseState, viewModel: NfcEraseViewModel) {
    val context = LocalContext.current
    when (state) {
        NfcEraseState.Idle -> Unit
        is NfcEraseState.Confirm -> NfcEraseConfirmDialog(
            state.reason,
            viewModel::confirm,
            viewModel::dismiss
        )

        is NfcEraseState.Waiting -> NfcErasingDialog(state.wholeTag, onCancel = viewModel::dismiss)
        NfcEraseState.Erased -> LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.nfc_erased, Toast.LENGTH_SHORT).show()
            viewModel.dismiss()
        }

        is NfcEraseState.Failed -> ErrorDialog(state.error, onDismiss = viewModel::dismiss)
    }
}
