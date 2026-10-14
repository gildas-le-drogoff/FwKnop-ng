// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import java.util.*

internal fun SecretKey.toBytes(): ByteArray? {
    val bytes = if (base64) decodeBase64(value) else value.toByteArray(Charsets.UTF_8)
    return bytes?.takeIf { it.size <= SpaFormat.MAX_KEY_LENGTH }
}

private fun decodeBase64(value: String): ByteArray? =
    runCatching { Base64.getDecoder().decode(value) }.getOrNull()
