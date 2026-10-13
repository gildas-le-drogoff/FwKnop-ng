package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.BackupEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigListEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigListState
import fr.gildasledrogoff.fwknop.presentation.state.KnockEffect
import fr.gildasledrogoff.fwknop.presentation.state.KnockEvent
import fr.gildasledrogoff.fwknop.presentation.state.KnockState
import fr.gildasledrogoff.fwknop.presentation.state.NfcEraseReason
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.SentNotice
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.description
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.openAppSettings
import fr.gildasledrogoff.fwknop.presentation.ui.screens.backup.BackupHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.knock.KnockHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc.NfcKnockReader
import fr.gildasledrogoff.fwknop.presentation.ui.screens.settings.SecurityHost
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.networkBackdrop
import fr.gildasledrogoff.fwknop.presentation.viewmodel.BackupViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.ConfigListViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.KnockViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.NfcEraseViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.SecuritySettingsViewModel
import kotlinx.coroutines.delay

@Immutable
data class ConfigListNavigation(
    val onCreate: () -> Unit,
    val onEdit: (String) -> Unit,
    val onWriteNfc: (String) -> Unit,
    val onExportQr: (String) -> Unit,
    val onSettings: () -> Unit,
    val onHelp: () -> Unit,
)

@Immutable
data class LockSuggestionActions(val onEnable: () -> Unit, val onDismiss: () -> Unit)

@Composable
fun ConfigListScreen(
    navigation: ConfigListNavigation,
    viewModel: ConfigListViewModel = hiltViewModel(),
    knockViewModel: KnockViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    nfcEraseViewModel: NfcEraseViewModel = hiltViewModel(),
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val knockState by knockViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastSent by remember { mutableStateOf<KnockEffect.Sent?>(null) }
    var justSent by remember { mutableStateOf<KnockEffect.Sent?>(null) }
    val haptics = LocalHapticFeedback.current
    StartupEffects(state, navigation.onHelp)
    SecurityHost(securityViewModel, snackbarHostState)
    KnockHost(knockViewModel, onSent = {
        lastSent = it
        justSent = it
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }) {
        knockViewModel.onEvent(KnockEvent.Cancel)
        nfcEraseViewModel.offer(NfcEraseReason.FWKNOP)
    }
    NfcKnockReader(state.nfcKnockEnabled, knockViewModel, nfcEraseViewModel)
    BackupHost(backupViewModel, snackbarHostState)
    lastSent?.let { SentSnackbar(it, snackbarHostState) { lastSent = null } }
    justSent?.let { sent -> LaunchedEffect(sent) { delay(SUCCESS_MS); justSent = null } }
    val knock = KnockActivity(
        sending = (knockState as? KnockState.Sending)?.nickname,
        sent = justSent?.nickname,
    )
    val actions = ConfigCardActions(
        onKnock = { knockViewModel.onEvent(KnockEvent.Knock(it)) },
        onEdit = navigation.onEdit,
        onDuplicate = {},
        onWriteNfc = navigation.onWriteNfc,
        onExportQr = navigation.onExportQr,
        onExportFile = { backupViewModel.onEvent(BackupEvent.ExportConfigRequested(it)) },
        onDelete = { viewModel.onEvent(ConfigListEvent.DeleteConfig(it)) },
    )
    ConfigListScaffold(
        state = state,
        navigation = navigation,
        actions = actions,
        snackbarHostState = snackbarHostState,
        knock = knock,
        lockSuggestion = LockSuggestionActions(
            onEnable = { securityViewModel.onEvent(SecuritySettingsEvent.LockToggled(true)) },
            onDismiss = { viewModel.onEvent(ConfigListEvent.DismissLockSuggestion) },
        ),
    ) { source, nickname ->
        viewModel.onEvent(ConfigListEvent.DuplicateConfig(source, nickname))
    }
    state.error?.let { ErrorDialog(it) { viewModel.onEvent(ConfigListEvent.DismissError) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigListScaffold(
    state: ConfigListState,
    navigation: ConfigListNavigation,
    actions: ConfigCardActions,
    snackbarHostState: SnackbarHostState,
    knock: KnockActivity,
    lockSuggestion: LockSuggestionActions,
    onDuplicate: (source: String, nickname: String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .networkBackdrop(glow = true)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = { ConfigListTopBar(navigation, scrollBehavior) },
        floatingActionButton = { NewConfigFab(navigation.onCreate) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.suggestLock) {
                LockSuggestionCard(
                    onEnable = lockSuggestion.onEnable,
                    onDismiss = lockSuggestion.onDismiss,
                    modifier = Modifier.padding(
                        start = DimensTheme.dimens.screenPadding,
                        top = DimensTheme.dimens.screenPadding,
                        end = DimensTheme.dimens.screenPadding,
                    ),
                )
            }
            ConfigListContent(state, actions, Modifier.weight(1f), knock, onDuplicate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigListTopBar(
    navigation: ConfigListNavigation,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        scrollBehavior = scrollBehavior,
        
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        actions = {
            IconButton(onClick = navigation.onHelp) {
                FwknopIcon(R.drawable.ic_help_rounded, stringResource(R.string.title_help))
            }
            IconButton(onClick = navigation.onSettings) {
                FwknopIcon(R.drawable.ic_settings_rounded, stringResource(R.string.title_settings))
            }
        },
    )
}

@Composable
private fun NewConfigFab(onCreate: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onCreate,
        icon = { FwknopIcon(R.drawable.ic_add_rounded, null) },
        text = { Text(stringResource(R.string.action_new_config)) },
    )
}

@Composable
private fun SentSnackbar(sent: KnockEffect.Sent, hostState: SnackbarHostState, onShown: () -> Unit) {
    val context = LocalContext.current
    
    val message = stringResource(sent.notice.messageRes(), sent.nickname) + "\n" + sent.source.description()
    val settingsLabel = stringResource(R.string.action_open_app_settings)
    LaunchedEffect(sent) {
        val result = when (sent.notice) {
            null -> hostState.showSnackbar(message)
            SentNotice.KEEP_OPEN_NEEDS_NOTIFICATIONS ->
                hostState.showSnackbar(message, actionLabel = settingsLabel, duration = SnackbarDuration.Long)
            SentNotice.KEEP_OPEN_UNAVAILABLE -> hostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
        if (result == SnackbarResult.ActionPerformed) openAppSettings(context)
        onShown()
    }
}

@StringRes
private fun SentNotice?.messageRes(): Int = when (this) {
    null -> R.string.knock_sent
    SentNotice.KEEP_OPEN_NEEDS_NOTIFICATIONS -> R.string.knock_sent_keep_open_blocked
    SentNotice.KEEP_OPEN_UNAVAILABLE -> R.string.knock_sent_keep_open_unavailable
}


private const val SUCCESS_MS = 2_500L

@Composable
private fun StartupEffects(state: ConfigListState, onHelp: () -> Unit) {
    LaunchedEffect(state.showHelpOnStart) { if (state.showHelpOnStart) onHelp() }
}
