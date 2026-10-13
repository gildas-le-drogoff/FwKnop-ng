package fr.gildasledrogoff.fwknop.data.datasource.spa

import java.security.SecureRandom
import javax.inject.Inject

interface SpaEntropy {
    fun randomValue(): String
    fun salt(): ByteArray
}

class SecureSpaEntropy @Inject constructor() : SpaEntropy {
    private val random = SecureRandom()
    override fun randomValue(): String = buildString {
        append(1 + random.nextInt(9))
        repeat(SpaFormat.RANDOM_VALUE_LENGTH - 1) { append(random.nextInt(10)) }
    }

    override fun salt(): ByteArray = ByteArray(SpaFormat.SALT_LENGTH).also(random::nextBytes)
}
