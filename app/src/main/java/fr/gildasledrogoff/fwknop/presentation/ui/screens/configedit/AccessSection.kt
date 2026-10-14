// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.AccessDraft
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun AccessSection(
    access: AccessDraft,
    gpgEnabled: Boolean,
    portKnocking: Boolean,
    onChange: (AccessDraft) -> Unit,
) {
    SectionCard(stringResource(R.string.section_access), R.drawable.ic_lock_rounded) {

        if (portKnocking) {
            PortFields(access, gpgEnabled, portKnocking = true, onChange)
        } else {
            MessageFields(access, gpgEnabled, onChange)
        }
    }
}

@Composable
private fun MessageFields(
    access: AccessDraft,
    gpgEnabled: Boolean,
    onChange: (AccessDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        val types = DropdownOptions(
            label = stringResource(R.string.field_message_type),
            options = MessageType.entries,
            optionLabel = labelsOf(MessageType.entries) { it.labelRes() },
        )
        OptionDropdown(types, access.type, { onChange(access.copy(type = it)) })
        val usesPorts = access.type != MessageType.SERVER_COMMAND
        AnimatedVisibility(visible = usesPorts) {
            PortFields(
                access,
                gpgEnabled,
                portKnocking = false,
                onChange
            )
        }
        val usesNat =
            access.type == MessageType.NAT_ACCESS || access.type == MessageType.LOCAL_NAT_ACCESS
        AnimatedVisibility(visible = usesNat) {
            NatFields(access, onChange)
        }
        AnimatedVisibility(visible = !usesPorts) {
            val label = stringResource(R.string.field_command)
            FormTextField(access.command, { onChange(access.copy(command = it)) }, label)
        }
    }
}

@Composable
private fun PortFields(
    access: AccessDraft,
    gpgEnabled: Boolean,
    portKnocking: Boolean,
    onChange: (AccessDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        FormTextField(
            value = access.ports,
            onValueChange = { onChange(access.copy(ports = it)) },
            label = stringResource(if (portKnocking) R.string.field_knock_access_ports else R.string.field_access_ports),
            supportingText = stringResource(if (portKnocking) R.string.hint_knock_access_ports else R.string.hint_access_ports),
        )
        FormTextField(
            value = access.timeout,
            onValueChange = { onChange(access.copy(timeout = it)) },
            label = stringResource(R.string.field_timeout),
            keyboardType = KeyboardType.Number,
        )
        AnimatedVisibility(visible = !gpgEnabled) {
            LabeledSwitch(
                label = stringResource(R.string.field_keep_open),
                checked = access.keepOpen,
                onCheckedChange = { onChange(access.copy(keepOpen = it)) },
                supportingText = stringResource(R.string.hint_keep_open),
            )
        }
    }
}

@Composable
private fun NatFields(access: AccessDraft, onChange: (AccessDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        AnimatedVisibility(visible = access.type == MessageType.NAT_ACCESS) {
            FormTextField(
                value = access.natIp,
                onValueChange = { onChange(access.copy(natIp = it)) },
                label = stringResource(R.string.field_internal_ip),
                keyboardType = KeyboardType.Uri,
            )
        }
        FormTextField(
            value = access.natPort,
            onValueChange = { onChange(access.copy(natPort = it)) },
            label = stringResource(R.string.field_internal_port),
            keyboardType = KeyboardType.Number,
        )
    }
}

@AdaptivePreviews
@Composable
private fun AccessSectionPreview() {
    FwknopTheme {
        AccessSection(
            AccessDraft(type = MessageType.NAT_ACCESS, timeout = "30"),
            gpgEnabled = false,
            portKnocking = false,
            {})
    }
}
