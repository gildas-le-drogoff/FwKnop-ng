package fr.gildasledrogoff.fwknop.domain
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.uniqueNickname
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository
import fr.gildasledrogoff.fwknop.domain.usecase.AddConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.DuplicateConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ExportConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.OpenBackupFileUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.RestoreBackupUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.FakeNfcTagRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
private class InMemoryBackupRepository : BackupRepository {
    private val sealed = mutableListOf<Backup>()
    override suspend fun seal(backup: Backup, passphrase: BackupPassphrase): ByteArray {
        sealed += backup
        return byteArrayOf((sealed.size - 1).toByte())
    }
    override suspend fun open(file: ByteArray, passphrase: BackupPassphrase): DomainResult<Backup, BackupError> =
        DomainResult.Success(sealed[file.single().toInt()])
}
class ConfigTransferUseCasesTest {
    private val backups = InMemoryBackupRepository()
    private fun passphrase() = BackupPassphrase("correct horse battery".toCharArray())
    @Test
    fun `a single config is exported, without the settings`() = runTest {
        val configs = FakeConfigRepository(listOf(sampleConfig("home"), sampleConfig("office")))
        val exported = ExportConfigUseCase(configs, backups)(Nickname("office"), passphrase())
        val file = (exported as DomainResult.Success).data
        assertThat(file.configCount).isEqualTo(1)
        assertThat(OpenBackupFileUseCase(backups)(file.file, passphrase()))
            .isEqualTo(DomainResult.Success(Backup(settings = null, configs = listOf(sampleConfig("office")))))
    }
    @Test
    fun `a single config export cannot restore the whole configuration`() = runTest {
        val configs = FakeConfigRepository(listOf(sampleConfig("kept")))
        val result = RestoreBackupUseCase(configs, FakeSettingsRepository(), FakeNfcTagRepository())(
            Backup(settings = null, configs = listOf(sampleConfig("home"))),
        )
        assertThat(result).isEqualTo(DomainResult.Failure(BackupError.PartialBackup))
        assertThat(configs.configs.value).containsExactly(sampleConfig("kept"))
    }
    @Test
    fun `a config is added under the chosen nickname, never over an existing one`() = runTest {
        val existingHome = sampleConfig("home", sourceIp = SourceIp.Resolve)
        val configs = FakeConfigRepository(listOf(existingHome))
        val add = AddConfigUseCase(configs)
        assertThat(add(sampleConfig("home"), "home")).isEqualTo(DomainResult.Failure(ConfigError.NicknameTaken))
        assertThat(add(sampleConfig("home"), "  ")).isEqualTo(DomainResult.Failure(ConfigError.NicknameBlank))
        assertThat(add(sampleConfig("home"), " home (2) ")).isEqualTo(DomainResult.Success(sampleConfig("home (2)")))
        assertThat(configs.configs.value).containsExactly(existingHome, sampleConfig("home (2)"))
    }
    @Test
    fun `a duplicate copies the config under a new nickname`() = runTest {
        val home = sampleConfig("home", sourceIp = SourceIp.Resolve)
        val configs = FakeConfigRepository(listOf(home))
        val duplicate = DuplicateConfigUseCase(configs, AddConfigUseCase(configs))
        assertThat(duplicate(Nickname("home"), "home")).isEqualTo(DomainResult.Failure(ConfigError.NicknameTaken))
        assertThat(duplicate(Nickname("gone"), "copy")).isEqualTo(DomainResult.Failure(ConfigError.NotFound))
        assertThat(duplicate(Nickname("home"), "office")).isEqualTo(DomainResult.Success(home.copy(nickname = Nickname("office"))))
        assertThat(configs.configs.value).containsExactly(home, home.copy(nickname = Nickname("office")))
    }
    @Test
    fun `the suggested nickname skips the used ones`() {
        assertThat(uniqueNickname("home", setOf("home"))).isEqualTo("home (2)")
        assertThat(uniqueNickname("home", setOf("home", "home (2)"))).isEqualTo("home (3)")
        assertThat(uniqueNickname("lab", setOf("home"))).isEqualTo("lab")
    }
    @Test
    fun `a full backup is read as such`() = runTest {
        val file = backups.seal(Backup(AppSettings(), listOf(sampleConfig("home"))), passphrase())
        assertThat(OpenBackupFileUseCase(backups)(file, passphrase()))
            .isEqualTo(DomainResult.Success(Backup(AppSettings(), listOf(sampleConfig("home")))))
    }
}
