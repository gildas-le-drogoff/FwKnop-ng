package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import fr.gildasledrogoff.fwknop.presentation.state.BackupState
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.SecuritySettingsState
import fr.gildasledrogoff.fwknop.presentation.state.SettingsEffect
import fr.gildasledrogoff.fwknop.presentation.state.SettingsEvent
import fr.gildasledrogoff.fwknop.presentation.state.SettingsState
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.CollectEffects
import fr.gildasledrogoff.fwknop.presentation.ui.components.ErrorDialog
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.LabeledSwitch
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.components.SuggestionTextField
import fr.gildasledrogoff.fwknop.presentation.ui.screens.backup.BackupHost
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
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
        Box(Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.TopCenter) {
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
private fun ExternalAppDelayField(seconds: Int, onChange: (Int) -> Unit) {
    val range = AppSettings.EXTERNAL_APP_DELAY_RANGE
    val label = stringResource(R.string.field_external_app_delay)
    val value = pluralStringResource(R.plurals.external_app_delay_value, seconds, seconds)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = form.themeMode == mode,
                    onClick = { onChange(form.copy(themeMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) {
                    Text(stringResource(mode.labelRes()))
                }
            }
        }
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
    Row(horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.ResetDefaults) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.action_defaults))
        }
        Button(
            onClick = { onEvent(SettingsEvent.Save) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f),
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
