package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.data.local.models.KnockLogColumns
import fr.gildasledrogoff.fwknop.data.local.models.KnockLogEntity
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.model.KnockLogOutcome
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import java.time.Instant

fun KnockLogEvent.toEntity(timestamp: Instant): KnockLogEntity {
    val (method, methodDetail) = source?.method?.toColumns() ?: (null to null)
    val (result, errorDetail) = when (outcome) {
        KnockLogOutcome.Sent -> KnockLogColumns.RESULT_SENT to null
        is KnockLogOutcome.Failed -> outcome.code to outcome.detail
    }
    return KnockLogEntity(
        timestamp = timestamp.toEpochMilli(),
        nickName = nickname.value,
        protocol = protocol.name,
        sourceIp = source?.address,
        sourceIpMethod = method,
        sourceIpMethodDetail = methodDetail,
        result = result,
        errorDetail = errorDetail,
    )
}

fun KnockLogEntity.toDomain(): KnockLogEntry {
    val method = sourceIpMethod?.let { sourceIpMethodOf(it, sourceIpMethodDetail) }
    return KnockLogEntry(
        id = id,
        timestamp = Instant.ofEpochMilli(timestamp),
        event = KnockLogEvent(
            nickname = Nickname(nickName),
            // Unknown protocol names cannot come from this app: fall back rather than drop the row.
            protocol = Protocol.entries.firstOrNull { it.name == protocol } ?: Protocol.UDP,
            source = if (sourceIp != null && method != null) ResolvedSourceIp(sourceIp, method) else null,
            outcome = if (result == KnockLogColumns.RESULT_SENT) KnockLogOutcome.Sent
            else KnockLogOutcome.Failed(result, errorDetail),
        ),
    )
}

private fun SourceIpMethod.toColumns(): Pair<String, String?> = when (this) {
    SourceIpMethod.Kernel -> KERNEL to null
    is SourceIpMethod.Stun -> STUN to server
    is SourceIpMethod.Https -> HTTPS to host
    SourceIpMethod.Dns -> DNS to null
    SourceIpMethod.PacketSource -> PACKET_SOURCE to null
    SourceIpMethod.Fixed -> FIXED to null
    SourceIpMethod.Prompted -> PROMPTED to null
}

private fun sourceIpMethodOf(name: String, detail: String?): SourceIpMethod? = when (name) {
    KERNEL -> SourceIpMethod.Kernel
    STUN -> SourceIpMethod.Stun(detail.orEmpty())
    HTTPS -> SourceIpMethod.Https(detail.orEmpty())
    DNS -> SourceIpMethod.Dns
    PACKET_SOURCE -> SourceIpMethod.PacketSource
    FIXED -> SourceIpMethod.Fixed
    PROMPTED -> SourceIpMethod.Prompted
    else -> null
}

private const val KERNEL = "KERNEL"
private const val STUN = "STUN"
private const val HTTPS = "HTTPS"
private const val DNS = "DNS"
private const val PACKET_SOURCE = "PACKET_SOURCE"
private const val FIXED = "FIXED"
private const val PROMPTED = "PROMPTED"
