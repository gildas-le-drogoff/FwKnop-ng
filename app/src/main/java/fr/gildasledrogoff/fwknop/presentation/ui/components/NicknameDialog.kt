// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Immutable
data class NicknameSpec(
    val title: String,
    val text: String?,
    val initial: String,
    val taken: Set<String>,
    val confirmLabel: String,
)

@Composable
fun NicknameDialog(spec: NicknameSpec, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var nickname by rememberSaveable(spec.initial) { mutableStateOf(spec.initial) }
    val trimmed = nickname.trim()
    val error = when {
        trimmed.isEmpty() -> R.string.error_nickname_blank
        trimmed in spec.taken -> R.string.error_nickname_taken
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
                spec.text?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FormTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = stringResource(R.string.field_nickname),
                    supportingText = error?.let { stringResource(it) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = error == null
            ) { Text(spec.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@AdaptivePreviews
@Composable
private fun NicknameDialogPreview() {
    FwknopTheme {
        NicknameDialog(
            NicknameSpec("Duplicate home", null, "home (2)", setOf("home"), "Duplicate"),
            {},
            {},
        )
    }
}
