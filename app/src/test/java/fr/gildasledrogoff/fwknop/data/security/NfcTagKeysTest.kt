// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyPolicy
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagDigester
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagKeys
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import fr.gildasledrogoff.fwknop.fakes.FakeKeysetStore
import fr.gildasledrogoff.fwknop.fakes.FakeMasterKeyStore
import fr.gildasledrogoff.fwknop.fakes.newKeyset
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import fr.gildasledrogoff.fwknop.fakes.FakeSealedNfcTagKeyStore

class NfcTagKeysTest {
    private val store = FakeSealedNfcTagKeyStore()
    private val tagKeys = NfcTagKeys(store)
    private val aead = newKeyset().aead()
    @Test
    fun `created once on first use, then stable`() {
        assertThat(tagKeys.read(aead)).isNull()
        val created = tagKeys.obtain(aead)
        assertThat(created).hasLength(NfcTagKeys.KEY_BYTES)
        assertThat(tagKeys.obtain(aead)).isEqualTo(created)
    }
    @Test
    fun `another keyset cannot unseal it`() {
        tagKeys.obtain(aead)
        assertThrows(GeneralSecurityException::class.java) { tagKeys.read(newKeyset().aead()) }
    }
    @Test
    fun `rotation re-seals it under the new key, readable once the old one is pruned`() {
        val keysets = FakeKeysetStore(FakeMasterKeyStore().apply { keys["a"] = MasterKeyPolicy(userAuthRequired = false) })
        keysets.create("a")
        val key = tagKeys.obtain(keysets.read("a").aead())
        val sealedBefore = store.sealed
        tagKeys.reseal(keysets.rotate("a").aead())
        assertThat(store.sealed).isNotEqualTo(sealedBefore)
        assertThat(tagKeys.read(keysets.prune("a").aead())).isEqualTo(key)
    }
    @Test
    fun `the digest hides the nickname and matches only it, under the same key`() {
        val key = tagKeys.obtain(aead)
        val digest = NfcTagDigester.digest(key, NICKNAME)
        assertThat(digest).hasLength(NfcTagDigest.SIZE)
        assertThat(digest.toString(Charsets.ISO_8859_1)).doesNotContain(NICKNAME)
        assertThat(NfcTagDigester.matches(key, digest, NICKNAME)).isTrue()
        assertThat(NfcTagDigester.matches(key, digest, "office")).isFalse()
        assertThat(NfcTagDigester.matches(ByteArray(NfcTagKeys.KEY_BYTES), digest, NICKNAME)).isFalse()
        assertThat(NfcTagDigester.matches(key, digest.copyOf(NfcTagDigest.SIZE - 1), NICKNAME)).isFalse()
    }
    @Test
    fun `two tags of the same config are unlinkable`() {
        val key = tagKeys.obtain(aead)
        assertThat(NfcTagDigester.digest(key, NICKNAME)).isNotEqualTo(NfcTagDigester.digest(key, NICKNAME))
    }
    private companion object {
        const val NICKNAME = "home"
    }
}
