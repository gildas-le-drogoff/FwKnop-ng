// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

data class QuickAccessEntry(
    val nickname: String,
    val kind: ConfigKind,
    val unattended: Boolean,
    val icon: ConfigIcon = ConfigIcon.APP,
)

data class LastKnock(val atMillis: Long, val sent: Boolean)

data class QuickAccessCatalog(
    val entries: List<QuickAccessEntry> = emptyList(),
    val favorite: String? = null,
    val lastKnocks: Map<String, LastKnock> = emptyMap(),
) {
    val favoriteEntry: QuickAccessEntry?
        get() = entries.firstOrNull { it.nickname == favorite } ?: entries.singleOrNull()
}
