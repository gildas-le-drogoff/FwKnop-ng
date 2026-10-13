package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
    suspend fun markHelpShown()
    suspend fun dismissLockSuggestion()
}
