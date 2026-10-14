package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.presentation.state.AutoKnockEvent
import fr.gildasledrogoff.fwknop.presentation.state.AutoKnockState
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.DropdownOptions
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.LabeledSwitch
import fr.gildasledrogoff.fwknop.presentation.ui.components.LargeFontPreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.OptionDropdown
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.components.openAppSettings
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.AutoKnockViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccess
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccessStatus

@Immutable
data class LocationActions(
    val onGrant: () -> Unit,
    val onGrantBackground: () -> Unit,
    val onEnableService: () -> Unit,
)

@Composable
fun AutoKnockHost(viewModel: AutoKnockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val refresh = { viewModel.onEvent(AutoKnockEvent.PermissionsChanged) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused for good: only the system settings can still grant it.
        if (!granted) openAppSettings(context)
        refresh()
    }
    val actions = LocationActions(
        onGrant = { foreground.launch(LocationAccess.FOREGROUND) },
        onGrantBackground = { LocationAccess.BACKGROUND?.let(background::launch) },
        onEnableService = {
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                openAppSettings(context)
            }
        },
    )
    AutoKnockSection(state, viewModel::onEvent, actions)
    if (state.confirmingEnable) {
        AutoKnockWarningDialog(
            onConfirm = { viewModel.onEvent(AutoKnockEvent.EnableConfirmed) },
            onDismiss = { viewModel.onEvent(AutoKnockEvent.EnableDismissed) },
        )
    }
}

@Composable
fun AutoKnockSection(state: AutoKnockState, onEvent: (AutoKnockEvent) -> Unit, location: LocationActions) {
    SectionCard(stringResource(R.string.section_auto_knock), R.drawable.ic_wifi_rounded) {
        LabeledSwitch(
            label = stringResource(R.string.field_auto_knock),
            checked = state.settings.enabled,
            onCheckedChange = { onEvent(AutoKnockEvent.EnableRequested(it)) },
            supportingText = stringResource(R.string.field_auto_knock_hint),
            enabled = !state.isLoading,
        )
        if (state.settings.enabled) {
            ConfigChoice(state, onEvent)
            TrustedNetworks(state, onEvent)
            LocationStatus(state.location, location)
            Text(
                stringResource(R.string.auto_knock_behaviour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigChoice(state: AutoKnockState, onEvent: (AutoKnockEvent) -> Unit) {
    if (state.candidates.isEmpty()) {
        Notice(stringResource(R.string.auto_knock_no_candidate))
        return
    }
    val choose = stringResource(R.string.auto_knock_choose_config)
    OptionDropdown(
        spec = DropdownOptions(
            label = stringResource(R.string.field_auto_knock_config),
            options = state.candidates,
            optionLabel = { it.ifEmpty { choose } },
        ),
        selected = state.settings.nickname.orEmpty(),
        onSelected = { onEvent(AutoKnockEvent.ConfigChosen(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.configUnavailable) {
        Notice(stringResource(R.string.auto_knock_config_unavailable, state.settings.nickname.orEmpty()))
    }
}

@Composable
private fun TrustedNetworks(state: AutoKnockState, onEvent: (AutoKnockEvent) -> Unit) {
    Text(
        stringResource(R.string.auto_knock_trusted_networks),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    if (state.settings.trustedSsids.isEmpty()) {
        Text(
            stringResource(R.string.auto_knock_no_network),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.settings.trustedSsids.forEach { ssid ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            FwknopIcon(R.drawable.ic_wifi_rounded, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                ssid,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEvent(AutoKnockEvent.SsidRemoved(ssid)) }) {
                FwknopIcon(R.drawable.ic_delete_rounded, stringResource(R.string.a11y_remove_network, ssid))
            }
        }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(R.string.field_ssid),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                onEvent(AutoKnockEvent.SsidAdded(draft))
                draft = ""
            },
            enabled = draft.isNotBlank(),
        ) {
            FwknopIcon(R.drawable.ic_add_rounded, stringResource(R.string.a11y_add_network))
        }
    }
    OutlinedButton(
        onClick = { onEvent(AutoKnockEvent.AddCurrentNetwork) },
        shape = MaterialTheme.shapes.small,
        enabled = state.location != LocationAccessStatus.PERMISSION_MISSING,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.action_add_current_network))
    }
    if (state.currentSsidUnavailable) Notice(stringResource(R.string.auto_knock_current_unavailable))
}

@Composable
private fun LocationStatus(status: LocationAccessStatus, actions: LocationActions) {
    val (message, action, onClick) = when (status) {
        LocationAccessStatus.GRANTED -> return
        LocationAccessStatus.PERMISSION_MISSING ->
            Triple(R.string.auto_knock_location_missing, R.string.action_allow_location, actions.onGrant)
        LocationAccessStatus.BACKGROUND_MISSING ->
            Triple(R.string.auto_knock_background_missing, R.string.action_allow_background_location, actions.onGrantBackground)
        LocationAccessStatus.SERVICE_OFF ->
            Triple(R.string.auto_knock_location_off, R.string.action_enable_location, actions.onEnableService)
    }
    Notice(stringResource(message))
    OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(action))
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** Enabling is only possible after ticking that the consequences are understood. */
@Composable
fun AutoKnockWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FwknopIcon(R.drawable.ic_error_rounded, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.auto_knock_warning_title)) },
        text = {
            // Scrolls with the largest font sizes.
            Column(
                verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.auto_knock_warning_body))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text(stringResource(R.string.auto_knock_warning_ack), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = understood) {
                Text(stringResource(R.string.auto_knock_warning_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val previewState = AutoKnockState(
    isLoading = false,
    settings = AutoKnockSettings(enabled = true, nickname = "home", trustedSsids = setOf("Maison", "Bureau-5G")),
    candidates = listOf("home", "office-nat"),
    location = LocationAccessStatus.BACKGROUND_MISSING,
)

@AdaptivePreviews
@Composable
private fun AutoKnockSectionPreview() {
    FwknopTheme { AutoKnockSection(previewState, {}, LocationActions({}, {}, {})) }
}

@LargeFontPreviews
@Composable
private fun AutoKnockSectionLargeFontPreview() {
    FwknopTheme { AutoKnockSection(previewState, {}, LocationActions({}, {}, {})) }
}

@LargeFontPreviews
@Composable
private fun AutoKnockWarningDialogPreview() {
    FwknopTheme { AutoKnockWarningDialog({}, {}) }
}
