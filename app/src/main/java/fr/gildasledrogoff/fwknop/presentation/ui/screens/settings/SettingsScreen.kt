// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.ThemeMode
import fr.gildasledrogoff.fwknop.domain.model.VpnRouting
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.screens.backup.BackupHost
import fr.gildasledrogoff.fwknop.presentation.ui.theme.Appearance
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.PreviewAppearance
import fr.gildasledrogoff.fwknop.presentation.ui.theme.dynamicColorSupported
import fr.gildasledrogoff.fwknop.presentation.viewmodel.BackupViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.SecuritySettingsViewModel
import fr.gildasledrogoff.fwknop.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val security by securityViewModel.state.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CollectEffects(viewModel.effects) { if (it == SettingsEffect.Saved) onBack() }
    // Leaving without saving disposes the preview, which restores the saved theme.
    if (!state.isLoading) PreviewAppearance(Appearance(state.form.themeMode, state.form.dynamicColor))
    SecurityHost(securityViewModel, snackbarHostState)
    BackupHost(backupViewModel, snackbarHostState) { viewModel.onEvent(SettingsEvent.Reload) }
    SettingsScaffold(state, snackbarHostState, onBack) {
        SettingsContent(state.form, security, viewModel::onEvent, securityViewModel::onEvent) {
            BackupSection(backup, backupViewModel::onEvent)
            AutoKnockHost()
        }
    }
    state.error?.let { ErrorDialog(it) { viewModel.onEvent(SettingsEvent.DismissError) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FwknopIcon(
                            R.drawable.ic_arrow_back_rounded,
                            stringResource(R.string.action_back)
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.TopCenter
        ) {
            if (!state.isLoading) content()
        }
    }
}

@Composable
fun SettingsContent(
    form: AppSettings,
    security: SecuritySettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onSecurityEvent: (SecuritySettingsEvent) -> Unit,
    backupSection: @Composable () -> Unit,
) {
    val dimens = DimensTheme.dimens
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = Modifier
            .fillMaxWidth(dimens.contentWidthFraction)
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
    ) {
        SettingsForm(form) { onEvent(SettingsEvent.Changed(it)) }
        AppearanceSection(form) { onEvent(SettingsEvent.Changed(it)) }
        SecuritySection(
            security = security,
            autoLock = AutoLockField(form.autoLockDelay) {
                onEvent(
                    SettingsEvent.Changed(
                        form.copy(
                            autoLockDelay = it
                        )
                    )
                )
            },
            onEvent = onSecurityEvent,
        )
        SettingsButtons(onEvent)
        backupSection()
    }
}

@Composable
private fun SettingsForm(form: AppSettings, onChange: (AppSettings) -> Unit) {
    SectionCard(stringResource(R.string.section_general), R.drawable.ic_settings_rounded) {
        SuggestionTextField(
            value = form.ipLookupUrl,
            onValueChange = { onChange(form.copy(ipLookupUrl = it)) },
            label = stringResource(R.string.field_ip_lookup_url),
            suggestions = AppSettings.IP_LOOKUP_PRESETS,
            keyboardType = KeyboardType.Uri,
        )
        LabeledSwitch(stringResource(R.string.field_stun_lookup), form.stunLookupEnabled, {
            onChange(form.copy(stunLookupEnabled = it))
        })
        LabeledSwitch(stringResource(R.string.field_dns_lookup), form.dnsLookupEnabled, {
            onChange(form.copy(dnsLookupEnabled = it))
        })
        VpnRoutingField(form.vpnRouting) { onChange(form.copy(vpnRouting = it)) }
        LabeledSwitch(stringResource(R.string.field_nfc_knock), form.nfcKnockEnabled, {
            onChange(form.copy(nfcKnockEnabled = it))
        })
        LabeledSwitch(stringResource(R.string.field_notification), form.notificationsEnabled, {
            onChange(form.copy(notificationsEnabled = it))
        })
        LabeledSwitch(stringResource(R.string.field_port_check), form.portCheckEnabled, {
            onChange(form.copy(portCheckEnabled = it))
        })
        ExternalAppDelayField(form.externalAppDelaySeconds) {
            onChange(form.copy(externalAppDelaySeconds = it))
        }
    }
}

@Composable
private fun VpnRoutingField(routing: VpnRouting, onChange: (VpnRouting) -> Unit) {
    Column {
        Text(stringResource(R.string.field_vpn_routing), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSelector(VpnRouting.entries, routing, onChange, { stringResource(it.labelRes()) })
        Text(
            stringResource(R.string.field_vpn_routing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun VpnRouting.labelRes(): Int = when (this) {
    VpnRouting.AUTO -> R.string.vpn_routing_auto
    VpnRouting.VIA_VPN -> R.string.vpn_routing_via_vpn
    VpnRouting.OUTSIDE_VPN -> R.string.vpn_routing_outside_vpn
}

@Composable
private fun ExternalAppDelayField(seconds: Int, onChange: (Int) -> Unit) {
    val range = AppSettings.EXTERNAL_APP_DELAY_RANGE
    val label = stringResource(R.string.field_external_app_delay)
    val value = pluralStringResource(R.plurals.external_app_delay_value, seconds, seconds)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.semantics { stateDescription = value },
        )
        Text(
            stringResource(R.string.field_external_app_delay_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSection(form: AppSettings, onChange: (AppSettings) -> Unit) {
    SectionCard(stringResource(R.string.section_appearance), R.drawable.ic_palette_rounded) {
        Text(stringResource(R.string.field_theme), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSelector(
            ThemeMode.entries,
            form.themeMode,
            { onChange(form.copy(themeMode = it)) },
            { stringResource(it.labelRes()) },
        )
        if (dynamicColorSupported) {
            LabeledSwitch(
                label = stringResource(R.string.field_dynamic_color),
                checked = form.dynamicColor,
                onCheckedChange = { onChange(form.copy(dynamicColor = it)) },
                supportingText = stringResource(R.string.field_dynamic_color_hint),
            )
        }
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun SettingsButtons(onEvent: (SettingsEvent) -> Unit) {
    ButtonRow {
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.ResetDefaults) },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(stringResource(R.string.action_defaults))
        }
        Button(
            onClick = { onEvent(SettingsEvent.Save) },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@AdaptivePreviews
@Composable
private fun SettingsContentPreview() {
    FwknopTheme {
        SettingsContent(AppSettings(), SecuritySettingsState(canEnableLock = true), {}, {}) {
            BackupSection(BackupState()) {}
        }
    }
}
