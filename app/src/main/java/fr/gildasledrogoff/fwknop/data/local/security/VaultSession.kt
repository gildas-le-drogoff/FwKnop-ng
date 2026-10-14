// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import com.google.crypto.tink.KeysetHandle
import fr.gildasledrogoff.fwknop.data.local.dao.ConfigDao
import fr.gildasledrogoff.fwknop.data.local.database.FwknopDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class OpenVault(
    val keyset: KeysetHandle,
    val columns: ColumnCipher,
    val database: FwknopDatabase
) {
    val dao: ConfigDao get() = database.configDao()
    val cipher: ConfigEntityCipher get() = ConfigEntityCipher(columns)
}

@Singleton
class VaultSession @Inject constructor() {
    val mutex = Mutex()
    private val writeMutex = Mutex()
    private val _open = MutableStateFlow<OpenVault?>(null)
    val open: StateFlow<OpenVault?> = _open.asStateFlow()
    suspend fun await(): OpenVault = open.filterNotNull().first()
    suspend fun <T> writing(block: suspend (OpenVault) -> T): T =
        writeMutex.withLock { block(await()) }

    fun open(vault: OpenVault) {
        _open.value = vault
    }

    fun replaceKeyset(keyset: KeysetHandle) = _open.update { it?.copy(keyset = keyset) }
    fun close() {
        _open.getAndUpdate { null }?.database?.close()
    }
}
