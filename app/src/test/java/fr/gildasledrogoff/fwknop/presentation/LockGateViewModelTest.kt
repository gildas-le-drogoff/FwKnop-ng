// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResetVaultUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.UnlockVaultUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeVaultRepository
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.LockGateEffect
import fr.gildasledrogoff.fwknop.presentation.state.LockGateEvent
import fr.gildasledrogoff.fwknop.presentation.state.LockGateState
import fr.gildasledrogoff.fwknop.presentation.viewmodel.LockGateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class LockGateViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After
    fun tearDown() = Dispatchers.resetMain()
    private fun gate(vault: FakeVaultRepository) =
        LockGateViewModel(ObserveVaultStatusUseCase(vault), UnlockVaultUseCase(vault), ResetVaultUseCase(vault))
    @Test
    fun `without the lock the vault opens without prompting`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = false)
        val gate = gate(vault)
        assertThat(gate.state.value).isEqualTo(LockGateState.Unlocked)
        assertThat(vault.unlockCalls).isEqualTo(1)
    }
    @Test
    fun `with the lock nothing opens before the user authenticates`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = true)
        val gate = gate(vault)
        gate.effects.test { assertThat(awaitItem()).isEqualTo(LockGateEffect.Authenticate) }
        assertThat(gate.state.value).isEqualTo(LockGateState.Locked())
        assertThat(vault.unlockCalls).isEqualTo(0)
    }
    @Test
    fun `a cancelled or failed prompt keeps the vault closed`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = true)
        val gate = gate(vault)
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Cancelled))
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Error(7)))
        assertThat(gate.state.value).isInstanceOf(LockGateState.Locked::class.java)
        assertThat(vault.unlockCalls).isEqualTo(0)
    }
    @Test
    fun `a successful prompt opens the vault`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = true)
        val gate = gate(vault)
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Success))
        assertThat(gate.state.value).isEqualTo(LockGateState.Unlocked)
    }
    @Test
    fun `a key requiring auth behind lock-disabled metadata prompts once and never loops`() = runTest {
        val vault = FakeVaultRepository(
            lockEnabled = false,
            unlockResult = DomainResult.Failure(VaultError.AuthenticationRequired),
        )
        val gate = gate(vault)
        gate.effects.test { assertThat(awaitItem()).isEqualTo(LockGateEffect.Authenticate) }
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Success))
        assertThat(gate.state.value).isInstanceOf(LockGateState.Locked::class.java)
        assertThat((gate.state.value as LockGateState.Locked).error).isNotNull()
        assertThat(vault.unlockCalls).isEqualTo(2)
    }
    @Test
    fun `a vault locking under an open gate closes the gate`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = true)
        val gate = gate(vault)
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Success))
        vault.lock()
        assertThat(gate.state.value).isEqualTo(LockGateState.Locked())
    }
    @Test
    fun `a lost key asks for a reset, which starts a fresh unlocked vault`() = runTest {
        val vault = FakeVaultRepository(
            lockEnabled = true,
            unlockResult = DomainResult.Failure(VaultError.KeyInvalidated),
        )
        val gate = gate(vault)
        gate.onEvent(LockGateEvent.AuthCompleted(AuthResult.Success))
        assertThat(gate.state.value).isEqualTo(LockGateState.Invalidated)
        vault.unlockResult = DomainResult.Success(Unit)
        gate.onEvent(LockGateEvent.ResetConfirmed)
        assertThat(gate.state.value).isEqualTo(LockGateState.Unlocked)
        assertThat(vault.status.value.lockEnabled).isFalse()
    }
    @Test
    fun `reset is ignored unless the key is lost`() = runTest {
        val vault = FakeVaultRepository(lockEnabled = true)
        val gate = gate(vault)
        gate.onEvent(LockGateEvent.ResetConfirmed)
        assertThat(vault.status.value.lockEnabled).isTrue()
    }
}
