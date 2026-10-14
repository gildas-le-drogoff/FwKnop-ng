// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import fr.gildasledrogoff.fwknop.data.local.models.ConfigColumns
import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity

class ConfigEntityCipher(private val columns: ColumnCipher) {
    fun seal(entity: ConfigEntity): ConfigEntity = entity.mapSensitive(columns::encrypt)
    fun open(entity: ConfigEntity): ConfigEntity = entity.mapSensitive(columns::decrypt)

    companion object {
        val SENSITIVE: List<String> = listOf(
            ConfigColumns.ACCESS_IP,
            ConfigColumns.PORTS,
            ConfigColumns.SERVER_IP,
            ConfigColumns.SERVER_PORT,
            ConfigColumns.SERVER_TIMEOUT,
            ConfigColumns.KEY,
            ConfigColumns.HMAC,
            ConfigColumns.NAT_IP,
            ConfigColumns.NAT_PORT,
            ConfigColumns.SERVER_CMD,
            ConfigColumns.SSH_CMD,
            ConfigColumns.KNOCK_SEQUENCE,
            ConfigColumns.WAKE_ON_LAN,
        )
    }
}

private fun ConfigEntity.mapSensitive(transform: (column: String, value: String) -> String) = copy(
    accessIp = transform(ConfigColumns.ACCESS_IP, accessIp),
    ports = transform(ConfigColumns.PORTS, ports),
    serverIp = transform(ConfigColumns.SERVER_IP, serverIp),
    serverPort = transform(ConfigColumns.SERVER_PORT, serverPort),
    serverTimeout = transform(ConfigColumns.SERVER_TIMEOUT, serverTimeout),
    key = transform(ConfigColumns.KEY, key),
    hmac = transform(ConfigColumns.HMAC, hmac),
    natIp = transform(ConfigColumns.NAT_IP, natIp),
    natPort = transform(ConfigColumns.NAT_PORT, natPort),
    serverCmd = transform(ConfigColumns.SERVER_CMD, serverCmd),
    sshCmd = transform(ConfigColumns.SSH_CMD, sshCmd),

    knockSequence = knockSequence.takeIf { it.isNotEmpty() }
        ?.let { transform(ConfigColumns.KNOCK_SEQUENCE, it) }.orEmpty(),

    wakeOnLan = wakeOnLan.takeIf { it.isNotEmpty() }
        ?.let { transform(ConfigColumns.WAKE_ON_LAN, it) }.orEmpty(),
)
