// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.fakes.*
import fr.gildasledrogoff.fwknop.presentation.state.AutoKnockEvent
import fr.gildasledrogoff.fwknop.presentation.viewmodel.AutoKnockViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccessStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
    private val repository = FakeAutoKnockRepository()
    private val configs = FakeConfigRepository(
        listOf(sampleConfig("home"), sampleConfig("ask-ip", sourceIp = SourceIp.Prompt))
    )
    private val wifi = FakeWifiNetworkRepository()
    private val location = FakeLocationAccess()
    private val monitor = FakeAutoKnockMonitor()
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After
    fun tearDown() = Dispatchers.resetMain()
    private fun viewModel() = AutoKnockViewModel(
        ObserveAutoKnockSettingsUseCase(repository),
        ObserveConfigsUseCase(configs),
        SaveAutoKnockSettingsUseCase(repository),
        IsAutoKnockableUseCase(GetRequiredPromptsUseCase()),
        GetCurrentSsidUseCase(wifi),
        location,
        monitor,
    )
    @Test
    fun `off by default`() = runTest {
        assertThat(viewModel().state.value.settings.enabled).isFalse()
        assertThat(AutoKnockSettings().enabled).isFalse()
    }
    @Test
    fun `turning it on shows the warning and saves nothing`() = runTest {
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.EnableRequested(true))
        assertThat(vm.state.value.confirmingEnable).isTrue()
        assertThat(repository.state.value.enabled).isFalse()
    }
    @Test
    fun `dismissing the warning leaves it off`() = runTest {
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.EnableRequested(true))
        vm.onEvent(AutoKnockEvent.EnableDismissed)
        assertThat(vm.state.value.confirmingEnable).isFalse()
        assertThat(repository.state.value.enabled).isFalse()
    }
    @Test
    fun `only the confirmation turns it on`() = runTest {
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.EnableConfirmed)
        assertThat(repository.state.value.enabled).isFalse()
        vm.onEvent(AutoKnockEvent.EnableRequested(true))
        vm.onEvent(AutoKnockEvent.EnableConfirmed)
        assertThat(repository.state.value.enabled).isTrue()
        assertThat(vm.state.value.confirmingEnable).isFalse()
    }
    @Test
    fun `turning it off needs no confirmation`() = runTest {
        repository.state.value = AutoKnockSettings(enabled = true)
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.EnableRequested(false))
        assertThat(repository.state.value.enabled).isFalse()
        assertThat(vm.state.value.confirmingEnable).isFalse()
    }
    @Test
    fun `only offers configs that knock without input`() = runTest {
        assertThat(viewModel().state.value.candidates).containsExactly("home")
    }
    @Test
    fun `flags a saved config that can no longer knock unattended`() = runTest {
        repository.state.value = AutoKnockSettings(enabled = true, nickname = "ask-ip")
        assertThat(viewModel().state.value.configUnavailable).isTrue()
    }
    @Test
    fun `successive network edits build on each other`() = runTest {
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.SsidAdded("Maison"))
        vm.onEvent(AutoKnockEvent.SsidAdded("\"Bureau\""))
        vm.onEvent(AutoKnockEvent.SsidAdded("  "))
        assertThat(repository.state.value.trustedSsids).containsExactly("Bureau", "Maison")
        vm.onEvent(AutoKnockEvent.SsidRemoved("Maison"))
        assertThat(repository.state.value.trustedSsids).containsExactly("Bureau")
    }
    @Test
    fun `adds the current network, or says why it cannot`() = runTest {
        val vm = viewModel()
        vm.onEvent(AutoKnockEvent.AddCurrentNetwork)
        assertThat(vm.state.value.currentSsidUnavailable).isTrue()
        wifi.current = "Maison"
        vm.onEvent(AutoKnockEvent.AddCurrentNetwork)
        assertThat(vm.state.value.currentSsidUnavailable).isFalse()
        assertThat(repository.state.value.trustedSsids).containsExactly("Maison")
    }
    @Test
    fun `a permission change refreshes the status and the monitoring`() = runTest {
        location.status = LocationAccessStatus.BACKGROUND_MISSING
        val vm = viewModel()
        assertThat(vm.state.value.location).isEqualTo(LocationAccessStatus.BACKGROUND_MISSING)
        location.status = LocationAccessStatus.GRANTED
        vm.onEvent(AutoKnockEvent.PermissionsChanged)
        assertThat(vm.state.value.location).isEqualTo(LocationAccessStatus.GRANTED)
        assertThat(monitor.refreshes).isEqualTo(1)
    }
}
