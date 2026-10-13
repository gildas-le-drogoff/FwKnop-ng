package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.text
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun ErrorDialog(
    error: UiError,
    action: DialogAction? = null,
    title: String? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            FwknopIcon(
                R.drawable.ic_error_rounded,
                null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title ?: stringResource(R.string.error_title)) },
        text = { Text(error.text()) },
        confirmButton = {
            if (action == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(onClick = action.onClick) { Text(action.label) }
            }
        },
        dismissButton = action?.let {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        },
    )
}

@Immutable
data class DialogAction(val label: String, val onClick: () -> Unit)

@Immutable
data class ConfirmSpec(val title: String, val text: String, val confirmLabel: String)

@Composable
fun ConfirmDialog(spec: ConfirmSpec, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = { Text(spec.text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(spec.confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@AdaptivePreviews
@Composable
private fun ErrorDialogPreview() {
    FwknopTheme { ErrorDialog(UiError.Message(R.string.error_server_invalid), onDismiss = {}) }
}

@AdaptivePreviews
@Composable
private fun ConfirmDialogPreview() {
    FwknopTheme {
        ConfirmDialog(
            ConfirmSpec("Delete home?", "This cannot be undone.", "Delete"),
            {},
            {})
    }
}
