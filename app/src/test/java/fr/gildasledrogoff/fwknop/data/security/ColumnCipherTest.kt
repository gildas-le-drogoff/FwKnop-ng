package fr.gildasledrogoff.fwknop.data.security
import fr.gildasledrogoff.fwknop.data.local.security.ColumnCipher
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.fakes.FakeKeysetStore
import fr.gildasledrogoff.fwknop.fakes.FakeMasterKeyStore
import fr.gildasledrogoff.fwknop.fakes.newKeyset
import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import org.junit.Assert.assertThrows
import org.junit.Test
class ColumnCipherTest {
    private val cipher = ColumnCipher(newKeyset().aead())
    @Test
    fun `values round-trip, unicode included`() {
        for (value in listOf("", "s3cr3t", "clé HMAC ✓", "x".repeat(4096))) {
            assertThat(cipher.decrypt("KEY", cipher.encrypt("KEY", value))).isEqualTo(value)
        }
    }
    @Test
    fun `ciphertext reveals neither the value nor its repetitions`() {
        val first = cipher.encrypt("KEY", "s3cr3t")
        val second = cipher.encrypt("KEY", "s3cr3t")
        assertThat(first).doesNotContain("s3cr3t")
        assertThat(first).isNotEqualTo(second)
    }
    @Test
    fun `a value moved to another column does not decrypt`() {
        val key = cipher.encrypt("KEY", "s3cr3t")
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt("HMAC", key) }
    }
    @Test
    fun `a tampered value does not decrypt`() {
        val bytes = java.util.Base64.getDecoder().decode(cipher.encrypt("KEY", "s3cr3t"))
        bytes[bytes.size - 1] = (bytes.last() + 1).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(bytes)
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt("KEY", tampered) }
    }
    @Test
    fun `another keyset cannot decrypt`() {
        val other = ColumnCipher(newKeyset().aead())
        assertThrows(GeneralSecurityException::class.java) { other.decrypt("KEY", cipher.encrypt("KEY", "s3cr3t")) }
    }
    @Test
    fun `rotation keeps old values readable until pruned, after re-encryption they survive the prune`() {
        val keys = FakeMasterKeyStore().apply { keys["a"] = fr.gildasledrogoff.fwknop.data.local.security.MasterKeyPolicy(false) }
        val keysets = FakeKeysetStore(keys)
        val cipher = ColumnCipher(keysets.create("a").aead())
        val old = cipher.encrypt("KEY", "s3cr3t")
        cipher.swap(keysets.rotate("a").aead())
        assertThat(cipher.decrypt("KEY", old)).isEqualTo("s3cr3t")
        val reencrypted = cipher.encrypt("KEY", cipher.decrypt("KEY", old))
        cipher.swap(keysets.prune("a").aead())
        assertThat(cipher.decrypt("KEY", reencrypted)).isEqualTo("s3cr3t")
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt("KEY", old) }
        assertThat(keysets.read("a").size()).isEqualTo(1)
    }
}
