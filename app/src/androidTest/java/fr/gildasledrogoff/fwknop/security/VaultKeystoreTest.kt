package fr.gildasledrogoff.fwknop.security
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import fr.gildasledrogoff.fwknop.DeviceCredential
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFiles
import fr.gildasledrogoff.fwknop.data.local.security.AndroidKeysetStore
import fr.gildasledrogoff.fwknop.data.local.security.AndroidMasterKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyRewrap
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsVaultMetadataStore
import fr.gildasledrogoff.fwknop.data.local.security.RandomKeyAliasFactory
import fr.gildasledrogoff.fwknop.data.local.security.VaultFailures
import fr.gildasledrogoff.fwknop.data.local.security.VaultJanitor
import fr.gildasledrogoff.fwknop.data.local.security.VaultOpener
import fr.gildasledrogoff.fwknop.data.local.security.VaultWiper
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import com.google.common.truth.Truth.assertThat
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class VaultKeystoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = AndroidMasterKeyStore(context)
    private val keysets = AndroidKeysetStore(context)
    private val metadata = PrefsVaultMetadataStore(context)
    private val files = DatabaseFiles(context)
    private val opener = VaultOpener(
        keys, keysets, metadata, VaultJanitor(keys, keysets), RandomKeyAliasFactory(), files,
        PrefsSealedPassphraseStore(context),
    )
    private val rewrap = MasterKeyRewrap(keys, keysets, metadata, RandomKeyAliasFactory())
    @Before
    @After
    fun wipe() = VaultWiper(keys, metadata, files).wipe()
    @Test
    fun newVaultOpensWithoutAuthAndItsKeyNeverLeavesTheKeystore() {
        val handle = opener.open()
        assertThat(opener.open().equalsKeyset(handle)).isTrue()
        val alias = requireNotNull(metadata.read().masterKeyAlias)
        assertThat(keystoreKey(alias).encoded).isNull()
        assertThat(keyInfo(alias).isUserAuthenticationRequired).isFalse()
    }
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun keyLivesInStrongBoxExactlyWhenTheDeviceHasOne() {
        val strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        opener.open()
        val level = keyInfo(requireNotNull(metadata.read().masterKeyAlias)).securityLevel
        assertThat(level == KeyProperties.SECURITY_LEVEL_STRONGBOX).isEqualTo(strongBox)
    }
    @Test
    fun lockedKeysetOnlyOpensRightAfterAnAuthentication() {
        val handle = opener.open()
        DeviceCredential.unlockLockScreen()
        rewrap.rewrap(handle, lockEnabled = true)
        assertThat(keyInfo(requireNotNull(metadata.read().masterKeyAlias)).isUserAuthenticationRequired).isTrue()
        Thread.sleep(AUTH_WINDOW_MS)
        val refused = runCatching { opener.open() }.exceptionOrNull()
        assertThat(refused).isNotNull()
        assertThat(VaultFailures.classify(requireNotNull(refused))).isEqualTo(VaultError.AuthenticationRequired)
        DeviceCredential.unlockLockScreen()
        assertThat(opener.open().equalsKeyset(handle)).isTrue()
    }
    @Test
    fun disablingTheLockNeedsNoAuthAfterwards() {
        val handle = opener.open()
        DeviceCredential.unlockLockScreen()
        rewrap.rewrap(handle, lockEnabled = true)
        DeviceCredential.unlockLockScreen()
        rewrap.rewrap(opener.open(), lockEnabled = false)
        Thread.sleep(AUTH_WINDOW_MS)
        assertThat(opener.open().equalsKeyset(handle)).isTrue()
        assertThat(keys.aliases()).containsExactly(metadata.read().masterKeyAlias)
    }
    private fun keystoreKey(alias: String): SecretKey =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as SecretKey
    private fun keyInfo(alias: String): KeyInfo {
        val key = keystoreKey(alias)
        return SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
            as KeyInfo
    }
    private companion object {
        const val AUTH_WINDOW_MS = 17_000L
    }
}
