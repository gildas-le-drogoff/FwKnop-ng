// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.gildasledrogoff.fwknop.domain.model.ConfigIcon
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.domain.model.LastKnock
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessCatalog
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessEntry
import fr.gildasledrogoff.fwknop.domain.repository.QuickAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickAccessRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : QuickAccessRepository {
    override val catalog: Flow<QuickAccessCatalog> =
        dataStore.data.map { it.toCatalog() }.distinctUntilChanged()

    override suspend fun syncEntries(entries: List<QuickAccessEntry>) {
        dataStore.edit { prefs ->
            val nicknames = entries.mapTo(mutableSetOf()) { it.nickname }
            prefs[ENTRIES] = json.encodeToString(ENTRY_LIST, entries.map(StoredEntry::of))
            val favorite = prefs[FAVORITE]
            if (favorite != null && favorite !in nicknames) prefs.remove(FAVORITE)
            prefs.writeKnocks(prefs.knocks().filter { it.nickname in nicknames })
        }
    }

    override suspend fun setFavorite(nickname: String?) {
        dataStore.edit { if (nickname == null) it.remove(FAVORITE) else it[FAVORITE] = nickname }
    }

    override suspend fun recordKnock(nickname: String, sent: Boolean, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs.writeKnocks(prefs.knocks().filter { it.nickname != nickname } + StoredKnock(nickname, atMillis, sent))
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(ENTRIES)
            it.remove(FAVORITE)
            it.remove(LAST_KNOCKS)
        }
    }

    private fun Preferences.toCatalog() = QuickAccessCatalog(
        entries = decode(this[ENTRIES], ENTRY_LIST).mapNotNull(StoredEntry::toDomain),
        favorite = this[FAVORITE],
        lastKnocks = knocks().associate { it.nickname to LastKnock(it.at, it.sent) },
    )

    private fun Preferences.knocks(): List<StoredKnock> = decode(this[LAST_KNOCKS], KNOCK_LIST)
    private fun MutablePreferences.writeKnocks(knocks: List<StoredKnock>) {
        this[LAST_KNOCKS] = json.encodeToString(KNOCK_LIST, knocks)
    }

    private fun <T> decode(value: String?, serializer: KSerializer<List<T>>): List<T> =
        value?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    @Serializable
    private data class StoredEntry(
        val nickname: String,
        val kind: String,
        val unattended: Boolean,
        val icon: String = "",
    ) {
        fun toDomain(): QuickAccessEntry? = ConfigKind.entries.firstOrNull { it.name == kind }
            ?.let { QuickAccessEntry(nickname, it, unattended, ConfigIcon.parse(icon)) }

        companion object {
            fun of(entry: QuickAccessEntry) =
                StoredEntry(entry.nickname, entry.kind.name, entry.unattended, entry.icon.name)
        }
    }

    @Serializable
    private data class StoredKnock(val nickname: String, val at: Long, val sent: Boolean)

    private companion object {
        val ENTRIES = stringPreferencesKey("QuickAccessEntries")
        val FAVORITE = stringPreferencesKey("QuickAccessFavorite")
        val LAST_KNOCKS = stringPreferencesKey("QuickAccessLastKnocks")
        val ENTRY_LIST = ListSerializer(StoredEntry.serializer())
        val KNOCK_LIST = ListSerializer(StoredKnock.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
