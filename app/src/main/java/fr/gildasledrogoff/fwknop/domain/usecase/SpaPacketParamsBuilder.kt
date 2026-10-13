package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.NatTarget
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ServerEndpoint
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams
import fr.gildasledrogoff.fwknop.domain.model.portAccess
import kotlin.random.Random

internal fun KnockRequest.toPacketParams(
    sourceIp: String,
    serverIp: String,
    key: SecretKey
): SpaPacketParams {
    val access = config.access
    val crypto = config.crypto
    return SpaPacketParams(
        accessPorts = access.portAccess?.ports.orEmpty(),
        sourceIp = sourceIp,
        key = key,
        hmac = crypto.hmac,
        timeoutSeconds = access.portAccess?.timeoutSeconds,
        nat = access.natTarget(serverIp),
        serverCommand = (access as? AccessRule.ServerCommand)?.let { "$sourceIp,${it.command}" },
        legacyMode = crypto.legacyMode,
        digest = crypto.digest,
        hmacDigest = crypto.hmacDigest,
    )
}

private fun AccessRule.natTarget(serverIp: String): NatTarget? = when (this) {
    is AccessRule.NatAccess -> NatTarget(natIp, natPort, local = false)
    is AccessRule.LocalNatAccess -> NatTarget(serverIp, natPort, local = true)
    else -> null
}

internal fun ServerEndpoint.toTarget(serverIp: String, random: Random): KnockTarget {
    val resolvedPort = when (val p = port) {
        is ServerPort.Fixed -> p.value
        ServerPort.Random -> random.nextInt(ServerPort.RANDOM_MIN, ServerPort.MAX)
    }
    return KnockTarget(serverIp, host, resolvedPort, protocol)
}
