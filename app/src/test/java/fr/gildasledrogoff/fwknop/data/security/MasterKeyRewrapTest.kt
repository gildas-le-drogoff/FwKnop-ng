// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.*
import fr.gildasledrogoff.fwknop.fakes.*
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
    private val keysets = FakeKeysetStore(keys)
    private val metadata = FakeVaultMetadataStore()
    private val aliases = SequentialAliases()
    private val rewrap = MasterKeyRewrap(keys, keysets, metadata, aliases)
    private val handle by lazy {
        val janitor = VaultJanitor(keys, keysets)
        VaultOpener(keys, keysets, metadata, janitor, aliases, FakeDatabaseFileStore(), FakeSealedPassphraseStore()).open()
    }
    @Before
    fun createVault() {
        handle
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_0", lockEnabled = false))
    }
    @Test
    fun `enabling the lock moves the same keyset under an auth-bound key and drops the old one`() {
        rewrap.rewrap(handle, lockEnabled = true)
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_1", lockEnabled = true))
        assertThat(keys.keys).containsExactly("fwknop_master_1", MasterKeyPolicy(userAuthRequired = true))
        assertThat(keysets.aliases()).containsExactly("fwknop_master_1")
        assertThat(keysets.read("fwknop_master_1").equalsKeyset(handle)).isTrue()
    }
    @Test
    fun `disabling the lock moves it back under a key without auth`() {
        rewrap.rewrap(handle, lockEnabled = true)
        rewrap.rewrap(handle, lockEnabled = false)
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_2", lockEnabled = false))
        assertThat(keys.keys).containsExactly("fwknop_master_2", MasterKeyPolicy(userAuthRequired = false))
        assertThat(keysets.read("fwknop_master_2").equalsKeyset(handle)).isTrue()
    }
    @Test
    fun `a failing key generation leaves the vault untouched`() {
        keys.failGenerate = true
        assertThrows(Exception::class.java) { rewrap.rewrap(handle, lockEnabled = true) }
        assertUntouched()
    }
    @Test
    fun `a failing keyset write rolls the new key back`() {
        keysets.failWrite = true
        assertThrows(Exception::class.java) { rewrap.rewrap(handle, lockEnabled = true) }
        assertUntouched()
    }
    @Test
    fun `a keyset that does not read back identical is never committed`() {
        keysets.corruptWrites = true
        assertThrows(Exception::class.java) { rewrap.rewrap(handle, lockEnabled = true) }
        assertUntouched()
    }
    @Test
    fun `a failing metadata commit rolls everything back`() {
        metadata.failCommit = true
        assertThrows(Exception::class.java) { rewrap.rewrap(handle, lockEnabled = true) }
        metadata.failCommit = false
        assertUntouched()
    }
    private fun assertUntouched() {
        assertThat(metadata.metadata).isEqualTo(VaultMetadata("fwknop_master_0", lockEnabled = false))
        assertThat(keys.keys).containsExactly("fwknop_master_0", MasterKeyPolicy(userAuthRequired = false))
        assertThat(keysets.aliases()).containsExactly("fwknop_master_0")
        assertThat(keysets.read("fwknop_master_0").equalsKeyset(handle)).isTrue()
    }
}
