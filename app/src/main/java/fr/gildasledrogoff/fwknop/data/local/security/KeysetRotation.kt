package fr.gildasledrogoff.fwknop.data.local.security

import androidx.room.withTransaction
import com.google.crypto.tink.KeysetHandle
import javax.inject.Inject

class KeysetRotation @Inject constructor(
    private val keysets: KeysetStore,
    private val passphrases: DatabasePassphrases,
    private val exportPassphrases: ExportPassphrases,
    private val nfcTagKeys: NfcTagKeys,
) {
    suspend fun rotate(vault: OpenVault, alias: String): KeysetHandle {
        val rotated = keysets.rotate(alias).aead()
        vault.columns.swap(rotated)
        reencryptRows(vault)
        passphrases.reseal(rotated)
        exportPassphrases.reseal(rotated)
        nfcTagKeys.reseal(rotated)
        return keysets.prune(alias).also { vault.columns.swap(it.aead()) }
    }

    private suspend fun reencryptRows(vault: OpenVault) {
        val cipher = vault.cipher
        vault.database.withTransaction {
            vault.dao.update(vault.dao.getAll().map { cipher.seal(cipher.open(it)) })
        }
    }
}
