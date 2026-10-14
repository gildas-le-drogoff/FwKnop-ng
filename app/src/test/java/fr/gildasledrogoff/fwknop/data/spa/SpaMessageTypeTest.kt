// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.spa
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaMessageType
import fr.gildasledrogoff.fwknop.data.datasource.spa.androidUsername
import fr.gildasledrogoff.fwknop.data.datasource.spa.messageType
import fr.gildasledrogoff.fwknop.domain.model.NatTarget
import org.junit.Test
import fr.gildasledrogoff.fwknop.domain.model.DigestAlgorithm
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams

class SpaMessageTypeTest {
    private fun params(timeout: Int?, nat: NatTarget? = null, command: String? = null) = SpaPacketParams(
        accessPorts = "tcp/22",
        sourceIp = "198.51.100.4",
        key = SecretKey("k", base64 = false),
        hmac = null,
        timeoutSeconds = timeout,
        nat = nat,
        serverCommand = command,
        legacyMode = false,
        digest = DigestAlgorithm.SHA256,
        hmacDigest = DigestAlgorithm.SHA256,
    )
    private val nat = NatTarget("192.168.1.20", 22, local = false)
    private val localNat = NatTarget("192.0.2.10", 22, local = true)
    @Test
    fun `message type follows libfko rules`() {
        assertThat(params(30, command = "198.51.100.4,ls").messageType()).isEqualTo(SpaMessageType.COMMAND)
        assertThat(params(null).messageType()).isEqualTo(SpaMessageType.ACCESS)
        assertThat(params(30).messageType()).isEqualTo(SpaMessageType.CLIENT_TIMEOUT_ACCESS)
        assertThat(params(0, nat).messageType()).isEqualTo(SpaMessageType.NAT_ACCESS)
        assertThat(params(30, nat).messageType()).isEqualTo(SpaMessageType.CLIENT_TIMEOUT_NAT_ACCESS)
        assertThat(params(null, localNat).messageType()).isEqualTo(SpaMessageType.LOCAL_NAT_ACCESS)
        assertThat(params(30, localNat).messageType()).isEqualTo(SpaMessageType.CLIENT_TIMEOUT_LOCAL_NAT_ACCESS)
    }
    @Test
    fun `empty server command is an access message`() {
        assertThat(params(null, command = "").messageType()).isEqualTo(SpaMessageType.ACCESS)
    }
    @Test
    fun `android username mirrors bionic getlogin`() {
        assertThat(androidUsername(10_123)).isEqualTo("u0_a123")
        assertThat(androidUsername(1_010_045)).isEqualTo("u10_a45")
        assertThat(androidUsername(1_000)).isEqualTo("NO_USER")
    }
}
