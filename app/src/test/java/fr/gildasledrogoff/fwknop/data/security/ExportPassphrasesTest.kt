// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.ExportPassphrases
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyPolicy
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.fakes.FakeKeysetStore
import fr.gildasledrogoff.fwknop.fakes.FakeMasterKeyStore
import fr.gildasledrogoff.fwknop.fakes.newKeyset
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import fr.gildasledrogoff.fwknop.fakes.FakeSealedExportPassphraseStore

class ExportPassphrasesTest {
    private val store = FakeSealedExportPassphraseStore()
    private val passphrases = ExportPassphrases(store)
    private val aead = newKeyset().aead()
    @Test
    fun `none until one is chosen`() {
        assertThat(passphrases.isSet()).isFalse()
        assertThat(passphrases.read(aead)).isNull()
    }
    @Test
    fun `stored only sealed, read back intact`() {
        passphrases.write(aead, PASSPHRASE.toCharArray())
        assertThat(passphrases.isSet()).isTrue()
        assertThat(store.sealed!!.toString(Charsets.ISO_8859_1)).doesNotContain(PASSPHRASE)
        assertThat(passphrases.read(aead)!!.concatToString()).isEqualTo(PASSPHRASE)
    }
    @Test
    fun `another keyset cannot unseal it`() {
        passphrases.write(aead, PASSPHRASE.toCharArray())
        assertThrows(GeneralSecurityException::class.java) { passphrases.read(newKeyset().aead()) }
    }
    @Test
    fun `rotation re-seals it under the new key, readable once the old one is pruned`() {
        val keysets = FakeKeysetStore(FakeMasterKeyStore().apply { keys["a"] = POLICY })
        keysets.create("a")
        passphrases.write(keysets.read("a").aead(), PASSPHRASE.toCharArray())
        val sealedBefore = store.sealed
        passphrases.reseal(keysets.rotate("a").aead())
        val pruned = keysets.prune("a").aead()
        assertThat(store.sealed).isNotEqualTo(sealedBefore)
        assertThat(passphrases.read(pruned)!!.concatToString()).isEqualTo(PASSPHRASE)
    }
    @Test
    fun `rotation without passphrase is a no-op`() {
        passphrases.reseal(aead)
        assertThat(store.sealed).isNull()
    }
    private companion object {
        const val PASSPHRASE = "phrase secrète d'export"
        val POLICY = MasterKeyPolicy(userAuthRequired = false)
    }
}
