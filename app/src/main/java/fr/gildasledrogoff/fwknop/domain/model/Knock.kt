package fr.gildasledrogoff.fwknop.domain.model

@JvmInline
value class InteractionId(val value: Int)
data class KnockOverrides(
    val key: String? = null,
    val sourceIp: String? = null,
    val gpgInteraction: InteractionId? = null,
)

data class KnockRequest(
    val config: SpaConfig,
    val overrides: KnockOverrides = KnockOverrides(),
)

data class KnockOutcome(val request: KnockRequest, val source: ResolvedSourceIp)
enum class KnockPrompt { KEY, SOURCE_IP }
data class NatTarget(val address: String, val port: Int, val local: Boolean)
data class SpaPacketParams(
    val accessPorts: String,
    val sourceIp: String,
    val key: SecretKey,
    val hmac: SecretKey?,
    val timeoutSeconds: Int?,
    val nat: NatTarget?,
    val serverCommand: String?,
    val legacyMode: Boolean,
    val digest: DigestAlgorithm,
    val hmacDigest: DigestAlgorithm,
)

data class KnockTarget(val address: String, val host: String, val port: Int, val protocol: Protocol)
sealed interface KnockTimerPlan {
    data class Countdown(val nickname: Nickname, val seconds: Int) : KnockTimerPlan
    data class KeepOpen(val request: KnockRequest, val seconds: Int) : KnockTimerPlan
}

enum class KnockTimerOutcome {
    NONE,
    SCHEDULED,

    
    NOTIFICATIONS_BLOCKED,

    
    KEEP_OPEN_UNAVAILABLE,
}

