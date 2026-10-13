package fr.gildasledrogoff.fwknop.data
import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class ConfigMapperTest {
    private val legacyRow = ConfigEntity(
        nickName = "router", accessIp = "Prompt IP", ports = "tcp/22", serverIp = "192.0.2.1",
        serverPort = "random", serverTimeout = "45", key = "", keyBase64 = false, hmac = "",
        natIp = "127.0.0.1", natPort = "2222", serverCmd = "", hmacBase64 = false,
        sshCmd = "ovpn:Home", legacy = false, protocol = "tcp",
        digestType = "SHA512", hmacType = "bogus", keepOpen = true, gpgSig = 0, gpgKey = 0, useGpg = false,
    )
    @Test
    fun `legacy sentinels map to typed values`() {
        val config = legacyRow.toDomain()
        assertThat(config.sourceIp).isEqualTo(SourceIp.Prompt)
        assertThat(config.server.port).isEqualTo(ServerPort.Random)
        assertThat(config.server.protocol).isEqualTo(Protocol.TCP)
        assertThat(config.access).isEqualTo(AccessRule.LocalNatAccess(PortAccess("tcp/22", 45, true), 2222))
        assertThat(config.crypto.hmac).isNull()
        assertThat(config.externalApp).isEqualTo(ExternalApp.OpenVpn("Home"))
    }
    @Test
    fun `unknown digest falls back to sha256`() {
        assertThat(legacyRow.toDomain().crypto.hmacDigest.name).isEqualTo("SHA256")
    }
    @Test
    fun `entity round trip is lossless`() {
        val configs = listOf(
            legacyRow.toDomain(),
            sampleConfig(),
            sampleConfig(access = AccessRule.NatAccess(PortAccess("udp/1194", null, false), "10.0.0.5", 1194)),
            sampleConfig(access = AccessRule.ServerCommand("uptime"), sourceIp = SourceIp.Source),
            sampleConfig(encryption = Encryption.Gpg(GpgKeyId(0x1234), GpgKeyId(0x5678))),
            sampleConfig(
                encryption = Encryption.Gpg(GpgKeyId(0x1234), GpgKeyId(0x5678), SecretKey("kept", base64 = false)),
            ),
            sampleConfig(
                encryption = Encryption.Rijndael(SecretKey("secret", base64 = false), GpgKeyId(0x1234), GpgKeyId(0x5678)),
            ),
        )
        configs.forEach { assertThat(it.toEntity().toDomain()).isEqualTo(it) }
    }
    @Test
    fun `openvpn and ssh commands keep their legacy prefixes`() {
        val vpn = sampleConfig().copy(externalApp = ExternalApp.OpenVpn("work"))
        assertThat(vpn.toEntity().sshCmd).isEqualTo("ovpn:work")
        val ssh = sampleConfig().copy(externalApp = ExternalApp.SshUri("me@host:22"))
        assertThat(ssh.toEntity().sshCmd).isEqualTo("me@host:22")
    }

    @Test
    fun `wireguard tunnel round trips through its prefix`() {
        val wg = sampleConfig().copy(externalApp = ExternalApp.WireGuard("home", "s3cret"))
        assertThat(wg.toEntity().sshCmd).isEqualTo("wg:home\ns3cret")
        assertThat(wg.toEntity().toDomain()).isEqualTo(wg)
    }

    @Test
    fun `wireguard entries saved without a key load with an empty one`() {
        val legacy = sampleConfig().toEntity().copy(sshCmd = "wg:home")
        assertThat(legacy.toDomain().externalApp).isEqualTo(ExternalApp.WireGuard("home", ""))
    }
}
