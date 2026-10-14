// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.ExportBackupUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.OpenBackupFileUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.RestoreBackupUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.FakeNfcTagRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository

private class FakeBackupRepository : BackupRepository {
    private val sealed = mutableMapOf<String, Backup>()
    override suspend fun seal(backup: Backup, passphrase: BackupPassphrase): ByteArray {
        val key = String(passphrase.chars)
        sealed[key] = backup
        return key.toByteArray()
    }
    override suspend fun open(file: ByteArray, passphrase: BackupPassphrase): DomainResult<Backup, BackupError> {
        val backup = sealed[String(file)] ?: return DomainResult.Failure(BackupError.NotABackup)
        return if (String(file) == String(passphrase.chars)) {
            DomainResult.Success(backup)
        } else {
            DomainResult.Failure(BackupError.WrongPassphrase)
        }
    }
}
class BackupUseCasesTest {
    private val backups = FakeBackupRepository()
    private val passphrase = "correct horse battery"
    @Test
    fun `export then import restores configs and settings, replacing the current ones`() = runTest {
        val source = FakeConfigRepository(listOf(sampleConfig("home"), sampleConfig("office")))
        val sourceSettings = FakeSettingsRepository(AppSettings(dnsLookupEnabled = false, autoLockDelay = AutoLockDelay.MINUTE_1))
        val sourceTags = FakeNfcTagRepository()
        val tag = sourceTags.digest(Nickname("home"))
        val exported = ExportBackupUseCase(source, sourceSettings, backups, sourceTags)(BackupPassphrase(passphrase.toCharArray()))
        val file = (exported as DomainResult.Success).data
        assertThat(file.configCount).isEqualTo(2)
        val target = FakeConfigRepository(listOf(sampleConfig("stale")))
        val targetSettings = FakeSettingsRepository(AppSettings(helpShown = true))
        val opened = OpenBackupFileUseCase(backups)(file.file, BackupPassphrase(passphrase.toCharArray()))
        val targetTags = FakeNfcTagRepository()
        val imported = RestoreBackupUseCase(target, targetSettings, targetTags)((opened as DomainResult.Success).data)
        assertThat(imported).isEqualTo(DomainResult.Success(2))
        assertThat(target.configs.value).containsExactly(sampleConfig("home"), sampleConfig("office"))
        assertThat(targetSettings.state.value)
            .isEqualTo(AppSettings(dnsLookupEnabled = false, autoLockDelay = AutoLockDelay.MINUTE_1, helpShown = true))
        assertThat(targetTags.match(listOf(tag), listOf(Nickname("office"), Nickname("home"))))
            .containsExactly(Nickname("home"))
    }
    @Test
    fun `restoring a backup keeps this device's appearance`() = runTest {
        val backup = Backup(settings = AppSettings(themeMode = ThemeMode.LIGHT, dynamicColor = true), configs = emptyList())
        val settings = FakeSettingsRepository(AppSettings(themeMode = ThemeMode.DARK))
        RestoreBackupUseCase(FakeConfigRepository(), settings, FakeNfcTagRepository())(backup)
        assertThat(settings.state.value.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(settings.state.value.dynamicColor).isFalse()
    }
    @Test
    fun `a short passphrase is refused and wiped`() = runTest {
        val chars = "a".toCharArray()
        val result = ExportBackupUseCase(FakeConfigRepository(), FakeSettingsRepository(), backups, FakeNfcTagRepository())(BackupPassphrase(chars))
        assertThat(result).isEqualTo(DomainResult.Failure(BackupError.PassphraseTooShort))
        assertThat(chars.all { it == '\u0000' }).isTrue()
    }
    @Test
    fun `whitespace does not count towards the minimum length`() {
        assertThat(BackupPassphrase.isLongEnough("  a  ")).isFalse()
        assertThat(BackupPassphrase.isLongEnough("a b")).isTrue()
    }
    @Test
    fun `a wrong passphrase is reported and wiped`() = runTest {
        val file = backups.seal(Backup(AppSettings(), listOf(sampleConfig("new"))), BackupPassphrase(passphrase.toCharArray()))
        val chars = "not the right passphrase".toCharArray()
        val result = OpenBackupFileUseCase(backups)(file, BackupPassphrase(chars))
        assertThat(result).isEqualTo(DomainResult.Failure(BackupError.WrongPassphrase))
        assertThat(chars.all { it == '\u0000' }).isTrue()
    }
}
