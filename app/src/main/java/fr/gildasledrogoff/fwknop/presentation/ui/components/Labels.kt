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

@DrawableRes
fun ConfigIcon.glyphRes(filled: Boolean = true): Int? {
    val (outlined, fill) = when (this) {
        ConfigIcon.APP -> return null
        ConfigIcon.HOME -> R.drawable.ic_home_rounded to R.drawable.ic_home_rounded_fill
        ConfigIcon.COTTAGE -> R.drawable.ic_cottage_rounded to R.drawable.ic_cottage_rounded_fill
        ConfigIcon.BUILDING -> R.drawable.ic_apartment_rounded to R.drawable.ic_apartment_rounded_fill
        ConfigIcon.GARAGE -> R.drawable.ic_garage_home_rounded to R.drawable.ic_garage_home_rounded_fill
        ConfigIcon.OFFICE -> R.drawable.ic_work_rounded to R.drawable.ic_work_rounded_fill
        ConfigIcon.STORE -> R.drawable.ic_storefront_rounded to R.drawable.ic_storefront_rounded_fill
        ConfigIcon.FACTORY -> R.drawable.ic_factory_rounded to R.drawable.ic_factory_rounded_fill
        ConfigIcon.SCHOOL -> R.drawable.ic_school_rounded to R.drawable.ic_school_rounded_fill
        ConfigIcon.BOAT -> R.drawable.ic_sailing_rounded to R.drawable.ic_sailing_rounded_fill
        ConfigIcon.CAMPING -> R.drawable.ic_camping_rounded to R.drawable.ic_camping_rounded_fill
        ConfigIcon.VACATION -> R.drawable.ic_beach_access_rounded to R.drawable.ic_beach_access_rounded_fill
        ConfigIcon.WORKSHOP -> R.drawable.ic_construction_rounded to R.drawable.ic_construction_rounded_fill
        ConfigIcon.SERVER -> R.drawable.ic_dns_rounded to R.drawable.ic_dns_rounded_fill
        ConfigIcon.DATABASE -> R.drawable.ic_database_rounded to R.drawable.ic_database_rounded_fill
        ConfigIcon.STORAGE -> R.drawable.ic_hard_drive_rounded to R.drawable.ic_hard_drive_rounded_fill
        ConfigIcon.CLOUD -> R.drawable.ic_cloud_rounded to R.drawable.ic_cloud_rounded_fill
        ConfigIcon.WEB -> R.drawable.ic_public_rounded to R.drawable.ic_public_rounded_fill
        ConfigIcon.ROUTER -> R.drawable.ic_router_rounded to R.drawable.ic_router_rounded_fill
        ConfigIcon.NETWORK -> R.drawable.ic_lan_rounded to R.drawable.ic_lan_rounded_fill
        ConfigIcon.COMPUTER -> R.drawable.ic_computer_rounded to R.drawable.ic_computer_rounded_fill
        ConfigIcon.LAPTOP -> R.drawable.ic_laptop_rounded to R.drawable.ic_laptop_rounded_fill
        ConfigIcon.TERMINAL -> R.drawable.ic_terminal_rounded to R.drawable.ic_terminal_rounded_fill
        ConfigIcon.VPN -> R.drawable.ic_vpn_lock_rounded to R.drawable.ic_vpn_lock_rounded_fill
        ConfigIcon.MAIL -> R.drawable.ic_mail_rounded to R.drawable.ic_mail_rounded_fill
        ConfigIcon.BACKUP -> R.drawable.ic_backup_rounded to R.drawable.ic_backup_rounded_fill
        ConfigIcon.BOARD -> R.drawable.ic_developer_board_rounded to R.drawable.ic_developer_board_rounded_fill
        ConfigIcon.CAMERA -> R.drawable.ic_videocam_rounded to R.drawable.ic_videocam_rounded_fill
        ConfigIcon.SMART_HOME -> R.drawable.ic_home_iot_device_rounded to R.drawable.ic_home_iot_device_rounded_fill
        ConfigIcon.LIGHTING -> R.drawable.ic_lightbulb_rounded to R.drawable.ic_lightbulb_rounded_fill
        ConfigIcon.MEDIA -> R.drawable.ic_movie_rounded to R.drawable.ic_movie_rounded_fill
        ConfigIcon.GAMES -> R.drawable.ic_sports_esports_rounded to R.drawable.ic_sports_esports_rounded_fill
        ConfigIcon.PRINTER -> R.drawable.ic_print_rounded to R.drawable.ic_print_rounded_fill
        ConfigIcon.TV -> R.drawable.ic_tv_rounded to R.drawable.ic_tv_rounded_fill
        ConfigIcon.SPEAKER -> R.drawable.ic_speaker_rounded to R.drawable.ic_speaker_rounded_fill
        ConfigIcon.HEATING -> R.drawable.ic_thermostat_rounded to R.drawable.ic_thermostat_rounded_fill
        ConfigIcon.DOORBELL -> R.drawable.ic_doorbell_rounded to R.drawable.ic_doorbell_rounded_fill
    }
    return if (filled) fill else outlined
}

@StringRes
fun ConfigIcon.labelRes(): Int = when (this) {
    ConfigIcon.APP -> R.string.icon_app
    ConfigIcon.HOME -> R.string.icon_home
    ConfigIcon.COTTAGE -> R.string.icon_cottage
    ConfigIcon.BUILDING -> R.string.icon_building
    ConfigIcon.GARAGE -> R.string.icon_garage
    ConfigIcon.OFFICE -> R.string.icon_office
    ConfigIcon.STORE -> R.string.icon_store
    ConfigIcon.FACTORY -> R.string.icon_factory
    ConfigIcon.SCHOOL -> R.string.icon_school
    ConfigIcon.BOAT -> R.string.icon_boat
    ConfigIcon.CAMPING -> R.string.icon_camping
    ConfigIcon.VACATION -> R.string.icon_vacation
    ConfigIcon.WORKSHOP -> R.string.icon_workshop
    ConfigIcon.SERVER -> R.string.icon_server
    ConfigIcon.DATABASE -> R.string.icon_database
    ConfigIcon.STORAGE -> R.string.icon_storage
    ConfigIcon.CLOUD -> R.string.icon_cloud
    ConfigIcon.WEB -> R.string.icon_web
    ConfigIcon.ROUTER -> R.string.icon_router
    ConfigIcon.NETWORK -> R.string.icon_network
    ConfigIcon.COMPUTER -> R.string.icon_computer
    ConfigIcon.LAPTOP -> R.string.icon_laptop
    ConfigIcon.TERMINAL -> R.string.icon_terminal
    ConfigIcon.VPN -> R.string.icon_vpn
    ConfigIcon.MAIL -> R.string.icon_mail
    ConfigIcon.BACKUP -> R.string.icon_backup
    ConfigIcon.BOARD -> R.string.icon_board
    ConfigIcon.CAMERA -> R.string.icon_camera
    ConfigIcon.SMART_HOME -> R.string.icon_smart_home
    ConfigIcon.LIGHTING -> R.string.icon_lighting
    ConfigIcon.MEDIA -> R.string.icon_media
    ConfigIcon.GAMES -> R.string.icon_games
    ConfigIcon.PRINTER -> R.string.icon_printer
    ConfigIcon.TV -> R.string.icon_tv
    ConfigIcon.SPEAKER -> R.string.icon_speaker
    ConfigIcon.HEATING -> R.string.icon_heating
    ConfigIcon.DOORBELL -> R.string.icon_doorbell
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
