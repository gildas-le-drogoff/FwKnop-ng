package fr.gildasledrogoff.fwknop.domain.model


class NfcTagDigest(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is NfcTagDigest && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        const val SALT_BYTES = 16
        const val SIZE = SALT_BYTES + 32
    }
}


@JvmInline
value class NfcTagKey(val base64: String)
