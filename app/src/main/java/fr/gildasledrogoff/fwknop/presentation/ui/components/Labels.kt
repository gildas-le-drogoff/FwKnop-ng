// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.*

@StringRes
fun ConfigKind.labelRes(): Int = when (this) {
    ConfigKind.FWKNOP -> R.string.kind_fwknop
    ConfigKind.PORT_KNOCK -> R.string.kind_port_knock
    ConfigKind.WAKE_ON_LAN -> R.string.kind_wake_on_lan
}

@StringRes
fun ConfigKind.hintRes(): Int = when (this) {
    ConfigKind.FWKNOP -> R.string.kind_fwknop_hint
    ConfigKind.PORT_KNOCK -> R.string.kind_port_knock_hint
    ConfigKind.WAKE_ON_LAN -> R.string.kind_wake_on_lan_hint
}

@DrawableRes
fun ConfigKind.iconRes(): Int = when (this) {
    ConfigKind.FWKNOP -> R.drawable.ic_shield_rounded
    ConfigKind.PORT_KNOCK -> R.drawable.ic_bolt_rounded
    ConfigKind.WAKE_ON_LAN -> R.drawable.ic_power_rounded
}

@StringRes
fun MessageType.labelRes(): Int = when (this) {
    MessageType.OPEN_PORT -> R.string.message_open_port
    MessageType.NAT_ACCESS -> R.string.message_nat_access
    MessageType.LOCAL_NAT_ACCESS -> R.string.message_local_nat_access
    MessageType.SERVER_COMMAND -> R.string.message_server_command
}

@StringRes
fun SourceIpMode.labelRes(): Int = when (this) {
    SourceIpMode.RESOLVE -> R.string.source_ip_resolve
    SourceIpMode.SOURCE -> R.string.source_ip_source
    SourceIpMode.ALLOW -> R.string.source_ip_allow
    SourceIpMode.PROMPT -> R.string.source_ip_prompt
}

@StringRes
fun SourceIpMode.hintRes(): Int? = when (this) {
    SourceIpMode.RESOLVE -> R.string.source_ip_resolve_hint
    SourceIpMode.SOURCE -> R.string.source_ip_source_hint
    SourceIpMode.ALLOW, SourceIpMode.PROMPT -> null
}

@Composable
fun ResolvedSourceIp.description(): String {
    val how = when (val method = method) {
        SourceIpMethod.Kernel -> stringResource(R.string.source_method_kernel)
        is SourceIpMethod.Stun -> stringResource(R.string.source_method_stun, method.server)
        is SourceIpMethod.Https -> stringResource(R.string.source_method_https, method.host)
        SourceIpMethod.Dns -> stringResource(R.string.source_method_dns)
        SourceIpMethod.Fixed -> stringResource(R.string.source_method_fixed)
        SourceIpMethod.Prompted -> stringResource(R.string.source_method_prompted)
        SourceIpMethod.PacketSource -> return stringResource(R.string.source_method_packet)
    }
    return stringResource(R.string.knock_source_line, address, how)
}

@StringRes
fun ExternalAppType.labelRes(): Int = when (this) {
    ExternalAppType.NONE -> R.string.app_none
    ExternalAppType.SSH_URI -> R.string.app_ssh_uri
    ExternalAppType.OPENVPN -> R.string.app_openvpn
    ExternalAppType.WIREGUARD -> R.string.app_wireguard
}

@Composable
fun <T> labelsOf(entries: List<T>, labelRes: (T) -> Int): (T) -> String {
    val labels = entries.associateWith { stringResource(labelRes(it)) }
    return { labels.getValue(it) }
}
