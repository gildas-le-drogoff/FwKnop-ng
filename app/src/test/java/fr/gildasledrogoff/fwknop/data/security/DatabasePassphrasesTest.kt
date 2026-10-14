// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.DatabasePassphrases
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyPolicy
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.fakes.FakeKeysetStore
import fr.gildasledrogoff.fwknop.fakes.FakeMasterKeyStore
import fr.gildasledrogoff.fwknop.fakes.FakeSealedPassphraseStore
import fr.gildasledrogoff.fwknop.fakes.newKeyset
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
    private val passphrases = DatabasePassphrases(store)
    private val aead = newKeyset().aead()
    @Test
    fun `a raw 256-bit key in SQLCipher's x'hex' form, created once then stable`() {
        val first = passphrases.obtain(aead)
        assertThat(first.toString(Charsets.US_ASCII)).matches("x'[0-9a-f]{64}'")
        assertThat(passphrases.obtain(aead)).isEqualTo(first)
    }
    @Test
    fun `stored only sealed`() {
        val passphrase = passphrases.obtain(aead)
        val sealed = store.sealed!!.toString(Charsets.ISO_8859_1)
        assertThat(sealed).doesNotContain(passphrase.toString(Charsets.US_ASCII).drop(2).dropLast(1))
    }
    @Test
    fun `two vaults never share a passphrase`() {
        val other = DatabasePassphrases(FakeSealedPassphraseStore()).obtain(newKeyset().aead())
        assertThat(passphrases.obtain(aead)).isNotEqualTo(other)
    }
    @Test
    fun `another keyset cannot unseal it`() {
        passphrases.obtain(aead)
        assertThrows(GeneralSecurityException::class.java) { passphrases.obtain(newKeyset().aead()) }
    }
    @Test
    fun `rotation re-seals the same passphrase under the new key, readable once the old one is pruned`() {
        val keysets = FakeKeysetStore(FakeMasterKeyStore().apply { keys["a"] = POLICY })
        keysets.create("a")
        val before = passphrases.obtain(keysets.read("a").aead())
        val sealedBefore = store.sealed
        passphrases.reseal(keysets.rotate("a").aead())
        val pruned = keysets.prune("a").aead()
        assertThat(store.sealed).isNotEqualTo(sealedBefore)
        assertThat(passphrases.obtain(pruned)).isEqualTo(before)
    }
    private companion object {
        val POLICY = MasterKeyPolicy(userAuthRequired = false)
    }
}
