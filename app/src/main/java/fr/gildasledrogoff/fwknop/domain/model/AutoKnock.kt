// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model


data class AutoKnockSettings(
    val enabled: Boolean = false,
    val nickname: String? = null,
    val trustedSsids: Set<String> = emptySet(),
) {
    val armed: Boolean get() = enabled && nickname != null && trustedSsids.isNotEmpty()
}


enum class WifiPresence {
    TRUSTED,
    UNTRUSTED,


    UNKNOWN,
}

object Ssids {

    private const val HIDDEN = "<unknown ssid>"


    fun normalize(raw: String?): String? {
        val ssid = raw?.trim()?.removeSurrounding("\"")
        return ssid?.takeUnless { it.isEmpty() || it == HIDDEN }
    }


    fun presence(connected: Collection<String?>, trusted: Set<String>): WifiPresence = when {
        connected.any { it != null && it in trusted } -> WifiPresence.TRUSTED
        connected.any { it == null } -> WifiPresence.UNKNOWN
        else -> WifiPresence.UNTRUSTED
    }
}
