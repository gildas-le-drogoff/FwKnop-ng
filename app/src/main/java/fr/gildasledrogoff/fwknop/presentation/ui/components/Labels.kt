package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppType
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMode

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
