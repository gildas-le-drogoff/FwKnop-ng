// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.spa
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.datasource.spa.RijndaelCipher
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaMessageEncoder
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaPacketEncoder
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
        override fun randomValue() = "1234567890123456"
        override fun salt() = ByteArray(8) { (it + 1).toByte() }
    }
    private val encoder = SpaPacketEncoder(
        SpaMessageEncoder(entropy, Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC)) { "u0_a123" },
        RijndaelCipher(entropy),
    )
    private val params = SpaPacketParams(
        accessPorts = "tcp/22",
        sourceIp = "198.51.100.4",
        key = SecretKey("secretkey", base64 = false),
        hmac = SecretKey("hmackey", base64 = false),
        timeoutSeconds = 30,
        nat = null,
        serverCommand = null,
        legacyMode = false,
        digest = DigestAlgorithm.SHA256,
        hmacDigest = DigestAlgorithm.SHA256,
    )
    @Test
    fun `rijndael packet with hmac matches libfko`() {
        assertThat(encoder.rijndaelPacket(params)).isEqualTo(
            "8BAgMEBQYHCJyBYsZWQBCHHRulLt21Ag/29SJ1/4mdjLguvq7rAvfmyWZZcgZNTk5RudfFwnyLpCFDcaBq6ILPecM5/Til" +
                "CMb9nviiakHwVzJrKxGLpYwfue46kUhpVWWXavBipLd8ZynxNixUyfJFCfHKKd9y7lWFCZ9W5go9e1PbBBKFU/qM4vCA2L" +
                "ply+Qa0Ns5zCd3ZCGtUTbQ8jr+gGdD+LqoQo8",
        )
    }
    @Test
    fun `legacy packet without hmac matches libfko`() {
        val legacy = params.copy(
            key = SecretKey("abc", base64 = false),
            hmac = null,
            legacyMode = true,
            digest = DigestAlgorithm.MD5,
            timeoutSeconds = null,
        )
        assertThat(encoder.rijndaelPacket(legacy)).isEqualTo(
            "8BAgMEBQYHCMhkO3toDlqk0zRAD+oXLXTj3xvXQOsYjOiCb8pkxnpqPbRuHCUD/Ox2O1xYrEgwsZ8Q+TVHvxQCw48URjo4" +
                "c/ZIoM3Xp7xG12o1QD/HFWRpXuO15YMs50DVhW8wrHJ9SXaam8Ei6P/hOCspGWu1KAg",
        )
    }
    @Test
    fun `gpg payload is the encoded message followed by its digest`() {
        assertThat(encoder.gpgPayload(params)).isEqualTo(
            "1234567890123456:dTBfYTEyMw:1700000000:2.1.0:3:MTk4LjUxLjEwMC40LHRjcC8yMg:30:" +
                "XXs5n5/0uOnHi3Au73YVJBL38Cr7mlPs8ej0DHmPieE",
        )
    }
    @Test
    fun `gpg packet strips padding and prefix and appends the hmac`() {
        val cipher = "hQEMA" + "A".repeat(40) + "xyz=="
        assertThat(encoder.gpgPacket(params, cipher)).isEqualTo(
            DomainResult.Success("EMA" + "A".repeat(40) + "xyz" + "ZyhPVHADsYFaTU/OW6OflhRn3Op5XKZ60qMr1xAAseQ"),
        )
    }
    @Test
    fun `command message carries neither nat nor timeout`() {
        val payload = encoder.gpgPayload(params.copy(serverCommand = "198.51.100.4,reboot"))
        assertThat(payload).startsWith("1234567890123456:dTBfYTEyMw:1700000000:2.1.0:0:MTk4LjUxLjEwMC40LHJlYm9vdA:")
        assertThat(payload!!.split(':')).hasSize(7)
    }
    @Test
    fun `nat access is appended before the timeout`() {
        val payload = encoder.gpgPayload(params.copy(nat = NatTarget("192.168.1.20", 2222, local = false)))
        assertThat(payload).startsWith(
            "1234567890123456:dTBfYTEyMw:1700000000:2.1.0:4:MTk4LjUxLjEwMC40LHRjcC8yMg:MTkyLjE2OC4xLjIwLDIyMjI:30:",
        )
    }
    @Test
    fun `invalid inputs yield no packet`() {
        val invalid = listOf(
            params.copy(sourceIp = "2001:db8::1"),
            params.copy(accessPorts = "icmp/0"),
            params.copy(accessPorts = "tcp/70000"),
            params.copy(accessPorts = "tcp/22,"),
            params.copy(timeoutSeconds = -1),
            params.copy(key = SecretKey("k".repeat(33), base64 = false)),
            params.copy(key = SecretKey("not base64!", base64 = true)),
            params.copy(hmac = SecretKey("h".repeat(129), base64 = false)),
            params.copy(nat = NatTarget("bad host", 22, local = false)),
            params.copy(serverCommand = "198.51.100.4,"),
        )
        invalid.forEach { assertThat(encoder.rijndaelPacket(it)).isNull() }
    }
    @Test
    fun `gpg cipher too short is rejected`() {
        assertThat(encoder.gpgPacket(params, "hQEMA")).isEqualTo(DomainResult.Failure(KnockError.PacketGeneration))
    }
    @Test
    fun `gpg cipher without the rsa prefix is rejected`() {
        val ecc = "hF4D" + "A".repeat(600)
        assertThat(encoder.gpgPacket(params, ecc))
            .isEqualTo(DomainResult.Failure(KnockError.Gpg(GpgError.UnsupportedKey)))
    }
    @Test
    fun `gpg cipher above the encoded limit is rejected as too large`() {
        assertThat(encoder.gpgPacket(params.copy(hmac = null), "hQ" + "A".repeat(1499)))
            .isEqualTo(DomainResult.Failure(KnockError.Gpg(GpgError.PacketTooLarge)))
    }
    @Test
    fun `gpg packet limit includes the hmac for every digest`() {
        DigestAlgorithm.entries.forEach { digest ->
            val withDigest = params.copy(hmacDigest = digest)
            val probe = (encoder.gpgPacket(withDigest, "hQ" + "A".repeat(100)) as DomainResult.Success).data
            val hmacLength = probe.length - 100
            val fitting = "hQ" + "A".repeat(SPA_LIMIT - hmacLength)
            assertThat((encoder.gpgPacket(withDigest, fitting) as DomainResult.Success).data).hasLength(SPA_LIMIT)
            assertThat(encoder.gpgPacket(withDigest, fitting + "A"))
                .isEqualTo(DomainResult.Failure(KnockError.Gpg(GpgError.PacketTooLarge)))
        }
    }
    private companion object {
        const val SPA_LIMIT = 1500
    }
}
