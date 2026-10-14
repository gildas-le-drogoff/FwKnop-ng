// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.model.WifiPresence.*
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.fakes.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
    fun `strips the quotes Android puts around the name`() {
        assertThat(Ssids.normalize("\"Maison\"")).isEqualTo("Maison")
        assertThat(Ssids.normalize("Maison")).isEqualTo("Maison")
    }
    @Test
    fun `a hidden or empty name is unknown`() {
        assertThat(Ssids.normalize("<unknown ssid>")).isNull()
        assertThat(Ssids.normalize("\"\"")).isNull()
        assertThat(Ssids.normalize(null)).isNull()
    }
    @Test
    fun `presence prefers a trusted network over a hidden one`() {
        val trusted = setOf("Maison")
        assertThat(Ssids.presence(listOf(null, "Maison"), trusted)).isEqualTo(TRUSTED)
        assertThat(Ssids.presence(listOf(null, "Café"), trusted)).isEqualTo(UNKNOWN)
        assertThat(Ssids.presence(listOf("Café"), trusted)).isEqualTo(UNTRUSTED)
        assertThat(Ssids.presence(emptyList(), trusted)).isEqualTo(UNTRUSTED)
    }
    @Test
    fun `network names are case sensitive`() {
        assertThat(Ssids.presence(listOf("maison"), setOf("Maison"))).isEqualTo(UNTRUSTED)
    }
}
class DetectTrustedNetworkExitUseCaseTest {
    private val detect = DetectTrustedNetworkExitUseCase()
    private val presence = MutableSharedFlow<WifiPresence>()
    private val settle = 5.seconds
    @Test
    fun `emits once the device stays away from the trusted networks`() = runTest {
        detect(presence, settle).test {
            presence.emit(TRUSTED)
            advanceTimeBy(6.seconds)
            presence.emit(UNTRUSTED)
            advanceTimeBy(4.seconds)
            expectNoEvents()
            advanceTimeBy(2.seconds)
            awaitItem()
        }
    }
    @Test
    fun `a short drop while roaming does not knock`() = runTest {
        detect(presence, settle).test {
            presence.emit(TRUSTED)
            advanceTimeBy(6.seconds)
            presence.emit(UNTRUSTED)
            advanceTimeBy(2.seconds)
            presence.emit(TRUSTED)
            advanceTimeBy(10.seconds)
            expectNoEvents()
        }
    }
    @Test
    fun `starting away from the trusted networks does not knock`() = runTest {
        detect(presence, settle).test {
            presence.emit(UNTRUSTED)
            advanceTimeBy(10.seconds)
            expectNoEvents()
        }
    }
    @Test
    fun `a hidden network name is not taken for a departure`() = runTest {
        detect(presence, settle).test {
            presence.emit(TRUSTED)
            advanceTimeBy(6.seconds)
            presence.emit(UNKNOWN)
            advanceTimeBy(10.seconds)
            expectNoEvents()
        }
    }
    @Test
    fun `knocks again on each new departure`() = runTest {
        detect(presence, settle).test {
            repeat(2) {
                presence.emit(TRUSTED)
                advanceTimeBy(6.seconds)
                presence.emit(UNTRUSTED)
                advanceTimeBy(6.seconds)
                awaitItem()
            }
        }
    }
}
class AutoKnockUseCaseTest {
    private val configs = FakeConfigRepository(listOf(sampleConfig()))
    private val vault = FakeVaultRepository()
    private val transport = FakeSpaTransportRepository(serverIp = "192.0.2.10")
    private val autoKnock = AutoKnockUseCase(
        vault,
        configs,
        IsAutoKnockableUseCase(GetRequiredPromptsUseCase()),
        SendKnockUseCase(
            ResolveKnockRouteUseCase(FakePublicIpRepository(), transport),
            BuildSpaPacketUseCase(FakeSpaPacketRepository(), FakeGpgRepository()),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        ),
    )
    @Test
    fun `sends the packet through the regular knock`() = runTest {
        val result = autoKnock(Nickname("home"))
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat(transport.sent.single().second.address).isEqualTo("192.0.2.10")
    }
    @Test
    fun `opens the vault silently when the app lock is off`() = runTest {
        autoKnock(Nickname("home"))
        assertThat(vault.unlockCalls).isEqualTo(1)
        assertThat(vault.status.value.state).isEqualTo(VaultState.UNLOCKED)
    }
    @Test
    fun `never asks for authentication when the app lock is on`() = runTest {
        vault.statusFlow.value = vault.statusFlow.value.copy(lockEnabled = true)
        val result = autoKnock(Nickname("home"))
        assertThat(result).isEqualTo(DomainResult.Failure(AutoKnockError.VaultLocked))
        assertThat(vault.unlockCalls).isEqualTo(0)
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `uses a vault the user already unlocked`() = runTest {
        vault.statusFlow.value = vault.statusFlow.value.copy(state = VaultState.UNLOCKED, lockEnabled = true)
        assertThat(autoKnock(Nickname("home"))).isInstanceOf(DomainResult.Success::class.java)
        assertThat(vault.unlockCalls).isEqualTo(0)
    }
    @Test
    fun `reports a deleted config`() = runTest {
        assertThat(autoKnock(Nickname("gone"))).isEqualTo(DomainResult.Failure(AutoKnockError.ConfigMissing))
    }
    @Test
    fun `refuses configs that need the user`() = runTest {
        configs.configs.value = listOf(
            sampleConfig("prompt-ip", sourceIp = SourceIp.Prompt),
            sampleConfig("blank-key", encryption = Encryption.Rijndael(SecretKey("", base64 = false))),
            sampleConfig("gpg", encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2))),
        )
        listOf("prompt-ip", "blank-key", "gpg").forEach {
            assertThat(autoKnock(Nickname(it))).isEqualTo(DomainResult.Failure(AutoKnockError.NeedsUser))
        }
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `wraps knock failures`() = runTest {
        transport.sendError = KnockError.PacketGeneration
        val result = autoKnock(Nickname("home"))
        assertThat(result).isEqualTo(DomainResult.Failure(AutoKnockError.Knock(KnockError.PacketGeneration)))
    }
}
class SaveAutoKnockSettingsUseCaseTest {
    @Test
    fun `stores bare network names`() = runTest {
        val repository = FakeAutoKnockRepository()
        SaveAutoKnockSettingsUseCase(repository)(
            AutoKnockSettings(trustedSsids = setOf("\"Maison\"", " Bureau ", "<unknown ssid>"))
        )
        assertThat(repository.state.value.trustedSsids).containsExactly("Bureau", "Maison")
    }
    @Test
    fun `armed only with a config and a network`() {
        assertThat(AutoKnockSettings(enabled = true, nickname = "home").armed).isFalse()
        assertThat(AutoKnockSettings(enabled = true, trustedSsids = setOf("Maison")).armed).isFalse()
        assertThat(AutoKnockSettings(enabled = false, nickname = "home", trustedSsids = setOf("Maison")).armed).isFalse()
        assertThat(AutoKnockSettings(enabled = true, nickname = "home", trustedSsids = setOf("Maison")).armed).isTrue()
    }
}
