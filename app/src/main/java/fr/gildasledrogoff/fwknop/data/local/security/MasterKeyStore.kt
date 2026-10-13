package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

data class MasterKeyPolicy(val userAuthRequired: Boolean)
interface MasterKeyStore {
    fun generate(alias: String, policy: MasterKeyPolicy)
    fun exists(alias: String): Boolean
    fun delete(alias: String)
    fun aliases(): Set<String>
}

@Singleton
class AndroidMasterKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MasterKeyStore {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }
    override fun generate(alias: String, policy: MasterKeyPolicy) {
        if (hasStrongBox()) {
            try {
                return generate(spec(alias, policy, strongBox = true))
            } catch (_: StrongBoxUnavailableException) {
            }
        }
        generate(spec(alias, policy, strongBox = false))
    }

    override fun exists(alias: String): Boolean = keyStore.containsAlias(alias)
    override fun delete(alias: String) = keyStore.deleteEntry(alias)
    override fun aliases(): Set<String> =
        keyStore.aliases().toList().filter(KeyAliases::isOurs).toSet()

    private fun generate(spec: KeyGenParameterSpec) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }
            .generateKey()
    }

    private fun hasStrongBox(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun spec(
        alias: String,
        policy: MasterKeyPolicy,
        strongBox: Boolean
    ): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(
                    true
                )
            }
            .apply { if (policy.userAuthRequired) requireUserAuthentication() }
            .build()

    private fun KeyGenParameterSpec.Builder.requireUserAuthentication() {
        setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_BITS = 256
        const val AUTH_VALIDITY_SECONDS = 15
    }
}
