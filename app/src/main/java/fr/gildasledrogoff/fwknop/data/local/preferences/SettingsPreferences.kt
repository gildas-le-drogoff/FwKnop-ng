package fr.gildasledrogoff.fwknop.data.local.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.AutoLockDelay
import fr.gildasledrogoff.fwknop.domain.model.ThemeMode

object SettingsPreferences {
    const val LEGACY_FILE = "MyPreferences"
    const val FILE = "settings"
    val DNS_ENABLED = booleanPreferencesKey("EnableDns")
    val STUN_ENABLED = booleanPreferencesKey("EnableStun")
    val NFC_ENABLED = booleanPreferencesKey("EnableNfc")
    val NOTIFICATION_ENABLED = booleanPreferencesKey("EnableNotification")
    val IP_SOURCE = stringPreferencesKey("ipSource")
    val HELP_SHOWN = booleanPreferencesKey("HaveShownPrefs")
    val LOCK_SUGGESTION_DISMISSED = booleanPreferencesKey("LockSuggestionDismissed")
    val AUTO_LOCK_DELAY = stringPreferencesKey("AutoLockDelay")
    val THEME_MODE = stringPreferencesKey("ThemeMode")
    val DYNAMIC_COLOR = booleanPreferencesKey("DynamicColor")
    val PERMISSIONS_PROMPTED = stringSetPreferencesKey("PermissionsPrompted")
    val PERMISSIONS_REFUSED_FOR_GOOD = stringSetPreferencesKey("PermissionsRefusedForGood")
}

fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        dnsLookupEnabled = this[SettingsPreferences.DNS_ENABLED] ?: defaults.dnsLookupEnabled,
        stunLookupEnabled = this[SettingsPreferences.STUN_ENABLED] ?: defaults.stunLookupEnabled,
        nfcKnockEnabled = this[SettingsPreferences.NFC_ENABLED] ?: defaults.nfcKnockEnabled,
        notificationsEnabled = this[SettingsPreferences.NOTIFICATION_ENABLED]
            ?: defaults.notificationsEnabled,
        ipLookupUrl = this[SettingsPreferences.IP_SOURCE] ?: defaults.ipLookupUrl,
        helpShown = this[SettingsPreferences.HELP_SHOWN] ?: defaults.helpShown,
        lockSuggestionDismissed = this[SettingsPreferences.LOCK_SUGGESTION_DISMISSED]
            ?: defaults.lockSuggestionDismissed,
        autoLockDelay = AutoLockDelay.entries.firstOrNull { it.name == this[SettingsPreferences.AUTO_LOCK_DELAY] }
            ?: defaults.autoLockDelay,
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[SettingsPreferences.THEME_MODE] }
            ?: defaults.themeMode,
        dynamicColor = this[SettingsPreferences.DYNAMIC_COLOR] ?: defaults.dynamicColor,
    )
}

fun MutablePreferences.write(settings: AppSettings) {
    this[SettingsPreferences.DNS_ENABLED] = settings.dnsLookupEnabled
    this[SettingsPreferences.STUN_ENABLED] = settings.stunLookupEnabled
    this[SettingsPreferences.NFC_ENABLED] = settings.nfcKnockEnabled
    this[SettingsPreferences.NOTIFICATION_ENABLED] = settings.notificationsEnabled
    this[SettingsPreferences.IP_SOURCE] = settings.ipLookupUrl
    this[SettingsPreferences.AUTO_LOCK_DELAY] = settings.autoLockDelay.name
    this[SettingsPreferences.THEME_MODE] = settings.themeMode.name
    this[SettingsPreferences.DYNAMIC_COLOR] = settings.dynamicColor
}
