// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

fun uniqueNickname(nickname: String, taken: Set<String>): String {
    if (nickname !in taken) return nickname
    return generateSequence(2) { it + 1 }.map { "$nickname ($it)" }.first { it !in taken }
}
