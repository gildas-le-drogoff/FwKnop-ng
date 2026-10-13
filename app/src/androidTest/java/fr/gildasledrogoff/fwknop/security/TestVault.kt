package fr.gildasledrogoff.fwknop.security
import android.content.Context
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFactory
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFiles
import fr.gildasledrogoff.fwknop.data.local.database.FwknopDatabase
import fr.gildasledrogoff.fwknop.data.local.security.AndroidKeysetStore
import fr.gildasledrogoff.fwknop.data.local.security.AndroidMasterKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.DatabasePassphrases
import fr.gildasledrogoff.fwknop.data.local.security.DeviceCredential
import fr.gildasledrogoff.fwknop.data.local.security.KeysetRotation
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyRewrap
import fr.gildasledrogoff.fwknop.data.local.security.ExportPassphrases
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagKeys
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedExportPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedNfcTagKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsVaultMetadataStore
import fr.gildasledrogoff.fwknop.data.local.security.RandomKeyAliasFactory
import fr.gildasledrogoff.fwknop.data.local.security.VaultJanitor
import fr.gildasledrogoff.fwknop.data.local.security.VaultOpener
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.local.security.VaultWiper
import fr.gildasledrogoff.fwknop.data.repository.ConfigRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.VaultRepositoryImpl
import fr.gildasledrogoff.fwknop.utils.dispatcher.DefaultDispatcherProvider
import com.google.common.truth.Truth.assertThat
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
