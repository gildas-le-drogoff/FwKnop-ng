// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.*
import fr.gildasledrogoff.fwknop.fakes.*
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultOpenerTest {
    private val keys = FakeMasterKeyStore()
    private val keysets = FakeKeysetStore(keys)
    private val metadata = FakeVaultMetadataStore()
    private val database = FakeDatabaseFileStore()
    private val passphrases = FakeSealedPassphraseStore()
    private val opener =
        VaultOpener(keys, keysets, metadata, VaultJanitor(keys, keysets), SequentialAliases(), database, passphrases)
    @Test
    fun `the first open creates an unlocked vault`() {
        val handle = opener.open()
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_0", lockEnabled = false))
        assertThat(keys.keys).containsExactly("fwknop_master_0", MasterKeyPolicy(userAuthRequired = false))
        assertThat(opener.open().equalsKeyset(handle)).isTrue()
    }
    @Test
    fun `a vault created locked requires authentication from the start`() {
        opener.open(lockNew = true)
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_0", lockEnabled = true))
        assertThat(keys.keys).containsExactly("fwknop_master_0", MasterKeyPolicy(userAuthRequired = true))
    }
    @Test
    fun `a missing master key means the data is lost, never a new vault`() {
        opener.open()
        keys.keys.clear()
        assertThrows(VaultUnrecoverableException::class.java) { opener.open() }
        assertThat(metadata.metadata.masterKeyAlias).isEqualTo("fwknop_master_0")
    }
    @Test
    fun `leftovers of an interrupted re-wrap are purged`() {
        opener.open()
        keys.keys["fwknop_master_orphan"] = MasterKeyPolicy(userAuthRequired = true)
        keysets.stored["fwknop_master_orphan"] = ByteArray(1)
        opener.open()
        assertThat(keys.aliases()).containsExactly("fwknop_master_0")
        assertThat(keysets.aliases()).containsExactly("fwknop_master_0")
    }
    @Test
    fun `an interrupted creation starts over cleanly`() {
        keys.keys["fwknop_master_crashed"] = MasterKeyPolicy(userAuthRequired = false)
        keysets.stored["fwknop_master_crashed"] = ByteArray(1)
        opener.open()
        assertThat(keys.aliases()).containsExactly("fwknop_master_0")
        assertThat(keysets.aliases()).containsExactly("fwknop_master_0")
        assertThat(newKeyset().equalsKeyset(keysets.read("fwknop_master_0"))).isFalse()
    }
    @Test
    fun `a database left by lost keys is deleted when a new vault is created`() {
        database.exists = true
        opener.open()
        assertThat(database.exists).isFalse()
    }
    @Test
    fun `a passphrase sealed by a lost keyset is dropped when a new vault is created`() {
        passphrases.sealed = ByteArray(1)
        opener.open()
        assertThat(passphrases.sealed).isNull()
    }
    @Test
    fun `reopening an existing vault keeps its passphrase`() {
        opener.open()
        passphrases.sealed = ByteArray(1)
        opener.open()
        assertThat(passphrases.sealed).isNotNull()
    }
}
