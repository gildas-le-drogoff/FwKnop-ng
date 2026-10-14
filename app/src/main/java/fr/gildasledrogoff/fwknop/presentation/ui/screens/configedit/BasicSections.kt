// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun GeneralSection(nickname: String, onChange: (String) -> Unit) {
    SectionCard(stringResource(R.string.section_general), R.drawable.ic_shield_rounded) {
        FormTextField(nickname, onChange, stringResource(R.string.field_nickname))
    }
}

@Composable
fun ServerSection(server: ServerDraft, portKnocking: Boolean, onChange: (ServerDraft) -> Unit) {
    SectionCard(stringResource(R.string.section_server), R.drawable.ic_dns_rounded) {
        FormTextField(
            value = server.host,
            onValueChange = { onChange(server.copy(host = it)) },
            label = stringResource(R.string.field_server_address),
            keyboardType = KeyboardType.Uri,
        )
        if (portKnocking) KnockSequenceFields(server, onChange) else SpaEndpointFields(
            server,
            onChange
        )
    }
}

@Composable
private fun KnockSequenceFields(server: ServerDraft, onChange: (ServerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        FormTextField(
            value = server.knockSequence,
            onValueChange = { onChange(server.copy(knockSequence = it)) },
            label = stringResource(R.string.field_knock_sequence),
            supportingText = stringResource(R.string.hint_knock_sequence),
        )
        FormTextField(
            value = server.knockDelayMs,
            onValueChange = { onChange(server.copy(knockDelayMs = it)) },
            label = stringResource(R.string.field_knock_delay),
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
private fun SpaEndpointFields(server: ServerDraft, onChange: (ServerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {

        AnimatedVisibility(visible = server.protocol != Protocol.HTTP) {
            LabeledSwitch(
                label = stringResource(R.string.field_random_port),
                checked = server.randomPort,
                onCheckedChange = { onChange(server.copy(randomPort = it)) },
            )
        }
        AnimatedVisibility(visible = !server.randomPort) {
            FormTextField(
                value = server.port,
                onValueChange = { onChange(server.copy(port = it)) },
                label = stringResource(R.string.field_server_port),
                keyboardType = KeyboardType.Number,
            )
        }
        val protocols = DropdownOptions(
            stringResource(R.string.field_protocol),
            Protocol.entries,
            Protocol::name
        )
        OptionDropdown(protocols, server.protocol, { onChange(server.withProtocol(it)) })
    }
}

@Composable
fun SourceIpSection(sourceIp: SourceIpDraft, onChange: (SourceIpDraft) -> Unit) {
    SectionCard(stringResource(R.string.section_source_ip), R.drawable.ic_dns_rounded) {
        val modes = DropdownOptions(
            label = stringResource(R.string.field_allow_ip),
            options = SourceIpMode.entries,
            optionLabel = labelsOf(SourceIpMode.entries) { it.labelRes() },
        )
        OptionDropdown(modes, sourceIp.mode, { onChange(sourceIp.copy(mode = it)) })
        sourceIp.mode.hintRes()?.let { SourceIpHint(it) }
        AnimatedVisibility(visible = sourceIp.mode == SourceIpMode.ALLOW) {
            FormTextField(
                value = sourceIp.address,
                onValueChange = { onChange(sourceIp.copy(address = it)) },
                label = stringResource(R.string.field_ip_to_allow),
                keyboardType = KeyboardType.Uri,
            )
        }
    }
}

@Composable
private fun SourceIpHint(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@AdaptivePreviews
@Composable
private fun ServerSectionPreview() {
    FwknopTheme { ServerSection(ServerDraft(host = "knock.example.org"), portKnocking = false, {}) }
}

@AdaptivePreviews
@Composable
private fun PortKnockingSectionPreview() {
    FwknopTheme {
        ServerSection(
            ServerDraft(host = "knock.example.org", knockSequence = "tcp/7000,udp/8000"),
            portKnocking = true,
            {},
        )
    }
}

@AdaptivePreviews
@Composable
private fun SourceIpSectionPreview() {
    FwknopTheme { SourceIpSection(SourceIpDraft(SourceIpMode.ALLOW, "198.51.100.4"), {}) }
}

@AdaptivePreviews
@Composable
private fun SourceModeSectionPreview() {
    FwknopTheme { SourceIpSection(SourceIpDraft(SourceIpMode.SOURCE), {}) }
}

@AdaptivePreviews
@Composable
private fun GeneralSectionPreview() {
    FwknopTheme { GeneralSection("home", {}) }
}
