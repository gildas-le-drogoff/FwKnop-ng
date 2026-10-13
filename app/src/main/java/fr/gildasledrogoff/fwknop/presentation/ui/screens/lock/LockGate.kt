package fr.gildasledrogoff.fwknop.presentation.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.LockGateEvent
import fr.gildasledrogoff.fwknop.presentation.state.LockGateState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.text
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ConfirmDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.ConfirmSpec
import fr.gildasledrogoff.fwknop.presentation.ui.components.ShieldLockIllustration
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberUserAuthenticator
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.networkBackdrop
import fr.gildasledrogoff.fwknop.presentation.viewmodel.LockGateViewModel

@Immutable
data class LockActions(val onUnlock: () -> Unit, val onReset: () -> Unit)

@Composable
fun LockGate(
    viewModel: LockGateViewModel,
    locked: @Composable (state: LockGateState, actions: LockActions) -> Unit,
    onAuthDismissed: () -> Unit = {},
    unlocked: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticator = rememberUserAuthenticator { result ->
        viewModel.onEvent(LockGateEvent.AuthCompleted(result))
        if (result is AuthResult.Cancelled || result is AuthResult.Error) onAuthDismissed()
    }
    CollectEffects(viewModel.effects) { authenticator.launch(AuthPurpose.UNLOCK) }
    if (state == LockGateState.Unlocked) {
        unlocked()
    } else {
        val actions = remember(viewModel) {
            LockActions(
                onUnlock = { viewModel.onEvent(LockGateEvent.UnlockRequested) },
                onReset = { viewModel.onEvent(LockGateEvent.ResetConfirmed) },
            )
        }
        locked(state, actions)
    }
}

@Composable
fun LockScreenContent(state: LockGateState, actions: LockActions) {
    Scaffold(
        modifier = Modifier.networkBackdrop(glow = true),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                is LockGateState.Locked -> LockedBody(state.error, actions.onUnlock)
                LockGateState.Invalidated -> InvalidatedBody(actions.onReset)
                LockGateState.Opening, LockGateState.Unlocked -> CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun LockedBody(error: UiError?, onUnlock: () -> Unit) {
    LockMessage(
        title = stringResource(R.string.lock_title),
        body = error?.text() ?: stringResource(R.string.lock_body),
        isError = error != null,
    ) {
        Button(onClick = onUnlock) { Text(stringResource(R.string.action_unlock)) }
    }
}

@Composable
private fun InvalidatedBody(onReset: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    LockMessage(
        title = stringResource(R.string.lock_invalidated_title),
        body = stringResource(R.string.lock_invalidated_body),
        isError = true,
        tint = MaterialTheme.colorScheme.error,
    ) {
        Button(onClick = {
            confirming = true
        }) { Text(stringResource(R.string.action_reset_vault)) }
    }
    if (confirming) {
        ConfirmDialog(
            spec = ConfirmSpec(
                title = stringResource(R.string.reset_confirm_title),
                text = stringResource(R.string.reset_confirm_body),
                confirmLabel = stringResource(R.string.action_reset_vault),
            ),
            onConfirm = {
                confirming = false
                onReset()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun LockMessage(
    title: String,
    body: String,
    isError: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    action: @Composable () -> Unit,
) {
    val dimens = DimensTheme.dimens
    val bodyColor =
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        modifier = Modifier
            .fillMaxWidth(dimens.contentWidthFraction)
            .padding(dimens.screenPadding),
    ) {
        ShieldLockIllustration(tint = tint)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = bodyColor,
            textAlign = TextAlign.Center
        )
        action()
    }
}

@AdaptivePreviews
@Composable
private fun LockScreenContentPreview() {
    FwknopTheme { LockScreenContent(LockGateState.Locked(), PreviewActions) }
}

@AdaptivePreviews
@Composable
private fun LockScreenErrorPreview() {
    FwknopTheme {
        LockScreenContent(
            LockGateState.Locked(UiError.Message(R.string.error_auth_failed)),
            PreviewActions
        )
    }
}

@AdaptivePreviews
@Composable
private fun LockScreenInvalidatedPreview() {
    FwknopTheme { LockScreenContent(LockGateState.Invalidated, PreviewActions) }
}

private val PreviewActions = LockActions(onUnlock = {}, onReset = {})
