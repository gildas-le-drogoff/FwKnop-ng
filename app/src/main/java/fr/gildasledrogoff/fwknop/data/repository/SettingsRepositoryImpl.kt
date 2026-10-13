package fr.gildasledrogoff.fwknop.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import fr.gildasledrogoff.fwknop.data.local.preferences.SettingsPreferences
import fr.gildasledrogoff.fwknop.data.local.preferences.toAppSettings
import fr.gildasledrogoff.fwknop.data.local.preferences.write
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data.map { it.toAppSettings() }.distinctUntilChanged()

    override suspend fun saveSettings(settings: AppSettings) {
        dataStore.edit { it.write(settings) }
    }

    override suspend fun markHelpShown() {
        dataStore.edit { it[SettingsPreferences.HELP_SHOWN] = true }
    }

    override suspend fun dismissLockSuggestion() {
        dataStore.edit { it[SettingsPreferences.LOCK_SUGGESTION_DISMISSED] = true }
    }
}
