// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import fr.gildasledrogoff.fwknop.domain.model.NfcTagKey
import fr.gildasledrogoff.fwknop.domain.model.Nickname

interface NfcTagRepository {
    suspend fun digest(nickname: Nickname): NfcTagDigest
    suspend fun match(digests: List<NfcTagDigest>, candidates: List<Nickname>): List<Nickname?>
    suspend fun exportKey(): NfcTagKey?
    suspend fun importKey(key: NfcTagKey)
}
