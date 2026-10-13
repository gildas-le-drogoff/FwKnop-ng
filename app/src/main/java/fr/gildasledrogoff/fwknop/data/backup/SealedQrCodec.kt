package fr.gildasledrogoff.fwknop.data.backup

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.SealedQr
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater


class SealedQrCodec(
    private val cipher: BackupCipher = BackupCipher(magic = BackupCipher.QR_MAGIC),
) {
    fun seal(content: String, passphrase: CharArray): String {
        val plaintext = deflate(content.toByteArray(Charsets.UTF_8))
        try {
            return SealedQr.PREFIX + ENCODER.encodeToString(cipher.seal(plaintext, passphrase))
        } finally {
            plaintext.fill(0)
        }
    }

    fun open(content: String, passphrase: CharArray): DomainResult<String, BackupError> {
        if (!SealedQr.isSealed(content)) return DomainResult.Failure(BackupError.UnreadableQr)
        val sealed = try {
            DECODER.decode(content.removePrefix(SealedQr.PREFIX).trim())
        } catch (_: IllegalArgumentException) {
            return DomainResult.Failure(BackupError.UnreadableQr)
        }
        return when (val opened = cipher.open(sealed, passphrase)) {
            BackupCipher.Opened.Unrecognized -> DomainResult.Failure(BackupError.UnreadableQr)
            BackupCipher.Opened.Rejected -> DomainResult.Failure(BackupError.WrongPassphrase)
            is BackupCipher.Opened.Plaintext -> inflate(opened.bytes)?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(BackupError.UnreadableQr)
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val out = ByteArrayOutputStream()
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(BUFFER_BYTES)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
            bytes.fill(0)
        }
    }

    
    private fun inflate(bytes: ByteArray): String? {
        val inflater = Inflater()
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(bytes)
            val buffer = ByteArray(BUFFER_BYTES)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, count)
                if (out.size() > MAX_CONTENT_BYTES) return null
            }
            return out.toByteArray().decodeToString()
        } catch (_: DataFormatException) {
            return null
        } finally {
            inflater.end()
            bytes.fill(0)
        }
    }

    private companion object {
        const val BUFFER_BYTES = 1024
        const val MAX_CONTENT_BYTES = 16 * 1024
        val ENCODER: Base64.Encoder = Base64.getEncoder()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
