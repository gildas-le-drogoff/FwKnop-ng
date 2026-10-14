// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import java.util.*
import javax.inject.Inject

fun interface KeyAliasFactory {
    fun next(): String
}

class RandomKeyAliasFactory @Inject constructor() : KeyAliasFactory {
    override fun next(): String = KeyAliases.PREFIX + UUID.randomUUID()
}

object KeyAliases {
    const val PREFIX = "fwknop_master_"
    fun isOurs(alias: String): Boolean = alias.startsWith(PREFIX)
}
