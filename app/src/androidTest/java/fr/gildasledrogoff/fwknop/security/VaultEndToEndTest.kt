// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.security
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.models.ConfigColumns
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultEndToEndTest {
    private val testVault = TestVault(InstrumentationRegistry.getInstrumentation().targetContext)
    private val vault = testVault.repository
    private val configs = testVault.configs
    @Before
    @After
    fun wipe() = testVault.wipe()
    @Test
    fun aSavedConfigIsStoredEncryptedAndStillReadable() = runBlocking {
        unlockWithSample()
        val stored = testVault.storedColumn(ConfigColumns.KEY, SampleConfig.NICKNAME)
        assertThat(stored).isNotEqualTo(SampleConfig.KEY)
        val config = (configs.getConfig(Nickname(SampleConfig.NICKNAME)) as DomainResult.Success).data
        assertThat(config.rijndaelKey()).isEqualTo(SampleConfig.KEY)
        assertThat(config.server.host).isEqualTo(SampleConfig.SERVER)
        vault.lock()
        testVault.assertNothingInClearOnDisk(SampleConfig.SECRETS + SampleConfig.NICKNAME)
    }
    @Test
    fun rotationReencryptsEveryValueAndOnlyTheNewKeyRemains() = runBlocking {
        unlockWithSample()
        val before = storedKeyColumn()
        assertThat(vault.rotateKeys()).isEqualTo(DomainResult.Success(Unit))
        assertThat(storedKeyColumn()).isNotEqualTo(before)
        assertThat(testVault.session.open.value!!.keyset.size()).isEqualTo(1)
        val config = (configs.getConfig(Nickname(SampleConfig.NICKNAME)) as DomainResult.Success).data
        assertThat(config.rijndaelKey()).isEqualTo(SampleConfig.KEY)
        vault.lock()
        vault.unlock()
        assertThat(configs.exists(Nickname(SampleConfig.NICKNAME))).isEqualTo(DomainResult.Success(true))
    }
    @Test
    fun aReWrapKeepsTheDatabaseReadable() = runBlocking {
        unlockWithSample()
        assertThat(vault.setLockEnabled(false)).isEqualTo(DomainResult.Success(Unit))
        vault.lock()
        assertThat(vault.unlock()).isEqualTo(DomainResult.Success(Unit))
        assertThat(configs.exists(Nickname(SampleConfig.NICKNAME))).isEqualTo(DomainResult.Success(true))
    }
    @Test
    fun aLockedVaultServesNothing() = runBlocking {
        unlockWithSample()
        vault.lock()
        assertThat(withTimeoutOrNull(LOCKED_WAIT_MS) { configs.getConfig(Nickname(SampleConfig.NICKNAME)) }).isNull()
    }
    private suspend fun unlockWithSample() {
        assertThat(vault.unlock()).isEqualTo(DomainResult.Success(Unit))
        assertThat(configs.saveConfig(SampleConfig.CONFIG, previous = null)).isEqualTo(DomainResult.Success(Unit))
    }
    private fun storedKeyColumn(): String = testVault.storedColumn(ConfigColumns.KEY, SampleConfig.NICKNAME)
    private fun SpaConfig.rijndaelKey() = (crypto.encryption as Encryption.Rijndael).key.value
    private companion object {
        const val LOCKED_WAIT_MS = 1_000L
    }
}
