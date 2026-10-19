// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.ParseFwknopRcUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ValidateConfigUseCase
import org.junit.Test
class FwknopRcTest {
    private val parse = ParseFwknopRcUseCase(ValidateConfigUseCase())
    @Test
    fun `every stanza becomes a config, with the default stanza applied`() {
        val result = parse(
            """
            # client config
            [default]
            ALLOW_IP            resolve
            HMAC_DIGEST_TYPE    sha512

            [home]
            SPA_SERVER          home.example.org
            ACCESS              tcp/22
            KEY_BASE64          c2VjcmV0c2VjcmV0
            HMAC_KEY_BASE64     aG1hY2htYWM=
            FW_TIMEOUT          45

            [office]
            SPA_SERVER          203.0.113.7
            SPA_SERVER_PORT     40001
            SPA_SERVER_PROTO    tcp
            ACCESS              tcp/443
            ALLOW_IP            198.51.100.9
            KEY                 plainsecret
            NAT_ACCESS          192.168.1.20,22
            """.trimIndent()
        )
        assertThat(result.rejected).isEmpty()
        assertThat(result.configs.map { it.nickname.value }).containsExactly("home", "office").inOrder()
        val home = result.configs[0]
        assertThat(home.server).isEqualTo(ServerEndpoint("home.example.org", ServerPort.Fixed(62201), Protocol.UDP))
        assertThat(home.access).isEqualTo(AccessRule.OpenPort(PortAccess("tcp/22", 45, keepOpen = false)))
        assertThat(home.sourceIp).isEqualTo(SourceIp.Resolve)
        assertThat(home.crypto.encryption).isEqualTo(Encryption.Rijndael(SecretKey("c2VjcmV0c2VjcmV0", base64 = true)))
        assertThat(home.crypto.hmac).isEqualTo(SecretKey("aG1hY2htYWM=", base64 = true))
        assertThat(home.crypto.hmacDigest).isEqualTo(DigestAlgorithm.SHA512)
        val office = result.configs[1]
        assertThat(office.server).isEqualTo(ServerEndpoint("203.0.113.7", ServerPort.Fixed(40001), Protocol.TCP))
        assertThat(office.sourceIp).isEqualTo(SourceIp.Fixed("198.51.100.9"))
        assertThat(office.access).isEqualTo(
            AccessRule.NatAccess(PortAccess("tcp/443", null, keepOpen = false), "192.168.1.20", 22)
        )
    }
    @Test
    fun `invalid stanzas are reported, stanzas without a server are ignored`() {
        val result = parse(
            """
            [no-server]
            ACCESS              tcp/22
            [bad-ports]
            SPA_SERVER          knock.example.org
            ACCESS              22
            """.trimIndent()
        )
        assertThat(result.configs).isEmpty()
        assertThat(result.rejected).containsExactly("bad-ports")
    }
    @Test
    fun `an exported config reads back the same`() {
        val config = SpaConfig(
            nickname = Nickname("home"),
            server = ServerEndpoint("knock.example.org", ServerPort.Random, Protocol.UDP),
            access = AccessRule.ServerCommand("systemctl restart sshd"),
            sourceIp = SourceIp.Source,
            crypto = SpaCrypto(
                Encryption.Rijndael(SecretKey("c2VjcmV0c2VjcmV0", base64 = true)),
                SecretKey("aG1hY2htYWM=", base64 = true),
                DigestAlgorithm.SHA384,
                DigestAlgorithm.SHA256,
                legacyMode = false,
            ),
            externalApp = ExternalApp.None,
        )
        val read = parse(config.toDraft().toFwknopRc())
        assertThat(read.configs).containsExactly(config)
    }
    @Test
    fun `the server stanza opens the ports with the same keys`() {
        val draft = ConfigDraft(
            nickname = "home",
            access = AccessDraft(ports = "tcp/22", timeout = "30"),
            crypto = CryptoDraft(key = "a2V5", keyBase64 = true, hmac = "aG1hYw==", hmacBase64 = true),
        )
        val lines = draft.toAccessConf().lines().map { it.split(Regex("\\s+"), limit = 2) }
            .filter { it.size == 2 && !it[0].startsWith("#") }
            .associate { it[0] to it[1] }
        assertThat(lines).containsAtLeast(
            "SOURCE", "ANY",
            "OPEN_PORTS", "tcp/22",
            "FW_ACCESS_TIMEOUT", "30",
            "KEY_BASE64", "a2V5",
            "HMAC_KEY_BASE64", "aG1hYw==",
            "HMAC_DIGEST_TYPE", "sha256",
        )
    }
}
