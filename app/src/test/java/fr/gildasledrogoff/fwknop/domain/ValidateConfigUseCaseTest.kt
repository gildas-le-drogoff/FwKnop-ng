package fr.gildasledrogoff.fwknop.domain
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.AccessDraft
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.ConfigDraft
import fr.gildasledrogoff.fwknop.domain.model.CryptoDraft
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppDraft
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppType
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.ServerDraft
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SourceIpDraft
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMode
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.toDraft
import fr.gildasledrogoff.fwknop.domain.usecase.ValidateConfigUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class ValidateConfigUseCaseTest {
    private val validate = ValidateConfigUseCase()
    private val valid = ConfigDraft(
        nickname = "home",
        server = ServerDraft(host = "knock.example.org"),
        access = AccessDraft(ports = "tcp/22", timeout = "30"),
        crypto = CryptoDraft(key = "secret"),
    )
    private fun errorOf(draft: ConfigDraft): ConfigError? = (validate(draft) as? DomainResult.Failure)?.error
    @Test
    fun `valid draft becomes a config`() {
        val config = (validate(valid) as DomainResult.Success).data
        assertThat(config.access).isEqualTo(AccessRule.OpenPort(PortAccess("tcp/22", 30, keepOpen = false)))
        assertThat(config.server.port).isEqualTo(ServerPort.Fixed(62201))
    }
    @Test
    fun `blank nickname is rejected`() {
        assertThat(errorOf(valid.copy(nickname = "  "))).isEqualTo(ConfigError.NicknameBlank)
    }
    @Test
    fun `server without dot or colon is rejected`() {
        assertThat(errorOf(valid.copy(server = ServerDraft(host = "localhost")))).isEqualTo(ConfigError.ServerInvalid)
    }
    @Test
    fun `invalid server port falls back to the fwknop default`() {
        val draft = valid.copy(server = valid.server.copy(port = "99999"))
        assertThat((validate(draft) as DomainResult.Success).data.server.port).isEqualTo(ServerPort.Fixed(62201))
    }
    @Test
    fun `ports must look like proto slash number`() {
        assertThat(errorOf(valid.copy(access = AccessDraft(ports = "22")))).isEqualTo(ConfigError.PortsFormat)
    }
    @Test
    fun `nat access requires a valid ip and port`() {
        val nat = AccessDraft(type = MessageType.NAT_ACCESS, ports = "tcp/22", natIp = "nope", natPort = "22")
        assertThat(errorOf(valid.copy(access = nat))).isEqualTo(ConfigError.NatIpInvalid)
        val badPort = nat.copy(natIp = "10.0.0.2", natPort = "0")
        assertThat(errorOf(valid.copy(access = badPort))).isEqualTo(ConfigError.NatPortInvalid)
    }
    @Test
    fun `hmac is refused in legacy mode`() {
        val crypto = valid.crypto.copy(hmac = "abc", legacy = true)
        assertThat(errorOf(valid.copy(crypto = crypto))).isEqualTo(ConfigError.HmacWithLegacy)
    }
    @Test
    fun `base64 key must have a length multiple of 4 and valid chars`() {
        assertThat(errorOf(valid.copy(crypto = CryptoDraft(key = "abc", keyBase64 = true))))
            .isEqualTo(ConfigError.KeyBase64Length)
        assertThat(errorOf(valid.copy(crypto = CryptoDraft(key = "ab*d", keyBase64 = true))))
            .isEqualTo(ConfigError.KeyBase64Chars)
    }
    @Test
    fun `gpg requires both keys`() {
        assertThat(errorOf(valid.copy(crypto = CryptoDraft(useGpg = true)))).isEqualTo(ConfigError.GpgKeysMissing)
    }
    @Test
    fun `allowed ip must be an address`() {
        val source = SourceIpDraft(SourceIpMode.ALLOW, "not-an-ip")
        assertThat(errorOf(valid.copy(sourceIp = source))).isEqualTo(ConfigError.AllowIpInvalid)
    }
    @Test
    fun `keep open is dropped without timeout`() {
        val draft = valid.copy(access = AccessDraft(ports = "tcp/22", timeout = "", keepOpen = true))
        val access = (validate(draft) as DomainResult.Success<SpaConfig>).data.access as AccessRule.OpenPort
        assertThat(access.access.keepOpen).isFalse()
    }
    @Test
    fun `config survives a draft round trip`() {
        val config = (validate(valid) as DomainResult.Success).data
        assertThat(validate(config.toDraft())).isEqualTo(DomainResult.Success(config))
    }
}
