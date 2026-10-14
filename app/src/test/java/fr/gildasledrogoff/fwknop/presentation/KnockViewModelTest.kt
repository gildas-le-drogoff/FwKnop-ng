package fr.gildasledrogoff.fwknop.presentation
import app.cash.turbine.test
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetExternalAppDelayUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetNfcKnockConfigUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.GetRequiredPromptsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.NeedsTimerNotificationUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ParseQrClientIpUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ScheduleKnockTimerUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeKnockHistoryRepository
import fr.gildasledrogoff.fwknop.fakes.FakeConfigRepository
import fr.gildasledrogoff.fwknop.fakes.FakeGpgRepository
import fr.gildasledrogoff.fwknop.fakes.FakeKnockTimerScheduler
import fr.gildasledrogoff.fwknop.fakes.FakeNfcTagRepository
import fr.gildasledrogoff.fwknop.fakes.FakePortProbeRepository
import fr.gildasledrogoff.fwknop.domain.model.PortCheck
import fr.gildasledrogoff.fwknop.domain.usecase.VerifyPortAccessUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeRuntimePermissions
import fr.gildasledrogoff.fwknop.fakes.FakePublicIpRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaPacketRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaTransportRepository
import fr.gildasledrogoff.fwknop.fakes.FakeVaultRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.presentation.state.KnockEffect
import fr.gildasledrogoff.fwknop.presentation.state.KnockEvent
import fr.gildasledrogoff.fwknop.presentation.state.KnockPermission
import fr.gildasledrogoff.fwknop.presentation.state.SentNotice
import fr.gildasledrogoff.fwknop.presentation.state.KnockState
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.viewmodel.KnockViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
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
class KnockViewModelTest {
    private val transport = FakeSpaTransportRepository()
    private val packets = FakeSpaPacketRepository()
    private val settings = FakeSettingsRepository(AppSettings(nfcKnockEnabled = false))
    private val nfcTags = FakeNfcTagRepository()
    private val vault = FakeVaultRepository(lockEnabled = true)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After
    fun tearDown() = Dispatchers.resetMain()
    private val permissions = FakeRuntimePermissions()
    private val scheduler = FakeKnockTimerScheduler()
    private var repository = FakeConfigRepository()
    private val portProbe = FakePortProbeRepository { _, _ -> true }
    private fun viewModel(vararg configs: SpaConfig): KnockViewModel {
        repository.configs.value = configs.toList()
        val send = SendKnockUseCase(
            ResolveKnockRouteUseCase(FakePublicIpRepository(), transport),
            BuildSpaPacketUseCase(packets, FakeGpgRepository()),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        )
        return KnockViewModel(
            GetConfigUseCase(repository),
            GetNfcKnockConfigUseCase(settings, repository, nfcTags),
            GetRequiredPromptsUseCase(),
            ParseQrClientIpUseCase(),
            send,
            ScheduleKnockTimerUseCase(settings, scheduler),
            NeedsTimerNotificationUseCase(settings),
            InteractionRegistry(),
            permissions,
            ObserveVaultStatusUseCase(vault),
            externalAppDelay = GetExternalAppDelayUseCase(settings),
            verifyPorts = VerifyPortAccessUseCase(settings, transport, portProbe),
        )
    }
    private fun KnockViewModel.scan(digests: List<NfcTagDigest>, auth: AuthResult = AuthResult.Success) {
        onEvent(KnockEvent.KnockFromNfc(digests))
        onEvent(KnockEvent.NfcAuthCompleted(auth))
    }
    private suspend fun KnockViewModel.scan(vararg nicknames: String) = scan(nicknames.map { nfcTags.digest(Nickname(it)) })
    @Test
    fun `local network permission is explained then requested only for a lan server`() = runTest {
        transport.serverOnLocalNetwork = true
        transport.localNetworkGranted = false
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(KnockState.ExplainPermission("home", KnockPermission.LOCAL_NETWORK))
        assertThat(transport.sent).isEmpty()
        vm.onEvent(KnockEvent.PermissionPromptLaunched)
        assertThat(vm.state.value).isEqualTo(KnockState.AwaitingPermission(KnockPermission.LOCAL_NETWORK))
        transport.localNetworkGranted = true
        vm.onEvent(KnockEvent.PermissionResult(granted = true, permanentlyDenied = false))
        assertThat(transport.sent).hasSize(1)
    }
    @Test
    fun `local network refused for good goes straight to the app settings`() = runTest {
        transport.serverOnLocalNetwork = true
        transport.localNetworkGranted = false
        permissions.snapshotFlow.value = permissions.snapshotFlow.value.copy(
            localNetwork = PermissionStatus.DENIED_PERMANENTLY,
        )
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(
            KnockState.Failed(UiError.Message(R.string.error_local_network_blocked), fixInAppSettings = true)
        )
    }
    @Test
    fun `refused local network permission fails with the settings shortcut`() = runTest {
        transport.serverOnLocalNetwork = true
        transport.localNetworkGranted = false
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.Knock("home"))
        vm.onEvent(KnockEvent.PermissionPromptLaunched)
        vm.onEvent(KnockEvent.PermissionResult(granted = false, permanentlyDenied = false))
        assertThat(vm.state.value).isEqualTo(
            KnockState.Failed(UiError.Message(R.string.error_local_network_blocked), fixInAppSettings = true)
        )
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `notifications are explained at the first timed knock, then sent either way`() = runTest {
        permissions.snapshotFlow.value = permissions.snapshotFlow.value.copy(notifications = PermissionStatus.DENIED)
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(KnockState.ExplainPermission("home", KnockPermission.NOTIFICATIONS))
        vm.onEvent(KnockEvent.PermissionDeclined)
        assertThat(transport.sent).hasSize(1)
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(transport.sent).hasSize(2)
    }
    @Test
    fun `keep open without notifications is refused and reported`() = runTest {
        scheduler.notificationsAllowed = false
        val keepOpen = sampleConfig(access = AccessRule.OpenPort(PortAccess("tcp/22", 60, keepOpen = true)))
        val vm = viewModel(keepOpen)
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE, SentNotice.KEEP_OPEN_NEEDS_NOTIFICATIONS))
        }
        assertThat(scheduler.plans).isEmpty()
    }
    @Test
    fun `the port check result follows the sent notice when enabled`() = runTest {
        settings.state.value = settings.state.value.copy(portCheckEnabled = true)
        val vm = viewModel(sampleConfig())
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE))
            assertThat(awaitItem()).isEqualTo(KnockEffect.PortChecked("home", PortCheck.Reachable(listOf(22))))
        }
    }
    @Test
    fun `no port check traffic while the option is off`() = runTest {
        val vm = viewModel(sampleConfig())
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE))
        }
        assertThat(portProbe.probes).isEmpty()
    }
    @Test
    fun `a failed send can be retried with the same request`() = runTest {
        transport.sendError = KnockError.Transport(java.io.IOException("unreachable"))
        val vm = viewModel(sampleConfig(encryption = Encryption.Rijndael(SecretKey("", false))))
        vm.onEvent(KnockEvent.Knock("home"))
        vm.onEvent(KnockEvent.KeySubmitted("typed"))
        assertThat((vm.state.value as KnockState.Failed).retryable).isTrue()
        transport.sendError = null
        vm.onEvent(KnockEvent.Retry)
        assertThat(transport.sent).hasSize(1)
        assertThat(packets.built.last().key.value).isEqualTo("typed")
    }
    @Test
    fun `an unreadable config store fails without crashing`() = runTest {
        val vm = viewModel(sampleConfig())
        repository.storageError = IllegalStateException("disk full")
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(KnockState.Failed(UiError.Message(R.string.error_config_storage)))
    }
    @Test
    fun `an unrecognized source ip qr code is reported`() = runTest {
        val vm = viewModel(sampleConfig(sourceIp = SourceIp.Prompt))
        vm.onEvent(KnockEvent.Knock("home"))
        vm.onEvent(KnockEvent.SourceIpScanned("garbage"))
        assertThat(vm.state.value).isEqualTo(KnockState.PromptSourceIp("home", qrUnrecognized = true))
    }
    @Test
    fun `local network block offers the app settings`() = runTest {
        transport.sendError = KnockError.LocalNetworkBlocked
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(
            KnockState.Failed(
                UiError.Message(R.string.error_local_network_blocked),
                fixInAppSettings = true,
                retryable = true,
            )
        )
    }
    @Test
    fun `knock without prompts sends and emits sent then app launch`() = runTest {
        val config = sampleConfig().copy(externalApp = ExternalApp.SshUri("me@host"))
        val vm = viewModel(config)
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE))
            assertThat(awaitItem()).isEqualTo(KnockEffect.LaunchApp(ExternalApp.SshUri("me@host"), "home"))
        }
        assertThat(transport.sent).hasSize(1)
        assertThat(vm.state.value).isEqualTo(KnockState.Idle)
    }
    @Test
    fun `the external app waits for the configured delay`() = runTest {
        settings.state.value = settings.state.value.copy(externalAppDelaySeconds = 3)
        val config = sampleConfig().copy(externalApp = ExternalApp.SshUri("me@host"))
        val vm = viewModel(config)
        vm.effects.test {
            val start = testScheduler.currentTime
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isInstanceOf(KnockEffect.Sent::class.java)
            assertThat(testScheduler.currentTime - start).isEqualTo(0)
            assertThat(awaitItem()).isEqualTo(KnockEffect.LaunchApp(ExternalApp.SshUri("me@host"), "home"))
            assertThat(testScheduler.currentTime - start).isEqualTo(3_000)
        }
    }
    @Test
    fun `no delay without an external app`() = runTest {
        settings.state.value = settings.state.value.copy(externalAppDelaySeconds = 5)
        val vm = viewModel(sampleConfig())
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE))
            expectNoEvents()
        }
        assertThat(testScheduler.currentTime).isEqualTo(0)
    }
    @Test
    fun `missing key and prompted ip are asked in order`() = runTest {
        val config = sampleConfig(sourceIp = SourceIp.Prompt, encryption = Encryption.Rijndael(SecretKey("", false)))
        val vm = viewModel(config)
        vm.onEvent(KnockEvent.Knock("home"))
        assertThat(vm.state.value).isEqualTo(KnockState.PromptKey("home"))
        vm.onEvent(KnockEvent.KeySubmitted("typed"))
        assertThat(vm.state.value).isEqualTo(KnockState.PromptSourceIp("home"))
        vm.onEvent(KnockEvent.SourceIpSubmitted("bad"))
        assertThat(vm.state.value).isEqualTo(KnockState.PromptSourceIp("home", invalid = true))
        vm.onEvent(KnockEvent.SourceIpScanned("CLIENT_IP:198.51.100.8"))
        assertThat(packets.built.single().sourceIp).isEqualTo("198.51.100.8")
        assertThat(packets.built.single().key.value).isEqualTo("typed")
    }
    @Test
    fun `nfc knock is refused when disabled in settings`() = runTest {
        val vm = viewModel(sampleConfig())
        vm.scan("home")
        assertThat(vm.state.value).isEqualTo(KnockState.Failed(UiError.Message(R.string.error_nfc_disabled)))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `nfc knock asks before opening the single config of the tag`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig("office"), sampleConfig("home"))
        vm.effects.test {
            vm.scan("home")
            assertThat(awaitItem()).isEqualTo(KnockEffect.Authenticate)
            assertThat(vm.state.value).isEqualTo(KnockState.ChooseConfig(listOf("home")))
            assertThat(transport.sent).isEmpty()
            vm.onEvent(KnockEvent.ConfigChosen("home"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("home", FIXED_SOURCE))
        }
    }
    @Test
    fun `nfc knock asks which config to open when the tag holds several`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig("office"), sampleConfig("home"), sampleConfig("lab"))
        val digests = listOf("home", "renamed", "office", "home").map { nfcTags.digest(Nickname(it)) }
        vm.effects.test {
            vm.scan(digests)
            assertThat(awaitItem()).isEqualTo(KnockEffect.Authenticate)
            assertThat(vm.state.value).isEqualTo(KnockState.ChooseConfig(listOf("home", "office")))
            assertThat(transport.sent).isEmpty()
            vm.onEvent(KnockEvent.ConfigChosen("lab"))
            assertThat(vm.state.value).isEqualTo(KnockState.ChooseConfig(listOf("home", "office")))
            vm.onEvent(KnockEvent.ConfigChosen("office"))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Sent("office", FIXED_SOURCE))
        }
        assertThat(transport.sent).hasSize(1)
    }
    @Test
    fun `cancelling the config choice aborts`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig("office"), sampleConfig("home"))
        vm.effects.test {
            vm.scan("home", "office")
            assertThat(awaitItem()).isEqualTo(KnockEffect.Authenticate)
            vm.onEvent(KnockEvent.Cancel)
            assertThat(awaitItem()).isEqualTo(KnockEffect.Aborted)
        }
        vm.onEvent(KnockEvent.ConfigChosen("home"))
        assertThat(vm.state.value).isEqualTo(KnockState.Idle)
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `nfc knock is refused without the app lock, before any authentication`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        vault.statusFlow.value = vault.statusFlow.value.copy(lockEnabled = false)
        val vm = viewModel(sampleConfig())
        vm.onEvent(KnockEvent.KnockFromNfc(listOf(nfcTags.digest(Nickname("home")))))
        assertThat(vm.state.value).isEqualTo(KnockState.Failed(UiError.Message(R.string.error_nfc_lock_required)))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `nfc knock authenticates again before listing the configs`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig())
        vm.effects.test {
            vm.onEvent(KnockEvent.KnockFromNfc(listOf(nfcTags.digest(Nickname("home")))))
            assertThat(awaitItem()).isEqualTo(KnockEffect.Authenticate)
            assertThat(vm.state.value).isEqualTo(KnockState.Authenticating)
            vm.onEvent(KnockEvent.ConfigChosen("home"))
            vm.onEvent(KnockEvent.NfcAuthCompleted(AuthResult.Success))
            assertThat(vm.state.value).isEqualTo(KnockState.ChooseConfig(listOf("home")))
        }
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `cancelled nfc authentication lists nothing`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig())
        vm.scan(listOf(nfcTags.digest(Nickname("home"))), auth = AuthResult.Cancelled)
        assertThat(vm.state.value).isEqualTo(KnockState.Idle)
        vm.onEvent(KnockEvent.NfcAuthCompleted(AuthResult.Success))
        assertThat(vm.state.value).isEqualTo(KnockState.Idle)
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `nfc knock without a device credential left is refused`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig())
        vm.scan(listOf(nfcTags.digest(Nickname("home"))), auth = AuthResult.Unavailable)
        assertThat(vm.state.value).isEqualTo(KnockState.Failed(UiError.Message(R.string.error_nfc_lock_required)))
    }
    @Test
    fun `nfc knock reports a tag matching no config, which may be erased`() = runTest {
        settings.state.value = AppSettings(nfcKnockEnabled = true)
        val vm = viewModel(sampleConfig("home"))
        vm.scan("renamed")
        assertThat(vm.state.value)
            .isEqualTo(KnockState.Failed(UiError.Message(R.string.error_nfc_tag_unknown), tagErasable = true))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `cancel resets state and emits aborted`() = runTest {
        val vm = viewModel(sampleConfig(encryption = Encryption.Rijndael(SecretKey("", false))))
        vm.effects.test {
            vm.onEvent(KnockEvent.Knock("home"))
            vm.onEvent(KnockEvent.Cancel)
            assertThat(awaitItem()).isEqualTo(KnockEffect.Aborted)
        }
        assertThat(vm.state.value).isEqualTo(KnockState.Idle)
    }
}

private val FIXED_SOURCE = ResolvedSourceIp("198.51.100.4", SourceIpMethod.Fixed)
