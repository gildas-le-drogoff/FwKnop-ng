// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.security
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFactory
import fr.gildasledrogoff.fwknop.data.local.database.FwknopDatabase
import fr.gildasledrogoff.fwknop.data.local.security.*
import fr.gildasledrogoff.fwknop.data.repository.ConfigRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.VaultRepositoryImpl
import fr.gildasledrogoff.fwknop.utils.dispatcher.DefaultDispatcherProvider
import android.content.Context
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFiles

class TestVault(context: Context) {
    val files = DatabaseFiles(context)
    private val keys = AndroidMasterKeyStore(context)
    private val keysets = AndroidKeysetStore(context)
    private val metadata = PrefsVaultMetadataStore(context)
    private val aliases = RandomKeyAliasFactory()
    private val sealedPassphrase = PrefsSealedPassphraseStore(context)
    private val passphrases = DatabasePassphrases(sealedPassphrase)
    private val wiper = VaultWiper(keys, metadata, files)
    val session = VaultSession()
    val configs = ConfigRepositoryImpl(session)
    val repository = VaultRepositoryImpl(
        opener = VaultOpener(keys, keysets, metadata, VaultJanitor(keys, keysets), aliases, files, sealedPassphrase),
        databases = DatabaseFactory(context),
        passphrases = passphrases,
        rewrap = MasterKeyRewrap(keys, keysets, metadata, aliases),
        rotation = KeysetRotation(
            keysets,
            passphrases,
            ExportPassphrases(PrefsSealedExportPassphraseStore(context)),
            NfcTagKeys(PrefsSealedNfcTagKeyStore(context)),
        ),
        wiper = wiper,
        metadata = metadata,
        session = session,
        device = DeviceCredential(context),
        dispatchers = DefaultDispatcherProvider(),
    )
    fun wipe() {
        session.close()
        wiper.wipe()
    }
    fun storedColumn(column: String, nickname: String): String =
        checkNotNull(session.open.value).database.openHelper.readableDatabase
            .query("SELECT `$column` FROM configs WHERE NICK_NAME = ?", arrayOf(nickname))
            .use { it.moveToFirst(); it.getString(0) }
    fun assertNothingInClearOnDisk(clear: List<String>) {
        val stored = files.file.parentFile!!.listFiles { f -> f.name.startsWith(FwknopDatabase.NAME) }.orEmpty()
            .joinToString("") { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertThat(stored).isNotEmpty()
        assertThat(files.hasPlainSqliteHeader()).isFalse()
        clear.forEach { assertThat(stored).doesNotContain(it) }
    }
}
