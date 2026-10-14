// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.ConfigEntityCipher
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import org.junit.Test
    private val entity = sampleConfig().toEntity().copy(id = 7, natIp = "10.0.0.2", serverCmd = "systemctl start x")
    @Test
    fun `seal then open gives the row back`() {
        assertThat(cipher.open(cipher.seal(entity))).isEqualTo(entity)
    }
    @Test
    fun `keys and infrastructure are sealed, the lookup key and flags stay clear`() {
        val sealed = cipher.seal(entity)
        val clear = listOf(entity.key, entity.hmac, entity.serverIp, entity.serverPort, entity.ports, entity.accessIp,
            entity.natIp, entity.serverCmd)
        val stored = listOf(sealed.key, sealed.hmac, sealed.serverIp, sealed.serverPort, sealed.ports, sealed.accessIp,
            sealed.natIp, sealed.serverCmd)
        stored.zip(clear).forEach { (s, c) -> assertThat(s).isNotEqualTo(c) }
        assertThat(stored.joinToString()).doesNotContain("secret")
        assertThat(stored.joinToString()).doesNotContain("knock.example.org")
        assertThat(sealed.copy(
            accessIp = entity.accessIp, ports = entity.ports, serverIp = entity.serverIp,
            serverPort = entity.serverPort, serverTimeout = entity.serverTimeout, key = entity.key, hmac = entity.hmac,
            natIp = entity.natIp, natPort = entity.natPort, serverCmd = entity.serverCmd, sshCmd = entity.sshCmd,
        )).isEqualTo(entity)
    }
    @Test
    fun `every column declared sensitive is actually sealed`() {
        assertThat(ConfigEntityCipher.SENSITIVE).hasSize(13)
        assertThat(ConfigEntityCipher.SENSITIVE)
            .containsAtLeast("KEY", "HMAC", "SERVER_IP", "ACCESS_IP", "NAT_IP", "KNOCK_SEQUENCE")
        assertThat(ConfigEntityCipher.SENSITIVE).doesNotContain("NICK_NAME")
    }
    @Test
    fun `knock sequence is sealed, and an empty one stays readable after the v4 migration`() {
        val knocking = entity.copy(knockSequence = "tcp/7000,udp/8000")
        val sealed = cipher.seal(knocking)
        assertThat(sealed.knockSequence).isNotEqualTo(knocking.knockSequence)
        assertThat(cipher.open(sealed)).isEqualTo(knocking)
        val spa = cipher.seal(entity.copy(knockSequence = ""))
        assertThat(spa.knockSequence).isEmpty()
        assertThat(cipher.open(spa).knockSequence).isEmpty()
    }
    @Test
    fun `wake-on-lan is sealed, and an empty one stays readable after the v5 migration`() {
        val waking = entity.copy(wakeOnLan = "AA:BB:CC:DD:EE:FF\n9\n192.168.1.255")
        val sealed = cipher.seal(waking)
        assertThat(sealed.wakeOnLan).doesNotContain("AA:BB")
        assertThat(cipher.open(sealed)).isEqualTo(waking)
        val off = cipher.seal(entity.copy(wakeOnLan = ""))
        assertThat(off.wakeOnLan).isEmpty()
        assertThat(cipher.open(off).wakeOnLan).isEmpty()
    }
}
