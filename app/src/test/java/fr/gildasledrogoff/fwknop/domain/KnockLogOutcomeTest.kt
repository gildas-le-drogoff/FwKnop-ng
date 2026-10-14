package fr.gildasledrogoff.fwknop.domain

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.model.KnockLogOutcome
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

class KnockLogOutcomeTest {
    @Test
    fun aSuccessIsSent() {
        assertThat(KnockLogOutcome.of(DomainResult.Success(Unit))).isEqualTo(KnockLogOutcome.Sent)
    }

    @Test
    fun errorsKeepTheirDiagnosticDetail() {
        assertThat(KnockLogOutcome.of(KnockError.SourceIpUnresolved("all probes failed")))
            .isEqualTo(KnockLogOutcome.Failed("SOURCE_IP_UNRESOLVED", "all probes failed"))
        assertThat(KnockLogOutcome.of(KnockError.ServerFamilyMismatch("knock.example.org", "2001:db8::1")))
            .isEqualTo(KnockLogOutcome.Failed("SERVER_FAMILY_MISMATCH", "knock.example.org / 2001:db8::1"))
        assertThat(KnockLogOutcome.of(KnockError.Transport(IOException("ENETUNREACH"))))
            .isEqualTo(KnockLogOutcome.Failed("TRANSPORT", "ENETUNREACH"))
        assertThat(KnockLogOutcome.of(KnockError.Gpg(GpgError.Failed(null))))
            .isEqualTo(KnockLogOutcome.Failed("GPG", "FAILED"))
        assertThat(KnockLogOutcome.of(KnockError.Config(ConfigError.Storage(IllegalStateException("x")))))
            .isEqualTo(KnockLogOutcome.Failed("CONFIG", "STORAGE"))
    }

    @Test
    fun anEventIsBuiltFromTheConfigAndTheKnockResult() {
        val source = ResolvedSourceIp("198.51.100.4", SourceIpMethod.Fixed)
        val event = KnockLogEvent.of(sampleConfig(), source, DomainResult.Failure(KnockError.LocalNetworkBlocked))
        assertThat(event.nickname.value).isEqualTo("home")
        assertThat(event.protocol).isEqualTo(Protocol.UDP)
        assertThat(event.source).isEqualTo(source)
        assertThat(event.outcome).isEqualTo(KnockLogOutcome.Failed("LOCAL_NETWORK_BLOCKED"))
    }
}
