package fr.gildasledrogoff.fwknop.presentation
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.AutoLockDelay
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.usecase.LockVaultUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.FakeVaultRepository
import fr.gildasledrogoff.fwknop.presentation.service.AutoLock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockTest {
    private var now = 0L
    private val vault = FakeVaultRepository(lockEnabled = true)
    private fun TestScope.autoLock(delay: AutoLockDelay) = AutoLock(
        observeVault = ObserveVaultStatusUseCase(vault),
        observeSettings = ObserveSettingsUseCase(FakeSettingsRepository(AppSettings(autoLockDelay = delay))),
        lockVault = LockVaultUseCase(vault),
        clock = { now },
        scope = backgroundScope,
    ).also {
        runCurrent()
        vault.lock()
        vault.statusFlow.value = vault.statusFlow.value.copy(state = VaultState.UNLOCKED)
    }
    @Test
    fun `returning after the delay locks before anything is shown`() = runTest {
        val autoLock = autoLock(AutoLockDelay.MINUTE_1)
        autoLock.onBackground()
        now += 60_000
        autoLock.lockIfExpired()
        assertThat(vault.status.value.state).isEqualTo(VaultState.LOCKED)
    }
    @Test
    fun `returning before the delay keeps the vault open`() = runTest {
        val autoLock = autoLock(AutoLockDelay.MINUTE_1)
        autoLock.onBackground()
        now += 59_999
        autoLock.onForeground()
        advanceTimeBy(120_000)
        assertThat(vault.status.value.state).isEqualTo(VaultState.UNLOCKED)
    }
    @Test
    fun `the timer locks while the app stays in background`() = runTest {
        val autoLock = autoLock(AutoLockDelay.SECONDS_30)
        autoLock.onBackground()
        advanceTimeBy(30_001)
        assertThat(vault.status.value.state).isEqualTo(VaultState.LOCKED)
    }
    @Test
    fun `never only locks when the process restarts`() = runTest {
        val autoLock = autoLock(AutoLockDelay.NEVER)
        autoLock.onBackground()
        now += 24 * 3_600_000L
        advanceTimeBy(24 * 3_600_000L)
        autoLock.onForeground()
        assertThat(vault.status.value.state).isEqualTo(VaultState.UNLOCKED)
    }
    @Test
    fun `without the app lock nothing is locked`() = runTest {
        val autoLock = autoLock(AutoLockDelay.SECONDS_30)
        vault.statusFlow.value = vault.statusFlow.value.copy(lockEnabled = false)
        autoLock.onBackground()
        now += 60_000
        advanceTimeBy(60_000)
        autoLock.onForeground()
        assertThat(vault.status.value.state).isEqualTo(VaultState.UNLOCKED)
    }
}
