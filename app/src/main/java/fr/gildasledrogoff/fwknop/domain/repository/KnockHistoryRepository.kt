package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted knock journal, stored in the vault database: only reachable while the vault is unlocked.
 * Every method is best effort and never throws: logging must not break a knock.
 */
interface KnockHistoryRepository {
    /** Newest first; empty while the vault is locked. */
    fun observeHistory(): Flow<List<KnockLogEntry>>

    /** Timestamps [event] now. Dropped when the vault is locked or the retention is OFF. */
    suspend fun record(event: KnockLogEvent)

    /** Deletes the entries older than the configured retention (all of them when OFF). */
    suspend fun purgeExpired()

    suspend fun clear()
}
