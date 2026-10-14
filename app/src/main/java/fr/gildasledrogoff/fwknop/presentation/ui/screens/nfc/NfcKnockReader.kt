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
    val knock by knockViewModel.state.collectAsStateWithLifecycle()
    val erase by eraseViewModel.state.collectAsStateWithLifecycle()
    var unsupported by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val idle = knock == KnockState.Idle && erase == NfcEraseState.Idle && !unsupported
    NfcReaderMode(enabled = erase is NfcEraseState.Waiting || enabled && idle) { tag ->
        if (eraseViewModel.state.value is NfcEraseState.Waiting) {
            eraseViewModel.onTagDiscovered(tag)
        } else {
            scope.launch { if (onTagRead(tag, knockViewModel, eraseViewModel)) unsupported = true }
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
): Boolean {
    if (knockViewModel.state.value != KnockState.Idle || eraseViewModel.state.value != NfcEraseState.Idle) {
        return false
    }
    eraseViewModel.onTagRead(tag)
    when (val content = FwknopNdef.read(tag)) {
        is FwknopTagContent.Found -> knockViewModel.onEvent(KnockEvent.KnockFromNfc(content.digests))
        FwknopTagContent.Legacy -> eraseViewModel.offer(NfcEraseReason.LEGACY)
        FwknopTagContent.Foreign -> eraseViewModel.offer(NfcEraseReason.FOREIGN)
        FwknopTagContent.Unsupported -> return true
    }
    return false
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
