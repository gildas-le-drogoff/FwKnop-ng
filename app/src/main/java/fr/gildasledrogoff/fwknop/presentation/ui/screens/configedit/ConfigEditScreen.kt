// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
    val kind = state.draft.kind
    val title = if (state.isNew) stringResource(kind.newTitleRes()) else state.draft.nickname
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

            if (kind != ConfigKind.WAKE_ON_LAN) {
                IconButton(onClick = onScanQr) {
                    FwknopIcon(
                        R.drawable.ic_qr_code_scanner_rounded,
                        stringResource(R.string.action_scan_keys)
                    )
                }
            }
        },
    )
}

@StringRes
private fun ConfigKind.newTitleRes(): Int = when (this) {
    ConfigKind.FWKNOP -> R.string.title_new_fwknop
    ConfigKind.PORT_KNOCK -> R.string.title_new_port_knock
    ConfigKind.WAKE_ON_LAN -> R.string.title_new_wake_on_lan
}

@Composable
private fun SaveFab(onSave: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onSave,
        icon = { FwknopIcon(R.drawable.ic_check_rounded, null) },
        text = { Text(stringResource(R.string.action_save)) },
    )
}
