// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.backup.BackupCipher
import fr.gildasledrogoff.fwknop.data.backup.BackupConfig
import fr.gildasledrogoff.fwknop.data.backup.BackupPayload
import fr.gildasledrogoff.fwknop.data.backup.toDomain
import fr.gildasledrogoff.fwknop.data.repository.BackupRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer
import java.util.*

class BackupCipherTest {
    private val cipher = BackupCipher(iterations = BackupCipher.MIN_ITERATIONS)
    private val plaintext = "{\"configs\":[]}".toByteArray()
    @Test
    fun `sealed file opens with the same passphrase`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        val opened = cipher.open(file, "correct horse".toCharArray())
        assertThat((opened as BackupCipher.Opened.Plaintext).bytes).isEqualTo(plaintext)
    }
    @Test
    fun `the plaintext does not appear in the file`() {
        val file = cipher.seal("secret-key-value".toByteArray(), "correct horse".toCharArray())
        assertThat(String(file, Charsets.ISO_8859_1)).doesNotContain("secret-key-value")
    }
    @Test
    fun `two seals of the same content differ`() {
        val first = cipher.seal(plaintext, "correct horse".toCharArray())
        val second = cipher.seal(plaintext, "correct horse".toCharArray())
        assertThat(first).isNotEqualTo(second)
    }
    @Test
    fun `wrong passphrase is rejected`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        assertThat(cipher.open(file, "battery staple".toCharArray())).isEqualTo(BackupCipher.Opened.Rejected)
    }
    @Test
    fun `any altered byte is rejected, header included`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        for (index in 13 until file.size) {
            val altered = file.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThat(cipher.open(altered, "correct horse".toCharArray())).isEqualTo(BackupCipher.Opened.Rejected)
        }
    }
    @Test
    fun `an altered iteration count is rejected`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        ByteBuffer.wrap(file).putInt(9, BackupCipher.MIN_ITERATIONS + 1)
        assertThat(cipher.open(file, "correct horse".toCharArray())).isEqualTo(BackupCipher.Opened.Rejected)
    }
    @Test
    fun `out of bounds iteration counts are not even tried`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        ByteBuffer.wrap(file).putInt(9, Int.MAX_VALUE)
        assertThat(cipher.open(file, "correct horse".toCharArray())).isEqualTo(BackupCipher.Opened.Unrecognized)
    }
    @Test
    fun `foreign or truncated files are not recognized`() {
        val file = cipher.seal(plaintext, "correct horse".toCharArray())
        assertThat(cipher.open("PNG not a backup at all, really".toByteArray(), "x".toCharArray()))
            .isEqualTo(BackupCipher.Opened.Unrecognized)
        assertThat(cipher.open(file.copyOf(20), "correct horse".toCharArray()))
            .isEqualTo(BackupCipher.Opened.Unrecognized)
        val newerVersion = file.copyOf().also { it[8] = 2 }
        assertThat(cipher.open(newerVersion, "correct horse".toCharArray()))
            .isEqualTo(BackupCipher.Opened.Unrecognized)
    }
}
class BackupRepositoryTest {
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }
    private val repository = BackupRepositoryImpl(dispatchers)
    private fun passphrase() = BackupPassphrase("correct horse battery".toCharArray())
    @Test
    fun `settings and every kind of config survive the round trip`() = runTest {
        val backup = Backup(
            settings = AppSettings(
                dnsLookupEnabled = false,
                nfcKnockEnabled = true,
                notificationsEnabled = false,
                ipLookupUrl = "https://ip.example.org",
                autoLockDelay = AutoLockDelay.MINUTES_5,
                vpnRouting = VpnRouting.OUTSIDE_VPN,
            ),
            configs = listOf(
                sampleConfig("home"),
                sampleConfig(
                    nickname = "router",
                    access = AccessRule.NatAccess(PortAccess("tcp/22,udp/1194", 90, keepOpen = true), "10.0.0.5", 80),
                    sourceIp = SourceIp.Prompt,
                ).copy(externalApp = ExternalApp.OpenVpn("work")),
                sampleConfig(
                    nickname = "gpg box",
                    access = AccessRule.ServerCommand("systemctl restart sshd"),
                    sourceIp = SourceIp.Resolve,
                    encryption = Encryption.Gpg(GpgKeyId(0x1234ABCDL), GpgKeyId(0x5678EF01L)),
                ).copy(externalApp = ExternalApp.SshUri("ssh://admin@gpg.example.org")),
            ),
            nfcTagKey = NfcTagKey(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })),
        )
        val file = repository.seal(backup, passphrase())
        assertThat(repository.open(file, passphrase())).isEqualTo(DomainResult.Success(backup))
    }
    @Test
    fun `older backups without a gpg mode infer it from the recipient`() {
        val payload = BackupPayload(
            configs = listOf(
                BackupConfig("gpg", gpgKey = 0x1234, gpgSig = 0x5678),
                BackupConfig("plain", key = "secret"),
            ),
        )
        val encryptions = payload.toDomain().configs.map { it.crypto.encryption }
        assertThat(encryptions[0]).isEqualTo(Encryption.Gpg(GpgKeyId(0x1234), GpgKeyId(0x5678)))
        assertThat(encryptions[1]).isEqualTo(Encryption.Rijndael(SecretKey("secret", base64 = false)))
    }
    @Test
    fun `a selection export keeps the settings out`() = runTest {
        val backup = Backup(settings = null, configs = listOf(sampleConfig("home")))
        val file = repository.seal(backup, passphrase())
        assertThat(repository.open(file, passphrase())).isEqualTo(DomainResult.Success(backup))
    }
    @Test
    fun `a malformed nfc tag key is dropped, the current one being kept`() = runTest {
        val backup = Backup(AppSettings(), listOf(sampleConfig()), NfcTagKey("dG9vIHNob3J0"))
        val file = repository.seal(backup, passphrase())
        assertThat(repository.open(file, passphrase())).isEqualTo(DomainResult.Success(backup.copy(nfcTagKey = null)))
    }
    @Test
    fun `wrong passphrase and foreign files are told apart`() = runTest {
        val file = repository.seal(Backup(AppSettings(), listOf(sampleConfig())), passphrase())
        assertThat(repository.open(file, BackupPassphrase("another passphrase".toCharArray())))
            .isEqualTo(DomainResult.Failure(BackupError.WrongPassphrase))
        assertThat(repository.open(ByteArray(100), passphrase()))
            .isEqualTo(DomainResult.Failure(BackupError.NotABackup))
    }
}
