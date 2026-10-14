package fr.gildasledrogoff.fwknop.domain.model

data class AppSettings(
    
    val dnsLookupEnabled: Boolean = false,
    
    val stunLookupEnabled: Boolean = true,
    val nfcKnockEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    // Opt-in: the TCP probe is extra traffic and may be counted by fail2ban-style tools on the server.
    val portCheckEnabled: Boolean = false,
    val ipLookupUrl: String = DEFAULT_IP_LOOKUP_URL,
    val helpShown: Boolean = false,
    val lockSuggestionDismissed: Boolean = false,
    val autoLockDelay: AutoLockDelay = AutoLockDelay.NEVER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    
    val dynamicColor: Boolean = false,
    val knockHistoryRetention: KnockHistoryRetention = KnockHistoryRetention.DAYS_30,
    /** Lets slow firewalls apply the rule before SSH / OpenVPN / WireGuard connects. */
    val externalAppDelaySeconds: Int = DEFAULT_EXTERNAL_APP_DELAY_SECONDS,
) {
    companion object {
        const val DEFAULT_IP_LOOKUP_URL = "https://ifconfig.me/ip"
        const val DEFAULT_EXTERNAL_APP_DELAY_SECONDS = 0
        val EXTERNAL_APP_DELAY_RANGE = 0..5

        
        val IP_LOOKUP_PRESETS = listOf(
            DEFAULT_IP_LOOKUP_URL,
            "https://checkip.global.api.aws",
            "https://api.ipify.org",
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class QrKeys(val key: SecretKey?, val hmac: SecretKey?)
