package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEffect
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEvent
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.rememberUserAuthenticator
import fr.gildasledrogoff.fwknop.presentation.viewmodel.SecuritySettingsViewModel


@Composable
fun SecurityHost(viewModel: SecuritySettingsViewModel, snackbarHostState: SnackbarHostState?) {
    val resources = LocalResources.current
    val security by viewModel.state.collectAsStateWithLifecycle()
    val authenticator =
        rememberUserAuthenticator { viewModel.onEvent(SecuritySettingsEvent.AuthCompleted(it)) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SecuritySettingsEffect.Authenticate -> authenticator.launch(effect.purpose)
            is SecuritySettingsEffect.Message ->
                snackbarHostState?.showSnackbar(resources.getString(effect.resId))
        }
    }
    security.error?.let { ErrorDialog(it) { viewModel.onEvent(SecuritySettingsEvent.DismissError) } }
}
