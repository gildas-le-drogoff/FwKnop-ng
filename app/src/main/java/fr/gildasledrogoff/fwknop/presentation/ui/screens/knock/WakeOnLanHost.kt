// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.knock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanEffect
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanEvent
import fr.gildasledrogoff.fwknop.presentation.state.WakeOnLanState
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.viewmodel.WakeOnLanViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess

@Composable
fun WakeOnLanHost(viewModel: WakeOnLanViewModel, onSent: (WakeOnLanEffect.Sent) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onEvent = viewModel::onEvent
    val context = LocalContext.current
    val requestLocalNetwork = rememberPermissionRequest(
        LocalNetworkAccess.PERMISSION,
        onShown = { onEvent(WakeOnLanEvent.PermissionPromptLaunched) },
    ) { granted, permanentlyDenied ->
        onEvent(
            WakeOnLanEvent.PermissionResult(
                granted,
                permanentlyDenied
            )
        )
    }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is WakeOnLanEffect.Sent -> onSent(effect)
        }
    }
    val dismiss = { onEvent(WakeOnLanEvent.Dismiss) }
    when (val current = state) {
        WakeOnLanState.Idle, is WakeOnLanState.Sending, is WakeOnLanState.AwaitingPermission -> Unit
        is WakeOnLanState.ExplainPermission -> WakePermissionRationaleDialog(
            current.nickname,
            onContinue = requestLocalNetwork,
            onDecline = dismiss,
        )

        is WakeOnLanState.Failed -> {
            val action = if (current.fixInAppSettings) {
                DialogAction(stringResource(R.string.action_open_app_settings)) {
                    openAppSettings(context)
                    dismiss()
                }
            } else {
                DialogAction(stringResource(R.string.action_retry)) { onEvent(WakeOnLanEvent.Retry) }
            }
            ErrorDialog(
                current.error,
                action = action,
                title = stringResource(R.string.wake_failed_title),
                onDismiss = dismiss,
            )
        }
    }
}
