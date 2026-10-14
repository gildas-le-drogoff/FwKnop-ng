// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.NfcWriteNotices
import fr.gildasledrogoff.fwknop.presentation.state.NfcWriteState
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.text
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.screens.settings.SecurityHost
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.NfcWriteViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.SecuritySettingsViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess

@Composable
fun NfcWriteScreen(
    onBack: () -> Unit,
    viewModel: NfcWriteViewModel = hiltViewModel(),
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val keepOtherConfigs by viewModel.keepOtherConfigs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SecurityHost(securityViewModel, snackbarHostState = null)
    val requestLocalNetwork =
        rememberPermissionRequest(
            LocalNetworkAccess.PERMISSION,
            onResult = viewModel::onPermissionResult
        )
    NfcReaderMode(
        enabled = state is NfcWriteState.Waiting || state is NfcWriteState.Failed || state is NfcWriteState.Writing,
        onAvailable = viewModel::onNfcAvailable,
        onUnavailable = viewModel::onNfcUnavailable,
        onTag = viewModel::onTagDiscovered,
    )
    NfcWriteScaffold(
        content = NfcWriteUi(
            viewModel.nickname,
            state,
            notices,
            notices.localNetworkPermanentlyDenied,
            keepOtherConfigs,
        ),
        actions = NfcWriteActions(
            onBack = onBack,
            onEnableLock = { securityViewModel.onEvent(SecuritySettingsEvent.LockToggled(true)) },
            onOpenNfcSettings = { openNfcSettings(context) },
            onEnableNfcKnock = viewModel::enableNfcKnock,
            onKeepOtherConfigsChange = viewModel::setKeepOtherConfigs,
            onGrantLocalNetwork = {
                if (notices.localNetworkPermanentlyDenied) {
                    openAppSettings(context)
                } else {
                    requestLocalNetwork()
                }
            },
        ),
    )
}

@Composable
fun NfcReaderMode(
    enabled: Boolean,
    onAvailable: () -> Unit = {},
    onUnavailable: (present: Boolean) -> Unit = {},
    onTag: (Tag) -> Unit,
) {
    val activity = LocalActivity.current
    LifecycleResumeEffect(activity, enabled) {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        val on = adapter?.isEnabled == true
        if (on) onAvailable() else onUnavailable(adapter != null)
        if (enabled && on) {
            adapter.enableReaderMode(activity, { onTag(it) }, READER_FLAGS, null)
        }
        onPauseOrDispose { if (adapter?.isEnabled == true) adapter.disableReaderMode(activity) }
    }
}

@Immutable
data class NfcWriteUi(
    val nickname: String,
    val state: NfcWriteState,
    val notices: NfcWriteNotices,
    val permissionRefused: Boolean,
    val keepOtherConfigs: Boolean = true,
)

@Immutable
data class NfcWriteActions(
    val onBack: () -> Unit,
    val onEnableLock: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
    val onEnableNfcKnock: () -> Unit,
    val onGrantLocalNetwork: () -> Unit,
    val onKeepOtherConfigsChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcWriteScaffold(content: NfcWriteUi, actions: NfcWriteActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_nfc_write)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        FwknopIcon(
                            R.drawable.ic_arrow_back_rounded,
                            stringResource(R.string.action_back)
                        )
                    }
                },
            )
        },
    ) { padding -> NfcWriteContent(content, actions, Modifier.padding(padding)) }
}

@Composable
fun NfcWriteContent(content: NfcWriteUi, actions: NfcWriteActions, modifier: Modifier = Modifier) {
    val dimens = DimensTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            dimens.sectionSpacing,
            Alignment.CenterVertically
        ),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            modifier = Modifier.fillMaxWidth(dimens.contentWidthFraction),
        ) {
            NfcWriteStatus(content.nickname, content.state, actions.onOpenNfcSettings)
            if (content.state is NfcWriteState.Waiting || content.state is NfcWriteState.Failed) {
                LabeledSwitch(
                    label = stringResource(R.string.field_nfc_keep_others),
                    checked = content.keepOtherConfigs,
                    onCheckedChange = actions.onKeepOtherConfigsChange,
                    supportingText = stringResource(R.string.field_nfc_keep_others_hint),
                )
                NfcWriteTips()
            }
            if (content.state !is NfcWriteState.Unsupported && content.state !is NfcWriteState.Disabled) {
                NfcWriteNoticeCards(content, actions)
            }
        }
    }
}

