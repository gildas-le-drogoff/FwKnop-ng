package fr.gildasledrogoff.fwknop.domain.model

data class AppSettings(
    
    val dnsLookupEnabled: Boolean = false,
    
    val stunLookupEnabled: Boolean = true,
    val nfcKnockEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val ipLookupUrl: String = DEFAULT_IP_LOOKUP_URL,
    val helpShown: Boolean = false,
    val lockSuggestionDismissed: Boolean = false,
    val autoLockDelay: AutoLockDelay = AutoLockDelay.NEVER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    
    val dynamicColor: Boolean = false,
) {
    companion object {
        const val DEFAULT_IP_LOOKUP_URL = "https://ifconfig.me/ip"

        
        val IP_LOOKUP_PRESETS = listOf(
            DEFAULT_IP_LOOKUP_URL,
            "https://checkip.global.api.aws",
            "https://api.ipify.org",
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class QrKeys(val key: SecretKey?, val hmac: SecretKey?)
