package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.local.security.OpenVault
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigResult
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ConfigRepositoryImpl @Inject constructor(private val session: VaultSession) :
    ConfigRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeConfigs(): Flow<ConfigResult<List<SpaConfig>>> =
        session.open.flatMapLatest { vault -> vault?.let(::observe) ?: emptyFlow() }

    override suspend fun getConfig(nickname: Nickname): ConfigResult<SpaConfig> {
        val found = guarded {
            val vault = session.await()
            vault.dao.findByNickname(nickname.value)?.let { vault.cipher.open(it).toDomain() }
        }
        return when (found) {
            is DomainResult.Failure -> found
            is DomainResult.Success -> found.data?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(ConfigError.NotFound)
        }
    }

    override suspend fun exists(nickname: Nickname): ConfigResult<Boolean> =
        guarded { session.await().dao.exists(nickname.value) }

    override suspend fun saveConfig(config: SpaConfig, previous: Nickname?): ConfigResult<Unit> = guarded {
        session.writing { vault ->
            val existingId = vault.dao.findByNickname((previous ?: config.nickname).value)?.id ?: 0L
            vault.dao.replace(
                vault.cipher.seal(config.toEntity()).copy(id = existingId),
                previous?.value
            )
        }
    }

    override suspend fun deleteConfig(nickname: Nickname): ConfigResult<Unit> =
        guarded { session.await().dao.deleteByNickname(nickname.value) }

    override suspend fun getAll(): ConfigResult<List<SpaConfig>> = guarded {
        val vault = session.await()
        vault.dao.getAll().map { vault.cipher.open(it).toDomain() }
    }

    override suspend fun replaceAll(configs: List<SpaConfig>): ConfigResult<Unit> = guarded {
        session.writing { vault -> vault.dao.replaceAll(configs.map { vault.cipher.seal(it.toEntity()) }) }
    }

    override suspend fun saveAll(configs: List<SpaConfig>): ConfigResult<Unit> = guarded {
        session.writing { vault ->
            vault.dao.upsertAllByNickname(configs.map { vault.cipher.seal(it.toEntity()) })
        }
    }

    private fun observe(vault: OpenVault): Flow<ConfigResult<List<SpaConfig>>> =
        vault.dao.observeAll()
            .map<_, ConfigResult<List<SpaConfig>>> { entities ->
                DomainResult.Success(entities.map { vault.cipher.open(it).toDomain() })
            }
            
            .catch { if (session.open.value === vault) emit(storageFailure(it)) }

    private suspend fun <T> guarded(block: suspend () -> T): ConfigResult<T> =
        try {
            DomainResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            storageFailure(e)
        }

    private fun storageFailure(error: Throwable): DomainResult.Failure<ConfigError> {
        DebugLog.failure("config storage", error)
        return DomainResult.Failure(ConfigError.Storage(error))
    }
}
