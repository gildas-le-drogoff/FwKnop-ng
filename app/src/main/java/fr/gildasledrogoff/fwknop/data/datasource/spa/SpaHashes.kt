package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.model.DigestAlgorithm
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal fun DigestAlgorithm.digestBase64(text: String): String =
    MessageDigest.getInstance(jcaDigest).digest(text.toByteArray(Charsets.US_ASCII)).toSpaBase64()

internal fun DigestAlgorithm.hmacBase64(key: ByteArray, text: String): String =
    Mac.getInstance(jcaMac)
        .apply { init(SecretKeySpec(key, jcaMac)) }
        .doFinal(text.toByteArray(Charsets.US_ASCII))
        .toSpaBase64()

private val DigestAlgorithm.jcaDigest: String
    get() = when (this) {
        DigestAlgorithm.MD5 -> "MD5"
        DigestAlgorithm.SHA1 -> "SHA-1"
        DigestAlgorithm.SHA256 -> "SHA-256"
        DigestAlgorithm.SHA384 -> "SHA-384"
        DigestAlgorithm.SHA512 -> "SHA-512"
    }
private val DigestAlgorithm.jcaMac: String
    get() = when (this) {
        DigestAlgorithm.MD5 -> "HmacMD5"
        DigestAlgorithm.SHA1 -> "HmacSHA1"
        DigestAlgorithm.SHA256 -> "HmacSHA256"
        DigestAlgorithm.SHA384 -> "HmacSHA384"
        DigestAlgorithm.SHA512 -> "HmacSHA512"
    }
