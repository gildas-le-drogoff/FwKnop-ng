package fr.gildasledrogoff.fwknop.domain.model


object SealedQr {
    const val PREFIX = "FWKNOP-ENC1:"
    fun isSealed(content: String): Boolean = content.startsWith(PREFIX)
}
