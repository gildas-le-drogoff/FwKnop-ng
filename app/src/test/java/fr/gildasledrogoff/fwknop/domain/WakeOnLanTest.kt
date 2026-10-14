package fr.gildasledrogoff.fwknop.domain

import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.backup.toDomain
import fr.gildasledrogoff.fwknop.data.backup.toPayload
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.AccessDraft
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.ConfigDraft
import fr.gildasledrogoff.fwknop.domain.model.CryptoDraft
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.MacAddress
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.ServerDraft
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLan
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLanDraft
import fr.gildasledrogoff.fwknop.domain.model.toDraft
import fr.gildasledrogoff.fwknop.domain.repository.WakeOnLanRepository
import fr.gildasledrogoff.fwknop.domain.usecase.GetConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendWakeOnLanUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ValidateConfigUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WakeOnLanTest {
    private val validate = ValidateConfigUseCase()
    private val draft = ConfigDraft(
        nickname = "home",
        server = ServerDraft(host = "knock.example.org"),
        access = AccessDraft(ports = "tcp/22"),
        crypto = CryptoDraft(key = "secret"),
    )
    private val mac = MacAddress.parse("aa:bb:cc:dd:ee:ff")!!

    private fun wakeOf(wake: WakeOnLanDraft) = validate(draft.copy(wakeOnLan = wake))

    @Test
    fun `mac address accepts colons, dashes or no separator`() {
        listOf("aa:bb:cc:dd:ee:ff", "AA-BB-CC-DD-EE-FF", "aabbccddeeff", " AA:bb:CC:dd:EE:ff ").forEach {
            assertThat(MacAddress.parse(it)?.value).isEqualTo("AA:BB:CC:DD:EE:FF")
        }
    }

    @Test
    fun `malformed mac addresses are rejected`() {
        listOf("", "aa:bb:cc:dd:ee", "aa:bb-cc:dd:ee:ff", "gg:bb:cc:dd:ee:ff", "aa:bb:cc:dd:ee:ff:00").forEach {
            assertThat(MacAddress.parse(it)).isNull()
        }
    }

    @Test
    fun `magic packet is 6 sync bytes then the address 16 times`() {
        val packet = mac.magicPacket()
        assertThat(packet.size).isEqualTo(102)
        assertThat(packet.take(6).all { it == 0xFF.toByte() }).isTrue()
        val address = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        packet.drop(6).chunked(6).forEach { assertThat(it.toByteArray()).isEqualTo(address) }
    }

    @Test
    fun `disabled draft leaves wake-on-lan off`() {
        val config = (wakeOf(WakeOnLanDraft(mac = "garbage")) as DomainResult.Success).data
        assertThat(config.wakeOnLan).isNull()
    }

    @Test
    fun `enabled draft defaults to port 9 and the server host`() {
        val config = (wakeOf(WakeOnLanDraft(enabled = true, mac = "aabbccddeeff", port = "")) as DomainResult.Success).data
        assertThat(config.wakeOnLan).isEqualTo(WakeOnLan(mac, host = null, port = 9))
        assertThat(config.wakeOnLan!!.targetHost(config.server)).isEqualTo("knock.example.org")
    }

    @Test
    fun `invalid fields are reported`() {
        val enabled = WakeOnLanDraft(enabled = true, mac = "aa:bb:cc:dd:ee:ff")
        assertThat((wakeOf(enabled.copy(mac = "nope")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakeMacInvalid)
        assertThat((wakeOf(enabled.copy(port = "70000")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakePortInvalid)
        assertThat((wakeOf(enabled.copy(host = "192.168.1 .255")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakeHostInvalid)
    }

    @Test
    fun `wake-on-lan survives draft, entity and backup round trips`() {
        val config = sampleConfig().copy(wakeOnLan = WakeOnLan(mac, "192.168.1.255", 7))
        assertThat((validate(config.toDraft()) as DomainResult.Success).data).isEqualTo(config)
        assertThat(config.toEntity().toDomain()).isEqualTo(config)
        assertThat(Backup(settings = null, configs = listOf(config)).toPayload().toDomain().configs).containsExactly(config)
        assertThat(sampleConfig().toEntity().wakeOnLan).isEmpty()
        assertThat(sampleConfig().toEntity().toDomain().wakeOnLan).isNull()
    }

    @Test
    fun `send targets the configured host, or the server host`() = runTest {
        val sent = mutableListOf<Triple<MacAddress, String, Int>>()
        val repository = object : WakeOnLanRepository {
            override suspend fun sendMagicPacket(mac: MacAddress, host: String, port: Int): DomainResult<Unit, KnockError> {
                sent += Triple(mac, host, port)
                return DomainResult.Success(Unit)
            }
        }
        val configs = FakeConfigRepository(
            listOf(
                sampleConfig("lan").copy(wakeOnLan = WakeOnLan(mac, "192.168.1.255", 9)),
                sampleConfig("wan").copy(wakeOnLan = WakeOnLan(mac, null, 4009)),
                sampleConfig("none"),
            )
        )
        val send = SendWakeOnLanUseCase(GetConfigUseCase(configs), repository)
        assertThat(send(Nickname("lan"))).isEqualTo(DomainResult.Success(Unit))
        assertThat(send(Nickname("wan"))).isEqualTo(DomainResult.Success(Unit))
        assertThat(send(Nickname("none"))).isInstanceOf(DomainResult.Failure::class.java)
        assertThat(send(Nickname("missing"))).isEqualTo(DomainResult.Failure(KnockError.ConfigMissing))
        assertThat(sent).containsExactly(
            Triple(mac, "192.168.1.255", 9),
            Triple(mac, "knock.example.org", 4009),
        ).inOrder()
    }
}
