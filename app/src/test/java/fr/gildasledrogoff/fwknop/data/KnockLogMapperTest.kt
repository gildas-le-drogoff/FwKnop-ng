// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.models.KnockLogColumns
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.model.*
import org.junit.Test
    @Test
    fun everySourceIpMethodSurvivesARoundTrip() {
        listOf(
            SourceIpMethod.Kernel,
            SourceIpMethod.Stun("stun.example.org"),
            SourceIpMethod.Https("ifconfig.me"),
            SourceIpMethod.Dns,
            SourceIpMethod.PacketSource,
            SourceIpMethod.Fixed,
            SourceIpMethod.Prompted,
        ).forEach { method ->
            val event = event(source = ResolvedSourceIp("203.0.113.7", method))
            assertThat(event.toEntity(at).toDomain().event).isEqualTo(event)
        }
    }
    @Test
    fun aSentKnockIsStoredWithoutErrorDetail() {
        val entity = event(protocol = Protocol.TCP).toEntity(at)
        assertThat(entity.timestamp).isEqualTo(at.toEpochMilli())
        assertThat(entity.nickName).isEqualTo("home")
        assertThat(entity.protocol).isEqualTo("TCP")
        assertThat(entity.result).isEqualTo(KnockLogColumns.RESULT_SENT)
        assertThat(entity.errorDetail).isNull()
        assertThat(entity.toDomain().timestamp).isEqualTo(at)
    }
    @Test
    fun aFailureBeforeResolutionKeepsItsCodeAndDetail() {
        val event = event(source = null, outcome = KnockLogOutcome.Failed("SOURCE_IP_UNRESOLVED", "STUN timeout"))
        val entity = event.toEntity(at)
        assertThat(entity.sourceIp).isNull()
        assertThat(entity.sourceIpMethod).isNull()
        assertThat(entity.result).isEqualTo("SOURCE_IP_UNRESOLVED")
        assertThat(entity.errorDetail).isEqualTo("STUN timeout")
        assertThat(entity.toDomain().event).isEqualTo(event)
    }
    @Test
    fun aWakeUpKeepsItsKindAndOlderRowsAreKnocks() {
        val wake = KnockLogEvent.wakeOnLan(Nickname("home"), DomainResult.Success(Unit))
        val entity = wake.toEntity(at)
        assertThat(entity.kind).isEqualTo("WAKE_ON_LAN")
        assertThat(entity.toDomain().event).isEqualTo(wake)
        assertThat(event().toEntity(at).kind).isEqualTo(KnockLogColumns.KIND_KNOCK)
        assertThat(entity.copy(kind = "FUTURE").toDomain().event.kind).isEqualTo(KnockLogKind.KNOCK)
    }
    @Test
    fun anUnknownStoredMethodDropsTheSourceInsteadOfTheRow() {
        val entity = event().toEntity(at).copy(sourceIpMethod = "FUTURE")
        assertThat(entity.toDomain().event.source).isNull()
    }
    private fun event(
        protocol: Protocol = Protocol.UDP,
        source: ResolvedSourceIp? = ResolvedSourceIp("203.0.113.7", SourceIpMethod.Kernel),
        outcome: KnockLogOutcome = KnockLogOutcome.Sent,
    ) = KnockLogEvent(Nickname("home"), protocol, source, outcome)
}
