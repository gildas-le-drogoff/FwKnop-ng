package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import kotlinx.coroutines.flow.Flow

typealias ConfigResult<T> = DomainResult<T, ConfigError>

interface ConfigRepository {
    fun observeConfigs(): Flow<ConfigResult<List<SpaConfig>>>
    suspend fun getConfig(nickname: Nickname): ConfigResult<SpaConfig>
    suspend fun exists(nickname: Nickname): ConfigResult<Boolean>
    suspend fun saveConfig(config: SpaConfig, previous: Nickname?): ConfigResult<Unit>
    suspend fun deleteConfig(nickname: Nickname): ConfigResult<Unit>
    suspend fun getAll(): ConfigResult<List<SpaConfig>>
    suspend fun replaceAll(configs: List<SpaConfig>): ConfigResult<Unit>

    suspend fun saveAll(configs: List<SpaConfig>): ConfigResult<Unit>
}
