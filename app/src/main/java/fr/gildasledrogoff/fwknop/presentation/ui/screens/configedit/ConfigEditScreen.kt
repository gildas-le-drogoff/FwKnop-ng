package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.BackupEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEffect
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditState
import fr.gildasledrogoff.fwknop.presentation.state.SealedQrPrompt
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.PassphraseDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.PassphraseSpec
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberInteractionLauncher
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberQrScanner
import fr.gildasledrogoff.fwknop.presentation.ui.screens.backup.BackupHost
import fr.gildasledrogoff.fwknop.presentation.viewmodel.BackupViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.ConfigEditViewModel

@Composable
fun ConfigEditScreen(
    onBack: () -> Unit,
    viewModel: ConfigEditViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val launchInteraction = rememberInteractionLauncher { id, completed, data ->
        viewModel.onEvent(ConfigEditEvent.InteractionFinished(id, completed, data))
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ConfigEditEffect.Saved -> onBack()
            is ConfigEditEffect.LaunchInteraction ->
                viewModel.intentSender(effect.id)?.let { launchInteraction(effect.id, it) }
        }
    }
    
    BackupHost(backupViewModel, snackbarHostState) { if (it > 0) onBack() }
    val onImport = { backupViewModel.onEvent(BackupEvent.ImportRequested) }
    ConfigEditScaffold(
        state = state,
        bars = ConfigEditBars(snackbarHostState, importing = backup.busy, onImport = onImport),
        onEvent = viewModel::onEvent,
        onBack = onBack,
    )
    state.error?.let { ErrorDialog(it) { viewModel.onEvent(ConfigEditEvent.DismissError) } }
    if (state.pendingQr != null) QrImportDialog(viewModel::onEvent)
    state.sealedQr?.let { SealedQrDialog(it, viewModel::onEvent) }
}

@Composable
private fun SealedQrDialog(prompt: SealedQrPrompt, onEvent: (ConfigEditEvent) -> Unit) {
    PassphraseDialog(
        spec = PassphraseSpec(
            title = stringResource(R.string.qr_decrypt_title),
            hint = stringResource(R.string.hint_qr_decrypt),
            confirmLabel = stringResource(R.string.action_decrypt),
            creating = false,
            error = if (prompt.wrongPassphrase) stringResource(R.string.error_qr_wrong_passphrase) else null,
        ),
        onSubmit = { onEvent(ConfigEditEvent.SealedQrPassphraseEntered(it)) },
        onDismiss = { onEvent(ConfigEditEvent.SealedQrDismissed) },
    )
}

@Composable
private fun QrImportDialog(onEvent: (ConfigEditEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ConfigEditEvent.QrImportDismissed) },
        icon = { FwknopIcon(R.drawable.ic_qr_code_rounded, null) },
        title = { Text(stringResource(R.string.qr_import_title)) },
        text = { Text(stringResource(R.string.qr_import_body)) },
        confirmButton = {
            TextButton(onClick = { onEvent(ConfigEditEvent.QrImportSelected(fullConfig = true)) }) {
                Text(stringResource(R.string.qr_import_full))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConfigEditEvent.QrImportSelected(fullConfig = false)) }) {
                Text(stringResource(R.string.qr_import_keys))
            }
        },
    )
}


private class ConfigEditBars(
    val snackbarHostState: SnackbarHostState,
    val importing: Boolean,
    val onImport: () -> Unit,
)

@Composable
private fun ConfigEditScaffold(
    state: ConfigEditState,
    bars: ConfigEditBars,
    onEvent: (ConfigEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    val scanQr = rememberQrScanner { onEvent(ConfigEditEvent.QrScanned(it)) }
    Scaffold(
        topBar = { ConfigEditTopBar(state, bars, onBack, scanQr) },
        floatingActionButton = { SaveFab { onEvent(ConfigEditEvent.Save) } },
        snackbarHost = { SnackbarHost(bars.snackbarHostState) },
    ) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                ConfigEditContent(state, onEvent)
            }
            if (bars.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditTopBar(
    state: ConfigEditState,
    bars: ConfigEditBars,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
) {
    val title = if (state.isNew) stringResource(R.string.title_new_config) else state.draft.nickname
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                FwknopIcon(R.drawable.ic_arrow_back_rounded, stringResource(R.string.action_back))
            }
        },
        actions = {
            if (state.isNew) {
                IconButton(onClick = bars.onImport, enabled = !bars.importing) {
                    FwknopIcon(
                        R.drawable.ic_upload_rounded,
                        stringResource(R.string.action_import_config)
                    )
                }
            }
            IconButton(onClick = onScanQr) {
                FwknopIcon(
                    R.drawable.ic_qr_code_scanner_rounded,
                    stringResource(R.string.action_scan_keys)
                )
            }
        },
    )
}

@Composable
private fun SaveFab(onSave: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onSave,
        icon = { FwknopIcon(R.drawable.ic_check_rounded, null) },
        text = { Text(stringResource(R.string.action_save)) },
    )
}
