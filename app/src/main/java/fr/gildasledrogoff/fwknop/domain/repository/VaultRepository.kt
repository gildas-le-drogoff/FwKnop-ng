// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.VaultStatus
import kotlinx.coroutines.flow.StateFlow

interface VaultRepository {
    val status: StateFlow<VaultStatus>
    suspend fun unlock(): DomainResult<Unit, VaultError>
    fun lock()
    suspend fun setLockEnabled(enabled: Boolean): DomainResult<Unit, VaultError>
    suspend fun rotateKeys(): DomainResult<Unit, VaultError>
    suspend fun reset(): DomainResult<Unit, VaultError>
    fun isDeviceSecure(): Boolean
}
