package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


@Immutable
data class PassphraseSpec(
    val title: String,
    val hint: String,
    val confirmLabel: String,
    val creating: Boolean,
    val error: String? = null,
)

@Composable
fun PassphraseDialog(spec: PassphraseSpec, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember(spec) { mutableStateOf("") }
    var confirmation by remember(spec) { mutableStateOf("") }
    val longEnough = BackupPassphrase.isLongEnough(passphrase)
    val valid =
        if (spec.creating) longEnough && passphrase == confirmation else passphrase.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
                val mismatch = spec.creating && confirmation.isNotEmpty() && passphrase != confirmation
                PassphraseHint(spec, mismatch)
                SecretTextField(
                    passphrase,
                    { passphrase = it },
                    stringResource(R.string.field_backup_passphrase)
                )
                if (spec.creating) {
                    SecretTextField(
                        confirmation,
                        { confirmation = it },
                        stringResource(R.string.field_backup_passphrase_confirm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(passphrase.toCharArray()) }, enabled = valid) {
                Text(spec.confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PassphraseHint(spec: PassphraseSpec, mismatch: Boolean) {
    val (text, isError) = when {
        spec.error != null -> spec.error to true
        mismatch -> stringResource(R.string.error_backup_passphrase_mismatch) to true
        else -> spec.hint to false
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@AdaptivePreviews
@Composable
private fun PassphraseDialogPreview() {
    FwknopTheme {
        PassphraseDialog(
            PassphraseSpec("Encrypt the backup", "At least 2 characters.", "Export", creating = true),
            {},
            {})
    }
}
