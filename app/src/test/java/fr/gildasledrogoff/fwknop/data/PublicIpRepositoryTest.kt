// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.KernelRouteApi
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.PublicIpApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunApi
import fr.gildasledrogoff.fwknop.data.repository.PublicIpRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SourceIpProbe
import fr.gildasledrogoff.fwknop.data.repository.SourceIpProbes
import fr.gildasledrogoff.fwknop.data.repository.SourceIpStage
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.InetAddress

private const val SERVER = "8.8.4.4"
private val STUN_A = SourceIpMethod.Stun("stun.a.example")
private val STUN_B = SourceIpMethod.Stun("stun.b.example")
private val HTTPS = SourceIpMethod.Https("ip.example")

@RunWith(RobolectricTestRunner::class)
class PublicIpRepositoryTest {
    private val steps = mutableListOf<KnockStep>()
    private var stages: List<SourceIpStage> = emptyList()

    private fun TestScope.repository(): PublicIpRepositoryImpl {
        val io = StandardTestDispatcher(testScheduler)
        val scripted = object : SourceIpProbes(KernelRouteApi(), StunApi(), PublicIpApi(OkHttpClient())) {
            override fun towards(server: InetAddress, settings: AppSettings, route: NetworkPath.Route) = stages
        }
        return PublicIpRepositoryImpl(
            scripted,
            object : NetworkPath {
                override fun current() = NetworkPath.Route(null)
            },
            FakeSettingsRepository(),
            object : DispatcherProvider {
                override val main = io
                override val io = io
                override val default = io
            },
        )
    }

    private suspend fun PublicIpRepositoryImpl.resolve() =
        resolvePublicIp(SERVER, NetworkPath.Route(null), KnockObserver { steps += it })

    private fun probe(
        method: SourceIpMethod,
        crossChecked: Boolean = false,
        accepts: (InetAddress) -> Boolean = { !it.isSiteLocalAddress },
        run: suspend () -> InetAddress,
    ) = SourceIpProbe(method, accepts, crossChecked, run)

    private fun answers(address: String, after: Long = 0) = suspend {
        delay(after)
        InetAddress.getByName(address)
    }

    private val fails: suspend () -> InetAddress = { throw IOException("unreachable") }

