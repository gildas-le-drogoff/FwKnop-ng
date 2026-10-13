package fr.gildasledrogoff.fwknop.domain.model

import java.nio.CharBuffer


data class Backup(
    val settings: AppSettings?,
    val configs: List<SpaConfig>,
    val nfcTagKey: NfcTagKey? = null,
)
class SealedBackup(val file: ByteArray, val configCount: Int)
class BackupPassphrase(val chars: CharArray) {
    val isLongEnough: Boolean get() = isLongEnough(CharBuffer.wrap(chars))
    fun wipe() = chars.fill('\u0000')

    companion object {
        const val MIN_LENGTH = 2
        fun isLongEnough(text: CharSequence): Boolean =
            text.count { !it.isWhitespace() } >= MIN_LENGTH
    }
}
