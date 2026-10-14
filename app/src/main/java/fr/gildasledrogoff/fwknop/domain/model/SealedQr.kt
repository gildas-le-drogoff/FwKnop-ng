// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

object SealedQr {
    const val PREFIX = "FWKNOP-ENC1:"
    fun isSealed(content: String): Boolean = content.startsWith(PREFIX)
}
