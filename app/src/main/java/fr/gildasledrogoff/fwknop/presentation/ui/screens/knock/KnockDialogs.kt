// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.knock

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.KnockPermission
import fr.gildasledrogoff.fwknop.presentation.state.KnockState
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FormTextField
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.SecretTextField
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun ChooseConfigDialog(
    nicknames: List<String>,
    onChoose: (String) -> Unit,
    onCancel: () -> Unit,
    onErase: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { FwknopIcon(R.drawable.ic_nfc_rounded, null) },
        title = { Text(stringResource(R.string.knock_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                nicknames.forEach { nickname ->
                    ListItem(
                        headlineContent = { Text(nickname) },
                        leadingContent = { FwknopIcon(R.drawable.ic_dns_rounded, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { onChoose(nickname) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.nfc_do_nothing)) } },
        dismissButton = onErase?.let { erase -> { EraseTagButton(erase) } },
    )
}

@Composable
private fun EraseTagButton(onErase: () -> Unit) {
    TextButton(
        onClick = onErase,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(R.string.nfc_erase)) }
}

@Composable
fun KeyPromptDialog(nickname: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { FwknopIcon(R.drawable.ic_key_rounded, null) },
        title = { Text(stringResource(R.string.knock_key_prompt_title, nickname)) },
        text = { SecretTextField(key, { key = it }, stringResource(R.string.field_rijndael_key)) },
        confirmButton = {
            TextButton(onClick = { onSubmit(key) }, enabled = key.isNotEmpty()) {
                Text(
                    stringResource(R.string.knock)
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Immutable
data class SourceIpPromptActions(
    val onSubmit: (String) -> Unit,
    val onScan: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
fun SourceIpPromptDialog(prompt: KnockState.PromptSourceIp, actions: SourceIpPromptActions) {
    var ip by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = actions.onCancel,
        icon = { FwknopIcon(R.drawable.ic_dns_rounded, null) },
        title = { Text(stringResource(R.string.knock_ip_prompt_title, prompt.nickname)) },
        text = { SourceIpPromptContent(ip, { ip = it }, prompt.sourceIpIssue()) },
        confirmButton = {
            TextButton(onClick = { actions.onSubmit(ip) }, enabled = ip.isNotBlank()) {
                Text(stringResource(R.string.knock))
            }
        },
        dismissButton = { SourceIpPromptButtons(actions) },
    )
}

@StringRes
private fun KnockState.PromptSourceIp.sourceIpIssue(): Int? = when {
    qrUnrecognized -> R.string.error_qr_no_source_ip
    invalid -> R.string.error_source_ip_invalid
    else -> null
}

@Composable
private fun SourceIpPromptContent(ip: String, onChange: (String) -> Unit, @StringRes issue: Int?) {
    FormTextField(
        value = ip,
        onValueChange = onChange,
        label = stringResource(R.string.field_source_ip),
        keyboardType = KeyboardType.Uri,
        supportingText = issue?.let { stringResource(it) },
    )
}

@Composable
private fun SourceIpPromptButtons(actions: SourceIpPromptActions) {

    TextButton(onClick = actions.onScan) { Text(stringResource(R.string.action_scan_qr)) }
    TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
fun SendingDialog(nickname: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { FwknopIcon(R.drawable.ic_bolt_rounded_fill, null) },
        title = { Text(stringResource(R.string.knock_sending_title, nickname)) },
        text = { SendingContent() },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PermissionRationaleDialog(
    prompt: KnockState.ExplainPermission,
    onContinue: () -> Unit,
    onDecline: () -> Unit,
) {
    val (icon, title, body) = when (prompt.permission) {
        KnockPermission.NOTIFICATIONS -> Triple(
            R.drawable.ic_shield_rounded,
            R.string.permission_notifications_title,
            R.string.permission_notifications_body,
        )

        KnockPermission.LOCAL_NETWORK -> Triple(
            R.drawable.ic_dns_rounded,
            R.string.permission_local_network_title,
            R.string.permission_local_network_body,
        )
    }
    val declineLabel =
        if (prompt.permission == KnockPermission.NOTIFICATIONS) R.string.action_later else R.string.cancel
    RationaleDialog(
        icon,
        title,
        stringResource(body, prompt.nickname),
        declineLabel,
        onContinue,
        onDecline
    )
}


@Composable
fun WakePermissionRationaleDialog(nickname: String, onContinue: () -> Unit, onDecline: () -> Unit) {
    RationaleDialog(
        R.drawable.ic_dns_rounded,
        R.string.permission_local_network_title,
        stringResource(R.string.permission_local_network_wake_body, nickname),
        R.string.cancel,
        onContinue,
        onDecline,
    )
}

@Composable
private fun RationaleDialog(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    body: String,
    @StringRes declineLabel: Int,
    onContinue: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { FwknopIcon(icon, null) },
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(declineLabel)) } },
    )
}

@Composable
private fun SendingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.knock_sending_body))
    }
}

@AdaptivePreviews
@Composable
private fun ChooseConfigDialogPreview() {
    FwknopTheme { ChooseConfigDialog(listOf("home", "office"), {}, {}, {}) }
}

@AdaptivePreviews
@Composable
private fun KeyPromptDialogPreview() {
    FwknopTheme { KeyPromptDialog("home", {}, {}) }
}

@AdaptivePreviews
@Composable
private fun SourceIpPromptDialogPreview() {
    FwknopTheme {
        SourceIpPromptDialog(
            KnockState.PromptSourceIp("home", qrUnrecognized = true),
            SourceIpPromptActions({}, {}, {})
        )
    }
}

@AdaptivePreviews
@Composable
private fun SendingDialogPreview() {
    FwknopTheme { SendingDialog("home") {} }
}

@AdaptivePreviews
@Composable
private fun PermissionRationaleDialogPreview() {
    FwknopTheme {
        PermissionRationaleDialog(
            KnockState.ExplainPermission(
                "home",
                KnockPermission.LOCAL_NETWORK
            ), {}, {})
    }
}

@AdaptivePreviews
@Composable
private fun WakePermissionRationaleDialogPreview() {
    FwknopTheme { WakePermissionRationaleDialog("home", {}, {}) }
}
