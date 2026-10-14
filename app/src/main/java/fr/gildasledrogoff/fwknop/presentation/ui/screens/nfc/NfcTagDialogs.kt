// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.NfcEraseReason
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun NfcEraseConfirmDialog(reason: NfcEraseReason, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body, confirm) = when (reason) {
        NfcEraseReason.FOREIGN -> Triple(
            R.string.nfc_foreign_title,
            R.string.nfc_foreign_body,
            R.string.nfc_erase_all
        )

        NfcEraseReason.LEGACY -> Triple(
            R.string.nfc_read_legacy_title,
            R.string.nfc_read_legacy,
            R.string.nfc_erase
        )

        NfcEraseReason.FWKNOP -> Triple(
            R.string.nfc_erase_confirm_title,
            R.string.nfc_erase_confirm_body,
            R.string.nfc_erase
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FwknopIcon(R.drawable.ic_nfc_rounded, null) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nfc_do_nothing)) } },
    )
}

@Composable
fun NfcErasingDialog(wholeTag: Boolean, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { FwknopIcon(R.drawable.ic_nfc_rounded, null) },
        title = { Text(stringResource(R.string.nfc_erase_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
            ) {
                CircularProgressIndicator()
                Text(stringResource(if (wholeTag) R.string.nfc_wipe_body else R.string.nfc_erase_body))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@AdaptivePreviews
@Composable
private fun NfcForeignTagDialogPreview() {
    FwknopTheme { NfcEraseConfirmDialog(NfcEraseReason.FOREIGN, {}, {}) }
}

@AdaptivePreviews
@Composable
private fun NfcLegacyTagDialogPreview() {
    FwknopTheme { NfcEraseConfirmDialog(NfcEraseReason.LEGACY, {}, {}) }
}

@AdaptivePreviews
@Composable
private fun NfcErasingDialogPreview() {
    FwknopTheme { NfcErasingDialog(wholeTag = true) {} }
}
