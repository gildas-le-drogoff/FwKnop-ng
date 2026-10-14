// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.local.dao.KnockLogDao
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.model.KnockHistoryRetention
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class KnockHistoryRepositoryImpl @Inject constructor(
    private val session: VaultSession,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : KnockHistoryRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHistory(): Flow<List<KnockLogEntry>> =
        session.open.flatMapLatest { vault ->
            vault?.database?.knockLogDao()?.observeAll()
                ?.map { entities -> entities.map { it.toDomain() } }
                ?.catch {
                    DebugLog.failure("knock history read", it)
                    emit(emptyList())
                }
                ?: flowOf(emptyList())
        }


    override suspend fun record(event: KnockLogEvent) = withDao("knock history write") { dao ->
        val retention = retention()
        if (retention.days == null) return@withDao
        dao.insert(event.toEntity(clock.instant()))
        purge(dao, retention)
    }

    override suspend fun purgeExpired() =
        withDao("knock history purge") { dao -> purge(dao, retention()) }

    override suspend fun clear() = withDao("knock history clear") { it.deleteAll() }
    private suspend fun purge(dao: KnockLogDao, retention: KnockHistoryRetention) {
        val days = retention.days ?: return dao.deleteAll()
        dao.deleteOlderThan(clock.instant().minus(Duration.ofDays(days)).toEpochMilli())
    }

    private suspend fun retention() = settings.settings.first().knockHistoryRetention
    private suspend fun withDao(event: String, block: suspend (KnockLogDao) -> Unit) {
        val dao = session.open.value?.database?.knockLogDao() ?: return
        try {
            block(dao)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {

            DebugLog.failure(event, e)
        }
    }
}
