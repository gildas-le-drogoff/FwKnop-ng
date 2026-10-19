// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.FwknopExportEffect
import fr.gildasledrogoff.fwknop.presentation.state.FwknopExportEvent
import fr.gildasledrogoff.fwknop.presentation.state.FwknopExportState
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopBlocksDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberUserAuthenticator
import fr.gildasledrogoff.fwknop.presentation.viewmodel.FwknopExportViewModel

@Composable
fun FwknopExportHost(viewModel: FwknopExportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticator =
        rememberUserAuthenticator { viewModel.onEvent(FwknopExportEvent.AuthCompleted(it)) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            FwknopExportEffect.Authenticate -> authenticator.launch(AuthPurpose.EXPORT_CONFIG)
        }
    }
    val dismiss = { viewModel.onEvent(FwknopExportEvent.Dismissed) }
    when (val current = state) {
        FwknopExportState.Idle, is FwknopExportState.Authenticating -> Unit
        is FwknopExportState.Showing -> FwknopBlocksDialog(current.blocks, onDismiss = dismiss)
        is FwknopExportState.Failed -> ErrorDialog(current.error, onDismiss = dismiss)
    }
}
