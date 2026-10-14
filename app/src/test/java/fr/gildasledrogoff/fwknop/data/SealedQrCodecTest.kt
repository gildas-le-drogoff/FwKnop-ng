// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.backup.BackupCipher
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.SealedQr
import fr.gildasledrogoff.fwknop.domain.model.toQrContent
import fr.gildasledrogoff.fwknop.domain.usecase.ParseQrConfigUseCase
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import org.junit.Test
import java.util.*
        SealedQrCodec(BackupCipher(iterations = BackupCipher.MIN_ITERATIONS, magic = BackupCipher.QR_MAGIC))
    private val content = sampleConfig().toQrContent()
    @Test
    fun `sealed content opens with the same passphrase and parses like the plain one`() {
        val sealed = codec.seal(content, PASSPHRASE.toCharArray())
        assertThat(SealedQr.isSealed(sealed)).isTrue()
        val opened = codec.open(sealed, PASSPHRASE.toCharArray())
        assertThat((opened as DomainResult.Success).data).isEqualTo(content)
        val parse = ParseQrConfigUseCase()
        assertThat(parse(opened.data)).isEqualTo(parse(content))
    }
    @Test
    fun `keys do not appear in the sealed content`() {
        val sealed = codec.seal("KEY_BASE64:c2VjcmV0LWtleS12YWx1ZQ==", PASSPHRASE.toCharArray())
        assertThat(sealed).doesNotContain("c2VjcmV0LWtleS12YWx1ZQ")
        assertThat(String(decode(sealed), Charsets.ISO_8859_1)).doesNotContain("secret-key-value")
    }
    @Test
    fun `wrong passphrase is rejected`() {
        val sealed = codec.seal(content, PASSPHRASE.toCharArray())
        assertThat(codec.open(sealed, "battery staple horse".toCharArray()))
            .isEqualTo(DomainResult.Failure(BackupError.WrongPassphrase))
    }
    @Test
    fun `an altered payload is rejected`() {
        val bytes = decode(codec.seal(content, PASSPHRASE.toCharArray()))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        val altered = SealedQr.PREFIX + Base64.getEncoder().encodeToString(bytes)
        assertThat(codec.open(altered, PASSPHRASE.toCharArray()))
            .isEqualTo(DomainResult.Failure(BackupError.WrongPassphrase))
    }
    @Test
    fun `a backup file is not taken for a sealed QR code`() {
        val backup = BackupCipher(iterations = BackupCipher.MIN_ITERATIONS)
            .seal(content.toByteArray(), PASSPHRASE.toCharArray())
        val disguised = SealedQr.PREFIX + Base64.getEncoder().encodeToString(backup)
        assertThat(codec.open(disguised, PASSPHRASE.toCharArray()))
            .isEqualTo(DomainResult.Failure(BackupError.UnreadableQr))
    }
    @Test
    fun `malformed or plain contents are unreadable`() {
        val unreadable = DomainResult.Failure(BackupError.UnreadableQr)
        assertThat(codec.open(content, PASSPHRASE.toCharArray())).isEqualTo(unreadable)
        assertThat(codec.open(SealedQr.PREFIX + "not base64 !", PASSPHRASE.toCharArray())).isEqualTo(unreadable)
        assertThat(codec.open(SealedQr.PREFIX + "QUJD", PASSPHRASE.toCharArray())).isEqualTo(unreadable)
    }
    @Test
    fun `compression keeps the QR code close to the plain one`() {
        val sealed = codec.seal(content, PASSPHRASE.toCharArray())
        assertThat(sealed.length).isLessThan(content.length * 2)
    }
    private fun decode(sealed: String) = Base64.getDecoder().decode(sealed.removePrefix(SealedQr.PREFIX))
    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
