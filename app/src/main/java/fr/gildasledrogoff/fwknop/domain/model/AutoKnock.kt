package fr.gildasledrogoff.fwknop.domain.model

/**
 * Advanced option, off by default: knock [nickname] when the device leaves every trusted Wi-Fi network.
 * Only takes effect once a config and at least one network are chosen.
 */
data class AutoKnockSettings(
    val enabled: Boolean = false,
    val nickname: String? = null,
    val trustedSsids: Set<String> = emptySet(),
) {
    val armed: Boolean get() = enabled && nickname != null && trustedSsids.isNotEmpty()
}

/** Where the device stands relative to the trusted Wi-Fi networks. */
enum class WifiPresence {
    TRUSTED,
    UNTRUSTED,

    /** Connected to a Wi-Fi network whose name Android hides (location permission or service off). */
    UNKNOWN,
}

object Ssids {
    // Returned by WifiInfo.getSSID() when the caller may not see the network name.
    private const val HIDDEN = "<unknown ssid>"

    /** The bare network name, or null when Android withholds it. */
    fun normalize(raw: String?): String? {
        val ssid = raw?.trim()?.removeSurrounding("\"")
        return ssid?.takeUnless { it.isEmpty() || it == HIDDEN }
    }

    /** [connected] holds the name of every connected Wi-Fi network, null when hidden. */
    fun presence(connected: Collection<String?>, trusted: Set<String>): WifiPresence = when {
        connected.any { it != null && it in trusted } -> WifiPresence.TRUSTED
        connected.any { it == null } -> WifiPresence.UNKNOWN
        else -> WifiPresence.UNTRUSTED
    }
}
