package fr.gildasledrogoff.fwknop.presentation.ui.screens.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.presentation.state.BackupDialog
import fr.gildasledrogoff.fwknop.presentation.state.BackupEffect
import fr.gildasledrogoff.fwknop.presentation.state.BackupEvent
import fr.gildasledrogoff.fwknop.presentation.state.BackupMode
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ConfirmDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.ConfirmSpec
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.NicknameDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.NicknameSpec
import fr.gildasledrogoff.fwknop.presentation.ui.components.PassphraseDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.PassphraseSpec
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberUserAuthenticator
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.BackupViewModel


@Composable
fun BackupHost(
    viewModel: BackupViewModel,
    snackbarHostState: SnackbarHostState,
    onRestored: () -> Unit = {},
    onDone: (count: Int) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val authenticator =
        rememberUserAuthenticator { viewModel.onEvent(BackupEvent.AuthCompleted(it)) }
    val onPicked: (Uri?) -> Unit = { viewModel.onEvent(BackupEvent.DocumentPicked(it)) }
    val exportTarget =
        rememberLauncherForActivityResult(CreateDocument(BackupViewModel.MIME_TYPE), onPicked)
    val importSource = rememberLauncherForActivityResult(OpenDocument(), onPicked)
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BackupEffect.PickExportTarget -> exportTarget.launch(effect.fileName)
            BackupEffect.PickImportSource -> importSource.launch(arrayOf("*/*"))
            is BackupEffect.Authenticate -> authenticator.launch(effect.purpose)
            BackupEffect.Restored -> onRestored()
            is BackupEffect.Done -> {
                onDone(effect.count)
                snackbarHostState.showSnackbar(
                    resources.getQuantityString(effect.resId, effect.count, effect.count),
                )
            }
        }
    }
    BackupDialogs(state.dialog, viewModel::onEvent)
    state.error?.let { ErrorDialog(it) { viewModel.onEvent(BackupEvent.DismissError) } }
}

@Composable
private fun BackupDialogs(dialog: BackupDialog, onEvent: (BackupEvent) -> Unit) {
    val dismiss = { onEvent(BackupEvent.DialogDismissed) }
    when (dialog) {
        BackupDialog.None -> Unit
        is BackupDialog.Passphrase -> PassphraseDialog(
            spec = backupPassphraseSpec(dialog),
            onSubmit = { onEvent(BackupEvent.PassphraseEntered(it)) },
            onDismiss = dismiss,
        )

        is BackupDialog.ConfirmRestore -> ConfirmDialog(
            spec = ConfirmSpec(
                title = stringResource(R.string.backup_import_confirm_title),
                text = pluralStringResource(
                    R.plurals.backup_import_confirm_body,
                    dialog.configCount,
                    dialog.configCount
                ),
                confirmLabel = stringResource(R.string.action_backup_restore),
            ),
            onConfirm = { onEvent(BackupEvent.RestoreConfirmed) },
            onDismiss = dismiss,
        )

        is BackupDialog.RenameImport -> NicknameDialog(
            spec = NicknameSpec(
                title = stringResource(R.string.import_rename_title, dialog.nickname),
                text = stringResource(R.string.import_rename_body),
                initial = dialog.suggestion,
                taken = dialog.taken,
                confirmLabel = stringResource(R.string.action_backup_import),
            ),
            onConfirm = { onEvent(BackupEvent.ImportRenamed(it)) },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun backupPassphraseSpec(dialog: BackupDialog.Passphrase): PassphraseSpec {
    val exporting = dialog.mode.isExport
    return PassphraseSpec(
        title = stringResource(if (exporting) R.string.backup_export_title else R.string.backup_import_title),
        hint = if (exporting) {
            stringResource(R.string.hint_backup_passphrase, BackupPassphrase.MIN_LENGTH)
        } else {
            stringResource(R.string.hint_backup_import_passphrase)
        },
        confirmLabel = stringResource(if (exporting) R.string.action_export else R.string.action_backup_import),
        creating = exporting,
        error = if (dialog.wrongPassphrase) stringResource(R.string.error_backup_wrong_passphrase) else null,
    )
}

@AdaptivePreviews
@Composable
private fun PassphraseDialogPreview() {
    FwknopTheme { BackupDialogs(BackupDialog.Passphrase(BackupMode.EXPORT_ALL)) {} }
}

@AdaptivePreviews
@Composable
private fun RenameImportDialogPreview() {
    FwknopTheme {
        BackupDialogs(BackupDialog.RenameImport("home", "home (2)", setOf("home"))) {}
    }
}
