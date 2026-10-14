// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.database

object SqlCipher {
    private val loaded: Unit by lazy { System.loadLibrary("sqlcipher") }
    fun ensureLoaded() = loaded
}
