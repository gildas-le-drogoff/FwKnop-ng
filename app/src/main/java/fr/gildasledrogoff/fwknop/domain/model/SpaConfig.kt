package fr.gildasledrogoff.fwknop.domain.model

@JvmInline
value class Nickname(val value: String)
data class SpaConfig(
    val nickname: Nickname,
    val server: ServerEndpoint,
    val access: AccessRule,
    val sourceIp: SourceIp,
    val crypto: SpaCrypto,
    val externalApp: ExternalApp,
)

data class ServerEndpoint(
    val host: String,
    val port: ServerPort,
    val protocol: Protocol,
)

sealed interface ServerPort {
    data class Fixed(val value: Int) : ServerPort
    data object Random : ServerPort
    companion object {
        const val DEFAULT = 62201
        const val RANDOM_MIN = 10000
        const val MAX = 65535
    }
}

enum class Protocol { UDP, TCP, HTTP }
sealed interface SourceIp {
    data object Resolve : SourceIp
    data object Source : SourceIp
    data class Fixed(val address: String) : SourceIp
    data object Prompt : SourceIp
    companion object {
        const val ANY_ADDRESS = "0.0.0.0"
    }
}
