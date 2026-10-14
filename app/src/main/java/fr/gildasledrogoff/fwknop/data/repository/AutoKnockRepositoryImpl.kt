package fr.gildasledrogoff.fwknop.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoKnockRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoKnockRepository {
    override val settings: Flow<AutoKnockSettings> = dataStore.data.map { prefs ->
        AutoKnockSettings(
            enabled = prefs[ENABLED] ?: false,
            nickname = prefs[NICKNAME],
            trustedSsids = prefs[SSIDS].orEmpty(),
        )
    }.distinctUntilChanged()

    override suspend fun save(settings: AutoKnockSettings) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            settings.nickname?.let { prefs[NICKNAME] = it } ?: prefs.remove(NICKNAME)
            prefs[SSIDS] = settings.trustedSsids
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("AutoKnockEnabled")
        val NICKNAME = stringPreferencesKey("AutoKnockConfig")
        val SSIDS = stringSetPreferencesKey("AutoKnockTrustedSsids")
    }
}
