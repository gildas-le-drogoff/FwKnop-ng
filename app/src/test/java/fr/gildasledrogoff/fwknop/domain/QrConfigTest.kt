// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import org.junit.Test
import fr.gildasledrogoff.fwknop.domain.usecase.ParseQrConfigUseCase

class QrConfigTest {
    private val parse = ParseQrConfigUseCase()
    private fun roundTrip(original: SpaConfig): ConfigDraft =
        ConfigDraft().withQrConfig(parse(original.toQrContent()))
    @Test
    fun `default config round-trips through the qr stanzas`() {
        assertThat(roundTrip(sampleConfig())).isEqualTo(sampleConfig().toDraft())
    }
    @Test
    fun `nat access with options and non default digests round-trips`() {
        val crypto = SpaCrypto(
            encryption = Encryption.Rijndael(SecretKey("aGVsbG8gd29ybGQh", base64 = true)),
            hmac = SecretKey("aGFtYw==", base64 = true),
            digest = DigestAlgorithm.SHA512,
            hmacDigest = DigestAlgorithm.SHA384,
            legacyMode = false,
        )
        val original = sampleConfig(
            nickname = "router",
            access = AccessRule.NatAccess(PortAccess("tcp/22,udp/1194", 90, keepOpen = true), "10.0.0.5", 8080),
            encryption = crypto.encryption,
        ).copy(crypto = crypto)
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `values with spaces round-trip percent-encoded`() {
        val original = sampleConfig(
            nickname = "mon serveur",
            access = AccessRule.ServerCommand("systemctl restart sshd"),
        )
        val content = original.toQrContent()
        assertThat(content).contains("NICKNAME:mon%20serveur")
        assertThat(content).contains("CMD:systemctl%20restart%20sshd")
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `gpg config round-trips`() {
        val original = sampleConfig(
            encryption = Encryption.Gpg(GpgKeyId(0x123456789ABCDEFL), GpgKeyId(42L)),
        )
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `gpg key ids with the high bit set round-trip`() {
        val original = sampleConfig(
            encryption = Encryption.Gpg(GpgKeyId(-0x0DC5A1B2C3D4E5F6L), GpgKeyId(Long.MIN_VALUE)),
        )
        assertThat(original.toQrContent()).contains("GPG_RECIPIENT:F23A5E4D3C2B1A0A")
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `random port tcp protocol prompt source and ssh app round-trip`() {
        val original = sampleConfig().copy(
            server = ServerEndpoint("fw.example.org", ServerPort.Random, Protocol.TCP),
            sourceIp = SourceIp.Prompt,
            externalApp = ExternalApp.SshUri("root@10.0.0.5:2222"),
            crypto = sampleConfig().crypto.copy(legacyMode = true),
        )
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `http on a custom port round-trips`() {
        val original = sampleConfig().copy(server = ServerEndpoint("fw.example.org", ServerPort.Fixed(8080), Protocol.HTTP))
        assertThat(roundTrip(original)).isEqualTo(original.toDraft())
    }
    @Test
    fun `http without a port stanza goes to port 80 and never random`() {
        val qr = ParsedQr(QrKeys(null, null), QrConfig(protocol = Protocol.HTTP, randomPort = true))
        val server = ConfigDraft().withQrConfig(qr).server
        assertThat(server.port).isEqualTo("80")
        assertThat(server.randomPort).isFalse()
    }
    @Test
    fun `luci keys-only qr carries no config`() {
        val qr = parse("KEY_BASE64:a+G9/hZ= HMAC_KEY_BASE64:aGFt")
        assertThat(qr.config).isNull()
        assertThat(qr.keys).isEqualTo(QrKeys(SecretKey("a+G9/hZ=", true), SecretKey("aGFt", true)))
    }
    @Test
    fun `absent stanzas leave the draft fields alone`() {
        val draft = sampleConfig("home").toDraft()
        val qr = ParsedQr(QrKeys(null, null), QrConfig(serverHost = "elsewhere.example"))
        val merged = draft.withQrConfig(qr)
        assertThat(merged.server.host).isEqualTo("elsewhere.example")
        assertThat(merged.nickname).isEqualTo("home")
        assertThat(merged.access).isEqualTo(draft.access)
    }
    @Test
    fun `keys-only merge keeps the other crypto fields`() {
        val draft = sampleConfig("home").toDraft()
        val merged = draft.copy(crypto = draft.crypto.withQrKeys(parse("KEY:k").keys))
        assertThat(merged.crypto).isEqualTo(draft.crypto.copy(key = "k", keyBase64 = false))
    }
}
