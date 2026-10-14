// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.fakes.*
import fr.gildasledrogoff.fwknop.presentation.state.*
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.DiagnosticRoute
import fr.gildasledrogoff.fwknop.presentation.viewmodel.DiagnosticViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
    private val publicIp = FakePublicIpRepository()
    private val transport = FakeSpaTransportRepository()
    private val packets = FakeSpaPacketRepository()
    private val gpg = FakeGpgRepository()
    private val repository = FakeConfigRepository()
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After
    fun tearDown() = Dispatchers.resetMain()
    private fun viewModel(config: SpaConfig, nickname: String = config.nickname.value): DiagnosticViewModel {
        repository.configs.value = listOf(config)
        val send = SendKnockUseCase(
            ResolveKnockRouteUseCase(publicIp, transport),
            BuildSpaPacketUseCase(packets, gpg),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        )
        return DiagnosticViewModel(
            SavedStateHandle(mapOf(DiagnosticRoute.ARG_NICKNAME to nickname)),
            GetConfigUseCase(repository),
            GetRequiredPromptsUseCase(),
            send,
            InteractionRegistry(),
        )
    }
    private fun DiagnosticState.step(phase: DiagnosticPhase) = steps.single { it.phase == phase }
    @Test
    fun `a successful test details every step`() = runTest {
        val vm = viewModel(sampleConfig(sourceIp = SourceIp.Resolve))
        vm.onEvent(DiagnosticEvent.Run())
        val state = vm.state.value
        assertThat(state.succeeded).isTrue()
        assertThat(state.steps.map { it.status }).containsNoneOf(StepStatus.Pending, StepStatus.Running)
        assertThat(state.step(DiagnosticPhase.SERVER_DNS).detail)
            .isEqualTo(DiagnosticDetail.Server("knock.example.org", listOf("192.0.2.10")))
        assertThat(state.step(DiagnosticPhase.SOURCE_IP).detail).isEqualTo(
            DiagnosticDetail.Source(ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example"))),
        )
        assertThat(state.step(DiagnosticPhase.ADDRESS_FAMILY).detail)
            .isEqualTo(DiagnosticDetail.Family(AddressFamily.IPV4, "192.0.2.10"))
        assertThat(state.step(DiagnosticPhase.PACKET).detail).isEqualTo(DiagnosticDetail.Packet("packet-1".length))
        assertThat(state.step(DiagnosticPhase.TRANSPORT).detail).isEqualTo(
            DiagnosticDetail.Transport(KnockTarget("192.0.2.10", "knock.example.org", 62201, Protocol.UDP)),
        )
        assertThat(transport.sent).hasSize(1)
    }
    @Test
    fun `the external app is never launched`() = runTest {
        val vm = viewModel(sampleConfig().copy(externalApp = ExternalApp.SshUri("me@host")))
        vm.effects.test {
            vm.onEvent(DiagnosticEvent.Run())
            assertThat(vm.state.value.succeeded).isTrue()
            expectNoEvents()
        }
    }
    @Test
    fun `a transport failure keeps the target and marks only that step`() = runTest {
        transport.sendError = KnockError.Transport(java.io.IOException("unreachable"))
        val vm = viewModel(sampleConfig())
        vm.onEvent(DiagnosticEvent.Run())
        val state = vm.state.value
        assertThat(state.finished).isTrue()
        assertThat(state.succeeded).isFalse()
        val transportStep = state.step(DiagnosticPhase.TRANSPORT)
        assertThat(transportStep.status)
            .isEqualTo(StepStatus.Failed(UiError.Detailed(R.string.error_transport, "unreachable")))
        assertThat(transportStep.detail).isInstanceOf(DiagnosticDetail.Transport::class.java)
        assertThat(state.step(DiagnosticPhase.PACKET).status).isEqualTo(StepStatus.Done)
    }
    @Test
    fun `an unresolved source ip fails that step and skips the rest`() = runTest {
        publicIp.default = DomainResult.Failure(KnockError.SourceIpUnresolved("timeout"))
        val vm = viewModel(sampleConfig(sourceIp = SourceIp.Resolve))
        vm.onEvent(DiagnosticEvent.Run())
        val state = vm.state.value
        assertThat(state.step(DiagnosticPhase.SERVER_DNS).status).isEqualTo(StepStatus.Done)
        assertThat(state.step(DiagnosticPhase.SOURCE_IP).status)
            .isEqualTo(StepStatus.Failed(UiError.Detailed(R.string.error_source_ip_unresolved, "timeout")))
        listOf(DiagnosticPhase.ADDRESS_FAMILY, DiagnosticPhase.PACKET, DiagnosticPhase.TRANSPORT).forEach {
            assertThat(state.step(it).status).isEqualTo(StepStatus.Skipped)
        }
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `prompted key and source ip are asked before running`() = runTest {
        val config = sampleConfig(sourceIp = SourceIp.Prompt, encryption = Encryption.Rijndael(SecretKey("", false)))
        val vm = viewModel(config)
        assertThat(vm.state.value.needsKey).isTrue()
        assertThat(vm.state.value.needsSourceIp).isTrue()
        vm.onEvent(DiagnosticEvent.Run(key = "typed", sourceIp = "not an ip"))
        assertThat(vm.state.value.sourceIpInvalid).isTrue()
        vm.onEvent(DiagnosticEvent.Run(key = "typed", sourceIp = "2001:db8::77"))
        assertThat(vm.state.value.sourceIpInvalid).isTrue()
        assertThat(transport.sent).isEmpty()
        vm.onEvent(DiagnosticEvent.Run(key = "typed", sourceIp = " 198.51.100.77 "))
        assertThat(vm.state.value.succeeded).isTrue()
        assertThat(packets.built.single().key).isEqualTo(SecretKey("typed", false))
        assertThat(packets.built.single().sourceIp).isEqualTo("198.51.100.77")
    }
    @Test
    fun `stored key is not overridden by an empty field`() = runTest {
        val vm = viewModel(sampleConfig())
        assertThat(vm.state.value.needsKey).isFalse()
        vm.onEvent(DiagnosticEvent.Run(key = "ignored"))
        assertThat(packets.built.single().key).isEqualTo(SecretKey("secret", false))
    }
    @Test
    fun `gpg interaction is launched then the test resumes`() = runTest {
        gpg.encryptResult = DomainResult.Failure(GpgError.InteractionRequired(InteractionId(7)))
        val vm = viewModel(sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2))))
        vm.effects.test {
            vm.onEvent(DiagnosticEvent.Run())
            assertThat(awaitItem()).isEqualTo(DiagnosticEffect.LaunchInteraction(InteractionId(7)))
            assertThat(vm.state.value.running).isTrue()
            gpg.encryptResult = DomainResult.Success("cipher")
            vm.onEvent(DiagnosticEvent.InteractionFinished(InteractionId(7), completed = true, data = null))
        }
        assertThat(vm.state.value.succeeded).isTrue()
        assertThat(gpg.encryptCalls.last().second).isEqualTo(InteractionId(7))
    }
    @Test
    fun `cancelling a pending interaction resets the steps`() = runTest {
        gpg.encryptResult = DomainResult.Failure(GpgError.InteractionRequired(InteractionId(7)))
        val vm = viewModel(sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2))))
        vm.onEvent(DiagnosticEvent.Run())
        vm.onEvent(DiagnosticEvent.InteractionFinished(InteractionId(7), completed = false, data = null))
        val state = vm.state.value
        assertThat(state.running).isFalse()
        assertThat(state.steps.map { it.status }.distinct()).containsExactly(StepStatus.Pending)
    }
    @Test
    fun `unknown configuration reports an error`() = runTest {
        val vm = viewModel(sampleConfig(), nickname = "missing")
        assertThat(vm.state.value.error).isEqualTo(UiError.Message(R.string.error_config_not_found))
    }
}
