package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLanDraft
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.LabeledSwitch
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun WakeOnLanSection(wake: WakeOnLanDraft, onEvent: (ConfigEditEvent) -> Unit) {
    val onChange: (WakeOnLanDraft) -> Unit = { onEvent(ConfigEditEvent.WakeOnLanChanged(it)) }
    SectionCard(stringResource(R.string.section_wake_on_lan), R.drawable.ic_power_rounded) {
        LabeledSwitch(
            stringResource(R.string.field_wake_enabled),
            checked = wake.enabled,
            onCheckedChange = { onChange(wake.copy(enabled = it)) },
            supportingText = stringResource(R.string.field_wake_enabled_hint),
        )
        AnimatedVisibility(visible = wake.enabled) {
            Column {
                FormTextField(
                    wake.mac,
                    { onChange(wake.copy(mac = it)) },
                    stringResource(R.string.field_wake_mac),
                    keyboardType = KeyboardType.Ascii,
                )
                FormTextField(
                    wake.host,
                    { onChange(wake.copy(host = it)) },
                    stringResource(R.string.field_wake_host),
                    keyboardType = KeyboardType.Uri,
                    supportingText = stringResource(R.string.field_wake_host_hint),
                )
                FormTextField(
                    wake.port,
                    { onChange(wake.copy(port = it)) },
                    stringResource(R.string.field_wake_port),
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}

@AdaptivePreviews
@Composable
private fun WakeOnLanSectionPreview() {
    FwknopTheme { WakeOnLanSection(WakeOnLanDraft(enabled = true, mac = "AA:BB:CC:DD:EE:FF"), {}) }
}
