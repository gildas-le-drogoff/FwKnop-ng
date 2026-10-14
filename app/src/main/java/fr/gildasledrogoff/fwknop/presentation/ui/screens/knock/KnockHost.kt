package fr.gildasledrogoff.fwknop.presentation.ui.screens.knock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.KnockEffect
import fr.gildasledrogoff.fwknop.presentation.state.KnockEvent
import fr.gildasledrogoff.fwknop.presentation.state.KnockPermission
import fr.gildasledrogoff.fwknop.presentation.state.KnockState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.DialogAction
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.openAppSettings
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberExternalAppLauncher
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberInteractionLauncher
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberPermissionRequest
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberQrScanner
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberUserAuthenticator
import fr.gildasledrogoff.fwknop.presentation.viewmodel.KnockViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions

@Composable
fun KnockHost(
    viewModel: KnockViewModel,
    onSent: (KnockEffect.Sent) -> Unit,
    onPortChecked: (KnockEffect.PortChecked) -> Unit = {},
    onAborted: () -> Unit = {},
    
    onEraseTag: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var appError by remember { mutableStateOf<UiError?>(null) }
    val launchApp = rememberExternalAppLauncher { appError = it }
    val launchInteraction = rememberInteractionLauncher { id, completed, data ->
        viewModel.onEvent(KnockEvent.InteractionFinished(id, completed, data))
    }
    val authenticator = rememberUserAuthenticator { viewModel.onEvent(KnockEvent.NfcAuthCompleted(it)) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            KnockEffect.Authenticate -> authenticator.launch(AuthPurpose.NFC_KNOCK)
            is KnockEffect.LaunchApp -> launchApp(effect.app, effect.nickname)
            is KnockEffect.LaunchInteraction ->
                viewModel.intentSender(effect.id)?.let { launchInteraction(effect.id, it) }

            is KnockEffect.Sent -> onSent(effect)
            is KnockEffect.PortChecked -> onPortChecked(effect)
            KnockEffect.Aborted -> onAborted()
        }
    }
    KnockDialogs(state, viewModel::onEvent, onEraseTag)
    appError?.let { ErrorDialog(it) { appError = null } }
}

@Composable
private fun KnockDialogs(state: KnockState, onEvent: (KnockEvent) -> Unit, onEraseTag: (() -> Unit)?) {
    val scanQr = rememberQrScanner { onEvent(KnockEvent.SourceIpScanned(it)) }
    val requestPermission = rememberKnockPermissionRequests(onEvent)
    val cancel = { onEvent(KnockEvent.Cancel) }
    when (state) {
        KnockState.Idle, KnockState.Authenticating, is KnockState.AwaitingPermission -> Unit
        is KnockState.ChooseConfig -> ChooseConfigDialog(
            state.nicknames,
            { onEvent(KnockEvent.ConfigChosen(it)) },
            cancel,
            onEraseTag,
        )

        is KnockState.PromptKey -> KeyPromptDialog(
            state.nickname,
            { onEvent(KnockEvent.KeySubmitted(it)) },
            cancel
        )

        is KnockState.PromptSourceIp -> SourceIpPromptDialog(
            prompt = state,
            actions = SourceIpPromptActions(
                onSubmit = { onEvent(KnockEvent.SourceIpSubmitted(it)) },
                onScan = scanQr,
                onCancel = cancel,
            ),
        )

        is KnockState.ExplainPermission -> PermissionRationaleDialog(
            state,
            onContinue = { requestPermission(state.permission) },
            onDecline = { onEvent(KnockEvent.PermissionDeclined) },
        )
        is KnockState.Sending -> SendingDialog(state.nickname, cancel)
        is KnockState.Failed -> FailedDialog(state, onEvent, onEraseTag)
    }
}



@Composable
private fun rememberKnockPermissionRequests(onEvent: (KnockEvent) -> Unit): (KnockPermission) -> Unit {
    val onShown = { onEvent(KnockEvent.PermissionPromptLaunched) }
    val onResult = { granted: Boolean, permanentlyDenied: Boolean ->
        onEvent(KnockEvent.PermissionResult(granted, permanentlyDenied))
    }
    val notifications = rememberPermissionRequest(RuntimePermissions.NOTIFICATIONS, onShown, onResult)
    val localNetwork = rememberPermissionRequest(LocalNetworkAccess.PERMISSION, onShown, onResult)
    return { permission ->
        when (permission) {
            KnockPermission.NOTIFICATIONS -> notifications()
            KnockPermission.LOCAL_NETWORK -> localNetwork()
        }
    }
}

@Composable
private fun FailedDialog(state: KnockState.Failed, onEvent: (KnockEvent) -> Unit, onEraseTag: (() -> Unit)?) {
    val context = LocalContext.current
    val dismiss = { onEvent(KnockEvent.Cancel) }
    val action = when {
        state.fixInAppSettings -> DialogAction(stringResource(R.string.action_open_app_settings)) {
            openAppSettings(context)
            dismiss()
        }

        state.tagErasable && onEraseTag != null -> DialogAction(stringResource(R.string.nfc_erase), onEraseTag)
        state.retryable -> DialogAction(stringResource(R.string.action_retry)) { onEvent(KnockEvent.Retry) }
        else -> null
    }
    ErrorDialog(state.error, action = action, title = stringResource(R.string.knock_failed_title), onDismiss = dismiss)
}
