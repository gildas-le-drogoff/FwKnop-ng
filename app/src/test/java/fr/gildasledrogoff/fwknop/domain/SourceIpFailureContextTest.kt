// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.fakes.FakePublicIpRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaTransportRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SourceIpFailureContextTest {
    @Test
    fun `each failed server address keeps its own context`() = runTest {
        val publicIp = FakePublicIpRepository(
            results = mapOf(
                "192.0.2.10" to DomainResult.Failure(
                    KnockError.SourceIpUnresolved("towards 192.0.2.10 (IPv4): STUN stun.example: timeout")
                ),
            ),

            default = DomainResult.Success(ResolvedSourceIp("2001:db8::9", SourceIpMethod.Kernel)),
        )
        val transport = FakeSpaTransportRepository(serverFamilies = listOf("192.0.2.10", "192.0.2.11"))
        val result = ResolveKnockRouteUseCase(publicIp, transport)(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isEqualTo(
            DomainResult.Failure(
                KnockError.SourceIpUnresolved(
                    "towards 192.0.2.10 (IPv4): STUN stun.example: timeout / towards 192.0.2.11: SourceIpInvalid"
                )
            )
        )
    }
}