@Composable
private fun NfcWriteStatus(nickname: String, state: NfcWriteState, onOpenNfcSettings: () -> Unit) {
    val (title, body) = when (state) {
        NfcWriteState.Waiting ->
            stringResource(R.string.nfc_waiting_title) to stringResource(
                R.string.nfc_waiting_body,
                nickname
            )

        NfcWriteState.Writing ->
            stringResource(R.string.nfc_writing_title) to stringResource(R.string.nfc_writing_body)

        NfcWriteState.Written ->
            stringResource(R.string.nfc_written_title) to stringResource(
                R.string.nfc_written_body,
                nickname
            )

        NfcWriteState.Unsupported ->
            stringResource(R.string.nfc_unsupported_title) to stringResource(R.string.nfc_unsupported_body)

        NfcWriteState.Disabled ->
            stringResource(R.string.nfc_disabled_title) to stringResource(R.string.nfc_disabled_body)

        is NfcWriteState.Failed -> stringResource(R.string.nfc_failed_title) to state.error.text()
    }
    FwknopIcon(
        R.drawable.ic_nfc_rounded,
        null,
        Modifier.size(DimensTheme.dimens.heroIcon),
        tint = if (state is NfcWriteState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (state == NfcWriteState.Disabled) {
        FilledTonalButton(onClick = onOpenNfcSettings) { Text(stringResource(R.string.action_open_nfc_settings)) }
    }
}

@Composable
private fun NfcWriteTips() {
    SectionCard(stringResource(R.string.nfc_tips_title), R.drawable.ic_help_rounded) {
        Text(stringResource(R.string.nfc_tips_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NfcWriteNoticeCards(content: NfcWriteUi, actions: NfcWriteActions) {
    if (content.notices.lockDisabled) {
        SectionCard(
            stringResource(R.string.nfc_notice_lock_disabled_title),
            R.drawable.ic_lock_rounded
        ) {
            Text(
                stringResource(R.string.nfc_notice_lock_disabled_body),
                style = MaterialTheme.typography.bodyMedium
            )
            FilledTonalButton(onClick = actions.onEnableLock) {
                Text(stringResource(R.string.action_enable_lock))
            }
        }
    }
    if (content.notices.nfcKnockDisabled) {
        SectionCard(
            stringResource(R.string.nfc_notice_knock_disabled_title),
            R.drawable.ic_settings_rounded
        ) {
            Text(
                stringResource(R.string.nfc_notice_knock_disabled_body),
                style = MaterialTheme.typography.bodyMedium
            )
            FilledTonalButton(onClick = actions.onEnableNfcKnock) {
                Text(stringResource(R.string.action_enable_nfc_knock))
            }
        }
    }
    if (content.notices.localNetworkMissing) {
        SectionCard(
            stringResource(R.string.nfc_notice_local_network_title),
            R.drawable.ic_dns_rounded
        ) {
            Text(
                stringResource(R.string.nfc_notice_local_network_body),
                style = MaterialTheme.typography.bodyMedium
            )
            FilledTonalButton(onClick = actions.onGrantLocalNetwork) {
                Text(
                    stringResource(
                        if (content.permissionRefused) R.string.action_open_app_settings else R.string.action_allow
                    )
                )
            }
        }
    }
}

private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
private val PreviewActions = NfcWriteActions({}, {}, {}, {}, {}, {})

@AdaptivePreviews
@Composable
private fun NfcWriteContentPreview() {
    FwknopTheme {
        NfcWriteContent(
            NfcWriteUi(
                "home",
                NfcWriteState.Waiting,
                NfcWriteNotices(true, true),
                permissionRefused = false
            ),
            PreviewActions,
        )
    }
}

@AdaptivePreviews
@Composable
private fun NfcWriteDisabledPreview() {
    FwknopTheme {
        NfcWriteContent(
            NfcWriteUi("home", NfcWriteState.Disabled, NfcWriteNotices(), false),
            PreviewActions
        )
    }
}