    @Test
    fun `falls back stage by stage until an answer is accepted`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = fails)),
            listOf(probe(SourceIpMethod.Kernel, run = answers("192.168.1.2"))),
            listOf(probe(HTTPS, run = answers("1.2.3.4"))),
        )
        assertThat(repository().resolve()).isEqualTo(DomainResult.Success(ResolvedSourceIp("1.2.3.4", HTTPS)))
    }

    @Test
    fun `failure tells which probe failed towards which server address`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = fails)),
            listOf(probe(SourceIpMethod.Kernel, run = answers("192.168.1.2"))),
        )
        val detail = ((repository().resolve() as DomainResult.Failure).error as KnockError.SourceIpUnresolved).detail
        assertThat(detail).isEqualTo(
            "towards 8.8.4.4 (IPv4): STUN stun.a.example: unreachable; kernel: unusable address 192.168.1.2"
        )
    }

    @Test
    fun `with parallel stun servers the first accepted answer wins and the slower one is cancelled`() = runTest {
        val slowCancelled = CompletableDeferred<Unit>()
        stages = listOf(
            listOf(
                probe(STUN_A) {
                    try {
                        awaitCancellation()
                    } finally {
                        slowCancelled.complete(Unit)
                    }
                },
                probe(STUN_B, run = answers("1.2.3.4", after = 300)),
            ),
            listOf(probe(HTTPS, run = answers("5.6.7.8"))),
        )
        assertThat(repository().resolve()).isEqualTo(DomainResult.Success(ResolvedSourceIp("1.2.3.4", STUN_B)))
        assertThat(currentTime).isEqualTo(300)
        assertThat(slowCancelled.isCompleted).isTrue()
    }

    @Test
    fun `a probe ignoring cancellation cannot hold the knock past the 10 s budget`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = fails)),

            listOf(probe(HTTPS) { withContext(NonCancellable) { awaitCancellation() } }),
        )
        val result = repository().resolve()
        assertThat(currentTime).isEqualTo(PublicIpRepositoryImpl.BUDGET.inWholeMilliseconds)
        val detail = ((result as DomainResult.Failure).error as KnockError.SourceIpUnresolved).detail
        assertThat(detail).isEqualTo("towards 8.8.4.4 (IPv4): STUN stun.a.example: unreachable; no answer within 10 s")
    }

    @Test
    fun `nothing reaches the observer once the lookup returned`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A) { withContext(NonCancellable) { delay(60_000) }; throw IOException("late") }),
            listOf(probe(HTTPS, run = answers("1.2.3.4"))),
        )
        repository().resolve()
        val seen = steps.toList()
        testScheduler.advanceUntilIdle()
        assertThat(currentTime).isAtLeast(60_000)
        assertThat(steps).isEqualTo(seen)
        assertThat(seen).containsExactly(KnockStep.ThirdPartyQueried(STUN_A))
    }

    @Test
    fun `an answer already kept survives a cross-check that outlives the budget`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = answers("1.2.3.4"))),
            listOf(probe(SourceIpMethod.Dns, crossChecked = true) { withContext(NonCancellable) { awaitCancellation() } }),
        )
        assertThat(repository().resolve()).isEqualTo(DomainResult.Success(ResolvedSourceIp("1.2.3.4", STUN_A)))
        assertThat(currentTime).isEqualTo(PublicIpRepositoryImpl.BUDGET.inWholeMilliseconds)
    }

    @Test
    fun `opendns disagreeing with stun is flagged suspect, even with an unusable address`() = runTest {
        listOf("9.9.9.9", "192.168.1.1").forEach { forged ->
            steps.clear()
            stages = listOf(
                listOf(probe(STUN_A, run = answers("1.2.3.4", after = 50))),
                listOf(probe(SourceIpMethod.Dns, crossChecked = true, run = answers(forged))),
            )
            val result = repository().resolve()
            assertThat(result).isEqualTo(DomainResult.Success(ResolvedSourceIp("1.2.3.4", STUN_A)))
            val check = steps.filterIsInstance<KnockStep.SourceCrossChecked>().single()
            assertThat(check).isEqualTo(
                KnockStep.SourceCrossChecked(SourceIpMethod.Dns, forged, ResolvedSourceIp("1.2.3.4", STUN_A))
            )
            assertThat(check.verdict).isEqualTo(CrossCheckVerdict.SUSPECT)
        }
    }

    @Test
    fun `opendns agreeing with stun is confirmed`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = answers("1.2.3.4"))),
            listOf(probe(SourceIpMethod.Dns, crossChecked = true, run = answers("1.2.3.4", after = 20))),
        )
        repository().resolve()
        assertThat(steps.filterIsInstance<KnockStep.SourceCrossChecked>().single().verdict)
            .isEqualTo(CrossCheckVerdict.CONFIRMED)
    }

    @Test
    fun `opendns used alone is reported unverified`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = fails)),
            listOf(probe(SourceIpMethod.Dns, crossChecked = true, run = answers("9.9.9.9"))),
        )
        assertThat(repository().resolve())
            .isEqualTo(DomainResult.Success(ResolvedSourceIp("9.9.9.9", SourceIpMethod.Dns)))
        val check = steps.filterIsInstance<KnockStep.SourceCrossChecked>().single()
        assertThat(check.reference).isNull()
        assertThat(check.verdict).isEqualTo(CrossCheckVerdict.UNVERIFIED)
    }

    @Test
    fun `opendns starts with the first stage so the cross-check costs no extra round trip`() = runTest {
        stages = listOf(
            listOf(probe(STUN_A, run = answers("1.2.3.4", after = 100))),
            listOf(probe(SourceIpMethod.Dns, crossChecked = true, run = answers("1.2.3.4", after = 100))),
        )
        repository().resolve()
        assertThat(currentTime).isEqualTo(100)
    }

    @Test
    fun `third parties are announced as their stage starts, never for an unreached stage`() = runTest {
        stages = listOf(
            listOf(probe(SourceIpMethod.Kernel, run = fails)),
            listOf(probe(STUN_A, run = fails), probe(STUN_B, run = answers("1.2.3.4"))),
            listOf(probe(HTTPS, run = answers("5.6.7.8"))),
            listOf(probe(SourceIpMethod.Dns, crossChecked = true, run = answers("1.2.3.4"))),
        )
        repository().resolve()
        assertThat(steps.filterIsInstance<KnockStep.ThirdPartyQueried>().map { it.method })
            .containsExactly(SourceIpMethod.Dns, STUN_A, STUN_B).inOrder()
    }
}
