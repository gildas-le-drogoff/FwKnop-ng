package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppDraft
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppType
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.DropdownOptions
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.OptionDropdown
import fr.gildasledrogoff.fwknop.presentation.ui.components.SecretTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.components.labelRes
import fr.gildasledrogoff.fwknop.presentation.ui.components.labelsOf
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun ExternalAppSection(app: ExternalAppDraft, onEvent: (ConfigEditEvent) -> Unit) {
    val onChange: (ExternalAppDraft) -> Unit = { onEvent(ConfigEditEvent.ExternalAppChanged(it)) }
    SectionCard(stringResource(R.string.section_external_app), R.drawable.ic_bolt_rounded) {
        val types = DropdownOptions(
            label = stringResource(R.string.field_external_app),
            options = ExternalAppType.entries,
            optionLabel = labelsOf(ExternalAppType.entries) { it.labelRes() },
        )
        OptionDropdown(types, app.type, { onChange(app.copy(type = it)) })
        AnimatedVisibility(visible = app.type == ExternalAppType.SSH_URI) {
            FormTextField(
                app.sshTarget,
                { onChange(app.copy(sshTarget = it)) },
                stringResource(R.string.field_ssh_uri)
            )
        }
        AnimatedVisibility(visible = app.type == ExternalAppType.OPENVPN) {
            FormTextField(
                app.vpnProfile,
                { onChange(app.copy(vpnProfile = it)) },
                stringResource(R.string.field_ovpn)
            )
        }
        AnimatedVisibility(visible = app.type == ExternalAppType.WIREGUARD) {
            Column {
                FormTextField(
                    app.wgTunnel,
                    { onChange(app.copy(wgTunnel = it)) },
                    stringResource(R.string.field_wg),
                )
                SecretTextField(
                    app.wgRemoteKey,
                    { onChange(app.copy(wgRemoteKey = it)) },
                    stringResource(R.string.field_wg_key),
                    supportingText = stringResource(R.string.field_wg_hint),
                )
            }
        }
    }
}

@AdaptivePreviews
@Composable
private fun ExternalAppSectionPreview() {
    FwknopTheme {
        val draft = ExternalAppDraft(ExternalAppType.SSH_URI, sshTarget = "me@host:22")
        ExternalAppSection(draft, {})
    }
}
