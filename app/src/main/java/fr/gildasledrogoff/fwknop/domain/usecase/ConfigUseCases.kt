package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.ConfigDraft
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.flatMap
import fr.gildasledrogoff.fwknop.domain.model.map
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConfigsUseCase @Inject constructor(private val repository: ConfigRepository) {
    operator fun invoke(): Flow<ConfigResult<List<SpaConfig>>> = repository.observeConfigs()
}

class GetConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(nickname: Nickname): DomainResult<SpaConfig, ConfigError> =
        repository.getConfig(nickname)
}

class DeleteConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(nickname: Nickname): ConfigResult<Unit> = repository.deleteConfig(nickname)
}

class ValidateConfigUseCase @Inject constructor() {
    operator fun invoke(draft: ConfigDraft): DomainResult<SpaConfig, ConfigError> {
        val nickname = draft.nickname.trim()
        if (nickname.isEmpty()) return DomainResult.Failure(ConfigError.NicknameBlank)
        return draft.server.toEndpoint().flatMap { server ->
            draft.access.toAccessRule().flatMap { access ->
                draft.sourceIp.toSourceIp().flatMap { sourceIp ->
                    draft.crypto.toCrypto().flatMap { crypto ->
                        draft.externalApp.toExternalApp().map { app ->
                            SpaConfig(Nickname(nickname), server, access, sourceIp, crypto, app)
                        }
                    }
                }
            }
        }
    }
}

class SaveConfigUseCase @Inject constructor(
    private val validate: ValidateConfigUseCase,
    private val repository: ConfigRepository,
) {
    suspend operator fun invoke(
        draft: ConfigDraft,
        previous: Nickname?
    ): DomainResult<SpaConfig, ConfigError> {
        return validate(draft)
            .flatMap { ensureNicknameAvailable(it, previous) }
            .flatMap { config -> repository.saveConfig(config, previous).map { config } }
    }

    private suspend fun ensureNicknameAvailable(
        config: SpaConfig,
        previous: Nickname?,
    ): DomainResult<SpaConfig, ConfigError> {
        if (config.nickname == previous) return DomainResult.Success(config)
        return repository.exists(config.nickname).flatMap { taken ->
            if (taken) DomainResult.Failure(ConfigError.NicknameTaken) else DomainResult.Success(config)
        }
    }
}
