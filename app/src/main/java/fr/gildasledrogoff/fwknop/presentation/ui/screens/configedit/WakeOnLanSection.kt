// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLanDraft
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun WakeOnLanSection(wake: WakeOnLanDraft, onEvent: (ConfigEditEvent) -> Unit) {
    val onChange: (WakeOnLanDraft) -> Unit = { onEvent(ConfigEditEvent.WakeOnLanChanged(it)) }
    SectionCard(stringResource(R.string.section_wake_on_lan), R.drawable.ic_power_rounded) {
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
        FormTextField(
            wake.password,
            { onChange(wake.copy(password = it)) },
            stringResource(R.string.field_wake_password),
            keyboardType = KeyboardType.Ascii,
            supportingText = stringResource(R.string.field_wake_password_hint),
        )
    }
}

@AdaptivePreviews
@Composable
private fun WakeOnLanSectionPreview() {
    FwknopTheme { WakeOnLanSection(WakeOnLanDraft(mac = "AA:BB:CC:DD:EE:FF"), {}) }
}
