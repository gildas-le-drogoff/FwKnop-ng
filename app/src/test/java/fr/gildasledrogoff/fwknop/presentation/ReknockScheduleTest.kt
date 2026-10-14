// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import fr.gildasledrogoff.fwknop.presentation.service.NetworkChangeDetector
import fr.gildasledrogoff.fwknop.presentation.service.ReknockSchedule
import fr.gildasledrogoff.fwknop.presentation.service.followsNetwork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class ReknockScheduleTest {
    private val schedule = ReknockSchedule(intervalMs = INTERVAL, retryMs = RETRY)
    @Test
    fun `knocks periodically`() = runTest {
        val times = mutableListOf<Long>()
        val job = launch { schedule.run { times += currentTime; true } }
        advanceTimeBy(3 * INTERVAL + 1)
        job.cancel()
        assertThat(times).containsExactly(INTERVAL, 2 * INTERVAL, 3 * INTERVAL).inOrder()
    }
    @Test
    fun `a network change knocks at once and restarts the period`() = runTest {
        val times = mutableListOf<Long>()
        val job = launch { schedule.run { times += currentTime; true } }
        advanceTimeBy(1_000)
        schedule.onNetworkChanged()
        runCurrent()
        advanceTimeBy(INTERVAL + 1)
        job.cancel()
        assertThat(times).containsExactly(1_000L, 1_000L + INTERVAL).inOrder()
    }
    @Test
    fun `a failed periodic knock ends the session`() = runTest {
        var knocks = 0
        schedule.run { knocks++; false }
        assertThat(knocks).isEqualTo(1)
        assertThat(currentTime).isEqualTo(INTERVAL)
    }
    @Test
    fun `a knock after a network change is retried, then gives up`() = runTest {
        val times = mutableListOf<Long>()
        schedule.onNetworkChanged()
        schedule.run { times += currentTime; false }
        assertThat(times).containsExactly(0L, RETRY, 2 * RETRY).inOrder()
        assertThat(times).hasSize(ReknockSchedule.NETWORK_ATTEMPTS)
    }
    @Test
    fun `a successful retry goes back to the normal period`() = runTest {
        val times = mutableListOf<Long>()
        schedule.onNetworkChanged()
        val job = launch { schedule.run { times += currentTime; times.size >= 2 } }
        advanceTimeBy(RETRY + INTERVAL + 1)
        job.cancel()
        assertThat(times).containsExactly(0L, RETRY, RETRY + INTERVAL).inOrder()
    }
    @Test
    fun `only an actual switch of network or transport is a change`() {
        val detector = NetworkChangeDetector("wifi", setOf(WIFI))
        assertThat(detector.onCapabilitiesChanged("wifi", setOf(WIFI))).isFalse()
        assertThat(detector.onCapabilitiesChanged("mobile", setOf(CELLULAR))).isTrue()
        assertThat(detector.onCapabilitiesChanged("mobile", setOf(CELLULAR))).isFalse()
        detector.onLost("wifi")
        assertThat(detector.onCapabilitiesChanged("mobile", setOf(CELLULAR))).isFalse()
        detector.onLost("mobile")
        assertThat(detector.onCapabilitiesChanged("mobile", setOf(CELLULAR))).isTrue()
    }
    @Test
    fun `a vpn whose underlying transport changes is a change`() {
        val detector = NetworkChangeDetector("vpn", setOf(WIFI))
        assertThat(detector.onCapabilitiesChanged("vpn", setOf(CELLULAR))).isTrue()
    }
    @Test
    fun `the first network after none is a change`() {
        val detector = NetworkChangeDetector<String>(null)
        assertThat(detector.onCapabilitiesChanged("wifi", setOf(WIFI))).isTrue()
    }
    @Test
    fun `only network dependent source ips are re-knocked on a change`() {
        fun request(sourceIp: SourceIp) = KnockRequest(sampleConfig(sourceIp = sourceIp))
        assertThat(request(SourceIp.Resolve).followsNetwork).isTrue()
        assertThat(request(SourceIp.Source).followsNetwork).isTrue()
        assertThat(request(SourceIp.Fixed("198.51.100.4")).followsNetwork).isFalse()
        assertThat(request(SourceIp.Prompt).followsNetwork).isFalse()
    }
    private companion object {
        const val INTERVAL = 50_000L
        const val RETRY = 5_000L
        const val WIFI = 1
        const val CELLULAR = 0
    }
}
