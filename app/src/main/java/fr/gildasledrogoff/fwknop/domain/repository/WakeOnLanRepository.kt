// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult

interface WakeOnLanRepository {

    suspend fun sendMagicPacket(
        packet: ByteArray,
        host: String,
        port: Int
    ): DomainResult<Unit, KnockError>
}
