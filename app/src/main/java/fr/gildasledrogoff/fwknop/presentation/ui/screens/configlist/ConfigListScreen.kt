// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.domain.model.PortCheck
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.description
import fr.gildasledrogoff.fwknop.presentation.ui.components.openAppSettings
import fr.gildasledrogoff.fwknop.presentation.ui.screens.backup.BackupHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.knock.KnockHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.knock.WakeOnLanHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc.NfcKnockReader
import fr.gildasledrogoff.fwknop.presentation.ui.screens.settings.SecurityHost
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.networkBackdrop
import fr.gildasledrogoff.fwknop.presentation.viewmodel.*
import kotlinx.coroutines.delay

@Immutable
data class ConfigListNavigation(
    val onCreate: (ConfigKind) -> Unit,
    val onEdit: (String) -> Unit,
    val onWriteNfc: (String) -> Unit,
    val onExportQr: (String) -> Unit,
    val onDiagnose: (String) -> Unit,
    val onSettings: () -> Unit,
    val onHistory: () -> Unit,
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
    wakeViewModel: WakeOnLanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val knockState by knockViewModel.state.collectAsStateWithLifecycle()
    val wakeState by wakeViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastSent by remember { mutableStateOf<KnockEffect.Sent?>(null) }
    var justSent by remember { mutableStateOf<KnockEffect.Sent?>(null) }
    var portChecked by remember { mutableStateOf<KnockEffect.PortChecked?>(null) }
    var woken by remember { mutableStateOf<WakeOnLanEffect.Sent?>(null) }
    val haptics = LocalHapticFeedback.current
    StartupEffects(state, navigation.onHelp)
    SecurityHost(securityViewModel, snackbarHostState)
    KnockHost(knockViewModel, onSent = {
        lastSent = it
        justSent = it
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }, onPortChecked = { portChecked = it }) {
        knockViewModel.onEvent(KnockEvent.Cancel)
        nfcEraseViewModel.offer(NfcEraseReason.FWKNOP)
    }
    NfcKnockReader(state.nfcKnockEnabled, knockViewModel, nfcEraseViewModel)
    WakeOnLanHost(wakeViewModel) { woken = it }
    BackupHost(backupViewModel, snackbarHostState)
    lastSent?.let { SentSnackbar(it, snackbarHostState) { lastSent = null } }
    portChecked?.let { PortCheckSnackbar(it, snackbarHostState) { portChecked = null } }
    woken?.let { WakeSentSnackbar(it, snackbarHostState) { woken = null } }
    justSent?.let { sent -> LaunchedEffect(sent) { delay(SUCCESS_MS); justSent = null } }
    val knock = KnockActivity(
        sending = (knockState as? KnockState.Sending)?.nickname,
        sent = justSent?.nickname,
        waking = (wakeState as? WakeOnLanState.Sending)?.nickname,
    )
    val actions = ConfigCardActions(
        onKnock = { knockViewModel.onEvent(KnockEvent.Knock(it)) },
        onEdit = navigation.onEdit,
        onDuplicate = {},
        onWriteNfc = navigation.onWriteNfc,
        onExportQr = navigation.onExportQr,
        onDiagnose = navigation.onDiagnose,
        onExportFile = { backupViewModel.onEvent(BackupEvent.ExportConfigRequested(it)) },
        onDelete = { viewModel.onEvent(ConfigListEvent.DeleteConfig(it)) },
        onWake = { wakeViewModel.onEvent(WakeOnLanEvent.Wake(it)) },
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
            IconButton(onClick = navigation.onHistory) {
                FwknopIcon(R.drawable.ic_history_rounded, stringResource(R.string.title_history))
            }
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
private fun NewConfigFab(onCreate: (ConfigKind) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    ExtendedFloatingActionButton(
        onClick = { choosing = true },
        icon = { FwknopIcon(R.drawable.ic_add_rounded, null) },
        text = { Text(stringResource(R.string.action_new_config)) },
    )
    if (choosing) {
        NewConfigDialog(
            onChoose = {
                choosing = false
                onCreate(it)
            },
            onCancel = { choosing = false },
        )
    }
}

@Composable
private fun SentSnackbar(
    sent: KnockEffect.Sent,
    hostState: SnackbarHostState,
    onShown: () -> Unit
) {
    val context = LocalContext.current
    val message =
        stringResource(sent.notice.messageRes(), sent.nickname) + "\n" + sent.source.description()
    val settingsLabel = stringResource(R.string.action_open_app_settings)
    LaunchedEffect(sent) {
        val result = when (sent.notice) {
            null -> hostState.showSnackbar(message)
            SentNotice.KEEP_OPEN_NEEDS_NOTIFICATIONS ->
                hostState.showSnackbar(
                    message,
                    actionLabel = settingsLabel,
                    duration = SnackbarDuration.Long
                )

            SentNotice.KEEP_OPEN_UNAVAILABLE -> hostState.showSnackbar(
                message,
                duration = SnackbarDuration.Long
            )
        }
        if (result == SnackbarResult.ActionPerformed) openAppSettings(context)
        onShown()
    }
}

@Composable
private fun PortCheckSnackbar(
    checked: KnockEffect.PortChecked,
    hostState: SnackbarHostState,
    onShown: () -> Unit,
) {
    val message = when (val check = checked.check) {
        is PortCheck.Reachable -> stringResource(R.string.port_check_reachable, checked.nickname)
        is PortCheck.Closed -> stringResource(
            R.string.port_check_closed,
            checked.nickname,
            check.ports.joinToString(", ") { "tcp/$it" },
        )
    }
    LaunchedEffect(checked) {
        hostState.showSnackbar(message, duration = SnackbarDuration.Long)
        onShown()
    }
}

@Composable
private fun WakeSentSnackbar(
    sent: WakeOnLanEffect.Sent,
    hostState: SnackbarHostState,
    onShown: () -> Unit
) {
    val message = stringResource(R.string.wake_sent, sent.nickname)
    LaunchedEffect(sent) {
        hostState.showSnackbar(message)
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
