package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.SettingsError
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.isDomainName
import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

class MarkHelpShownUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke() = repository.markHelpShown()
}

class DismissLockSuggestionUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke() = repository.dismissLockSuggestion()
}

class SaveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: AppSettings): DomainResult<AppSettings, SettingsError> {
        val url = settings.ipLookupUrl.trim()
        if (!isValidLookupUrl(url)) return DomainResult.Failure(SettingsError.InvalidLookupUrl)
        val normalized = settings.copy(ipLookupUrl = url)
        repository.saveSettings(normalized)
        return DomainResult.Success(normalized)
    }

    private fun isValidLookupUrl(url: String): Boolean {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        
        if (scheme != "https") return false
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        return host.isDomainName() || host.isIpAddress()
    }
}
