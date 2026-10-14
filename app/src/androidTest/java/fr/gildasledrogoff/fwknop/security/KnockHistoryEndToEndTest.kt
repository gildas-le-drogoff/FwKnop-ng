// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.security
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.repository.KnockHistoryRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
    private val testVault = TestVault(InstrumentationRegistry.getInstrumentation().targetContext)
    private val settings = MutableSettings()
    private var now = Instant.parse("2026-10-15T08:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val history = KnockHistoryRepositoryImpl(testVault.session, settings, clock)
    @Before
    @After
    fun wipe() = testVault.wipe()
    @Test
    fun aRecordedKnockIsReadableAndNeverInClearOnDisk() = runBlocking {
        unlock()
        history.record(event(SOURCE_IP))
        val entries = history.observeHistory().first()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().timestamp).isEqualTo(now)
        assertThat(entries.single().event).isEqualTo(event(SOURCE_IP))
        testVault.repository.lock()
        testVault.assertNothingInClearOnDisk(listOf(SOURCE_IP, STUN_SERVER, NICKNAME))
    }
    @Test
    fun aKnockWhileLockedIsDroppedWithoutBlocking() = runBlocking {
        history.record(event(SOURCE_IP))
        unlock()
        assertThat(history.observeHistory().first()).isEmpty()
    }
    @Test
    fun entriesOlderThanTheRetentionArePurgedOnWrite() = runBlocking {
        unlock()
        history.record(event("192.0.2.1"))
        now += Duration.ofDays(31)
        history.record(event("192.0.2.2"))
        assertThat(history.observeHistory().first().map { it.event.source?.address }).containsExactly("192.0.2.2")
    }
    @Test
    fun aShorterRetentionPurgesOnDemand() = runBlocking {
        unlock()
        history.record(event("192.0.2.1"))
        now += Duration.ofDays(8)
        history.purgeExpired()
        assertThat(history.observeHistory().first()).hasSize(1)
        settings.state.value = AppSettings(knockHistoryRetention = KnockHistoryRetention.DAYS_7)
        history.purgeExpired()
        assertThat(history.observeHistory().first()).isEmpty()
    }
    @Test
    fun retentionOffRecordsNothingAndWipesTheJournal() = runBlocking {
        unlock()
        history.record(event("192.0.2.1"))
        settings.state.value = AppSettings(knockHistoryRetention = KnockHistoryRetention.OFF)
        history.record(event("192.0.2.2"))
        assertThat(history.observeHistory().first()).hasSize(1)
        history.purgeExpired()
        assertThat(history.observeHistory().first()).isEmpty()
    }
    private suspend fun unlock() =
        assertThat(testVault.repository.unlock()).isEqualTo(DomainResult.Success(Unit))
    private fun event(sourceIp: String) = KnockLogEvent(
        nickname = Nickname(NICKNAME),
        protocol = Protocol.UDP,
        source = ResolvedSourceIp(sourceIp, SourceIpMethod.Stun(STUN_SERVER)),
        outcome = KnockLogOutcome.Failed("TRANSPORT", "ENETUNREACH"),
    )
    private class MutableSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        override val settings = state
        override suspend fun saveSettings(settings: AppSettings) {
            state.value = settings
        }
        override suspend fun markHelpShown() = Unit
        override suspend fun dismissLockSuggestion() = Unit
    }
    private companion object {
        const val NICKNAME = "knock-history-host"
        const val SOURCE_IP = "203.0.113.77"
        const val STUN_SERVER = "stun.history.example"
    }
}
