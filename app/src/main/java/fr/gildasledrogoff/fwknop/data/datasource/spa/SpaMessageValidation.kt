package fr.gildasledrogoff.fwknop.data.datasource.spa

internal object SpaMessageValidation {
    private val IPV4 = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""")
    private val PROTO_PORT = Regex("""(?:tcp|udp)/(\d{1,5})""")
    private const val MAX_HOSTNAME_LENGTH = 70
    private const val FORBIDDEN_HOST_CHARS = " /?\"'\\"
    private val PORT_RANGE = 1..65_535
    fun isValidAccess(message: String): Boolean = withAllowIp(message) { rest ->
        rest.split(',')
            .all { spec -> PROTO_PORT.matchEntire(spec)?.groupValues?.get(1).isValidPort() }
    }

    fun isValidCommand(message: String): Boolean =
        withAllowIp(message) { rest -> rest.isNotEmpty() }

    fun isValidNatAccess(host: String, port: Int): Boolean =
        host.length <= MAX_HOSTNAME_LENGTH &&
                host.none { it in FORBIDDEN_HOST_CHARS || it == ',' } &&
                port in PORT_RANGE

    private inline fun withAllowIp(message: String, checkRest: (String) -> Boolean): Boolean {
        val comma = message.indexOf(',')
        return !(comma < 0 || message.length > SpaFormat.MAX_MESSAGE_LENGTH) && isValidIpv4(message.substring(0, comma)) && checkRest(message.substring(comma + 1))
    }

    private fun isValidIpv4(ip: String): Boolean =
        IPV4.matchEntire(ip)?.groupValues?.drop(1)?.all { it.toInt() <= 255 } == true

    private fun String?.isValidPort(): Boolean = this?.toIntOrNull() in PORT_RANGE
}
