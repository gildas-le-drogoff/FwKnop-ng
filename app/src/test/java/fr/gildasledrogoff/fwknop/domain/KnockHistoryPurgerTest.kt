// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.KnockHistoryRetention
import fr.gildasledrogoff.fwknop.domain.usecase.KnockHistoryPurger
import fr.gildasledrogoff.fwknop.fakes.FakeKnockHistoryRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.FakeVaultRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class KnockHistoryPurgerTest {
    private val vault = FakeVaultRepository()
    private val settings = FakeSettingsRepository()
    private val history = FakeKnockHistoryRepository()
    private fun TestScope.start() {
        KnockHistoryPurger(vault, settings, history).start(backgroundScope)
        runCurrent()
    }
    @Test
    fun nothingIsPurgedWhileLocked() = runTest {
        start()
        advanceTimeBy(KnockHistoryPurger.INTERVAL * 3)
        assertThat(history.purges).isEqualTo(0)
    }
    @Test
    fun unlockingPurgesThenEveryIntervalUntilLocked() = runTest {
        start()
        vault.unlock()
        runCurrent()
        assertThat(history.purges).isEqualTo(1)
        advanceTimeBy(KnockHistoryPurger.INTERVAL)
        runCurrent()
        assertThat(history.purges).isEqualTo(2)
        vault.lock()
        advanceTimeBy(KnockHistoryPurger.INTERVAL * 3)
        assertThat(history.purges).isEqualTo(2)
    }
    @Test
    fun aRetentionChangePurgesImmediately() = runTest {
        start()
        vault.unlock()
        runCurrent()
        settings.state.value = settings.state.value.copy(knockHistoryRetention = KnockHistoryRetention.DAYS_7)
        runCurrent()
        assertThat(history.purges).isEqualTo(2)
    }
    @Test
    fun unrelatedSettingsChangesDoNotPurge() = runTest {
        start()
        vault.unlock()
        runCurrent()
        settings.state.value = settings.state.value.copy(dynamicColor = true)
        runCurrent()
        assertThat(history.purges).isEqualTo(1)
    }
}
