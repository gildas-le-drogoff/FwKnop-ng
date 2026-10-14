// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.backup.toDomain
import fr.gildasledrogoff.fwknop.data.backup.toPayload
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.data.mapper.toWakeOnLan
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.WakeOnLanRepository
import fr.gildasledrogoff.fwknop.domain.usecase.GetConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendWakeOnLanUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.FakeKnockHistoryRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import fr.gildasledrogoff.fwknop.domain.usecase.ValidateConfigUseCase

class WakeOnLanTest {
    private val validate = ValidateConfigUseCase()
    private val spaDraft = ConfigDraft(
        nickname = "home",
        server = ServerDraft(host = "knock.example.org"),
        access = AccessDraft(ports = "tcp/22"),
        crypto = CryptoDraft(key = "secret"),
    )
    private val draft = ConfigDraft(kind = ConfigKind.WAKE_ON_LAN, nickname = "nas")
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
    fun `secureon password accepts the ether-wake hex and dotted forms`() {
        mapOf(
            "00:22:44:66:88:aa" to "00:22:44:66:88:AA",
            "00-22-44-66-88-AA" to "00:22:44:66:88:AA",
            "aabbccdd" to "AA:BB:CC:DD",
            " 192.168.001.1 " to "192.168.1.1",
        ).forEach { (text, value) -> assertThat(WakePassword.parse(text)?.value).isEqualTo(value) }
        assertThat(WakePassword.parse("00:22:44:66:88:aa")!!.bytes())
            .isEqualTo(byteArrayOf(0x00, 0x22, 0x44, 0x66, 0x88.toByte(), 0xAA.toByte()))
        assertThat(WakePassword.parse("192.168.1.255")!!.bytes())
            .isEqualTo(byteArrayOf(192.toByte(), 168.toByte(), 1, 255.toByte()))
    }
    @Test
    fun `malformed secureon passwords are rejected`() {
        listOf("", "aa:bb:cc", "aa:bb:cc:dd:ee", "aa:bb-cc:dd", "aa:bb:cc:dd:ee:ff:00", "1.2.3", "1.2.3.256", "1.2.3.4.5")
            .forEach { assertThat(WakePassword.parse(it)).isNull() }
    }
    @Test
    fun `the password is appended after the 16 address repeats`() {
        val password = WakePassword.parse("1.2.3.4")
        val packet = WakeOnLan(mac, null, 9, password).magicPacket()
        assertThat(packet.size).isEqualTo(106)
        assertThat(packet.copyOfRange(0, 102)).isEqualTo(mac.magicPacket())
        assertThat(packet.copyOfRange(102, 106)).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(WakeOnLan(mac, null, 9).magicPacket()).isEqualTo(mac.magicPacket())
    }
    @Test
    fun `fwknop and port knock configs never wake`() {
        val wake = WakeOnLanDraft(mac = "aa:bb:cc:dd:ee:ff")
        listOf(ConfigKind.FWKNOP, ConfigKind.PORT_KNOCK).forEach { kind ->
            val knock = spaDraft.copy(kind = kind, wakeOnLan = wake, server = spaDraft.server.copy(knockSequence = "7000"))
            val config = (validate(knock) as DomainResult.Success).data
            assertThat(config.wakeOnLan).isNull()
            assertThat(config.kind).isEqualTo(kind)
        }
    }
    @Test
    fun `wake-on-lan draft ignores the other sections`() {
        val hidden = draft.copy(
            server = ServerDraft(host = "not a host"),
            access = AccessDraft(ports = "22"),
            crypto = CryptoDraft(hmac = "x", legacy = true),
            wakeOnLan = WakeOnLanDraft(mac = "aabbccddeeff"),
        )
        val config = (validate(hidden) as DomainResult.Success).data
        assertThat(config.kind).isEqualTo(ConfigKind.WAKE_ON_LAN)
        assertThat(config.method).isEqualTo(KnockMethod.Spa)
    }
    @Test
    fun `wake-on-lan draft defaults to port 9 and the local broadcast`() {
        val config = (wakeOf(WakeOnLanDraft(mac = "aabbccddeeff", port = "")) as DomainResult.Success).data
        assertThat(config.wakeOnLan).isEqualTo(WakeOnLan(mac, host = WakeOnLan.BROADCAST, port = 9))
        assertThat(config.server.host).isEqualTo(WakeOnLan.BROADCAST)
    }
    @Test
    fun `invalid fields are reported`() {
        val enabled = WakeOnLanDraft(mac = "aa:bb:cc:dd:ee:ff")
        assertThat((wakeOf(enabled.copy(mac = "nope")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakeMacInvalid)
        assertThat((wakeOf(enabled.copy(port = "70000")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakePortInvalid)
        assertThat((wakeOf(enabled.copy(host = "192.168.1 .255")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakeHostInvalid)
        assertThat((wakeOf(enabled.copy(password = "secret")) as DomainResult.Failure).error)
            .isEqualTo(ConfigError.WakePasswordInvalid)
        assertThat((wakeOf(enabled.copy(password = "  ")) as DomainResult.Success).data.wakeOnLan?.password).isNull()
    }
    @Test
    fun `wake-on-lan survives draft, entity and backup round trips`() {
        listOf(null, WakePassword.parse("00:22:44:66:88:aa"), WakePassword.parse("192.168.1.1")).forEach { password ->
            val wake = WakeOnLanDraft(mac = mac.value, host = "192.168.1.255", port = "7", password = password?.value.orEmpty())
            val config = (wakeOf(wake) as DomainResult.Success).data
            assertThat(config.wakeOnLan).isEqualTo(WakeOnLan(mac, "192.168.1.255", 7, password))
            assertThat((validate(config.toDraft()) as DomainResult.Success).data).isEqualTo(config)
            assertThat(config.toEntity().toDomain()).isEqualTo(config)
            assertThat(Backup(settings = null, configs = listOf(config)).toPayload().toDomain().configs)
                .containsExactly(config)
        }

        assertThat("AA:BB:CC:DD:EE:FF\n7\n".toWakeOnLan()).isEqualTo(WakeOnLan(mac, null, 7))
        assertThat(sampleConfig().toEntity().wakeOnLan).isEmpty()
        assertThat(sampleConfig().toEntity().toDomain().wakeOnLan).isNull()
    }
    @Test
    fun `send targets the configured host, or the server host`() = runTest {
        val sent = mutableListOf<Triple<List<Byte>, String, Int>>()
        val repository = object : WakeOnLanRepository {
            override suspend fun sendMagicPacket(packet: ByteArray, host: String, port: Int): DomainResult<Unit, KnockError> {
                sent += Triple(packet.toList(), host, port)
                return DomainResult.Success(Unit)
            }
        }
        val password = WakePassword.parse("1.2.3.4")
        val configs = FakeConfigRepository(
            listOf(
                sampleConfig("lan").copy(wakeOnLan = WakeOnLan(mac, "192.168.1.255", 9)),
                sampleConfig("wan").copy(wakeOnLan = WakeOnLan(mac, null, 4009, password)),
                sampleConfig("none"),
            )
        )
        val history = FakeKnockHistoryRepository()
        val send = SendWakeOnLanUseCase(GetConfigUseCase(configs), repository, history)
        assertThat(send(Nickname("lan"))).isEqualTo(DomainResult.Success(Unit))
        assertThat(send(Nickname("wan"))).isEqualTo(DomainResult.Success(Unit))
        assertThat(send(Nickname("none"))).isInstanceOf(DomainResult.Failure::class.java)
        assertThat(send(Nickname("missing"))).isEqualTo(DomainResult.Failure(KnockError.ConfigMissing))
        assertThat(sent).containsExactly(
            Triple(mac.magicPacket().toList(), "192.168.1.255", 9),
            Triple((mac.magicPacket() + byteArrayOf(1, 2, 3, 4)).toList(), "knock.example.org", 4009),
        ).inOrder()
        assertThat(history.recorded.map { it.nickname.value to it.outcome }).containsExactly(
            "lan" to KnockLogOutcome.Sent,
            "wan" to KnockLogOutcome.Sent,
            "none" to KnockLogOutcome.Failed("CONFIG", "WAKE_MAC_INVALID"),
            "missing" to KnockLogOutcome.Failed("CONFIG_MISSING"),
        ).inOrder()
        assertThat(history.recorded.map { it.kind }.distinct()).containsExactly(KnockLogKind.WAKE_ON_LAN)
        assertThat(history.recorded.map { it.source }.distinct()).containsExactly(null)
    }
}
