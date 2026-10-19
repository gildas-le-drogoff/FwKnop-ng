// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigIcon
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessEntry
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnockShortcuts @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun publish(entries: List<QuickAccessEntry>, favorite: String?) {
        try {
            val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(MAX_DYNAMIC)
            val ordered = entries.sortedByDescending { it.nickname == favorite }.take(limit)
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                ordered.mapIndexed { rank, entry -> shortcut(entry.nickname, entry.icon, rank) },
            )
            val known = entries.associateBy { id(it.nickname) }
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }
            val refreshed = pinned.mapNotNull { known[it] }.filter { it !in ordered }
            if (refreshed.isNotEmpty()) {
                ShortcutManagerCompat.updateShortcuts(
                    context,
                    refreshed.map { shortcut(it.nickname, it.icon, rank = 0) },
                )
            }
            val stale = pinned.filter { it !in known }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    stale,
                    context.getString(R.string.shortcut_config_deleted),
                )
            }
        } catch (e: IllegalStateException) {
            DebugLog.failure("shortcuts publish", e)
        } catch (e: IllegalArgumentException) {
            DebugLog.failure("shortcuts publish", e)
        }
    }

    fun reportUsed(nickname: String) {
        try {
            ShortcutManagerCompat.reportShortcutUsed(context, id(nickname))
        } catch (e: IllegalStateException) {
            DebugLog.failure("shortcut usage", e)
        }
    }

    fun canPin(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(nickname: String, icon: ConfigIcon): Boolean =
        ShortcutManagerCompat.requestPinShortcut(context, shortcut(nickname, icon, rank = 0), null)

    private fun shortcut(nickname: String, icon: ConfigIcon, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id(nickname))
            .setShortLabel(nickname)
            .setLongLabel(context.getString(R.string.shortcut_knock, nickname))
            .setIcon(ShortcutIcons.of(context, icon))
            .setIntent(QuickKnockIntents.app(context, nickname))
            .setRank(rank)
            .build()

    private fun id(nickname: String) = "knock:$nickname"

    private companion object {
        const val MAX_DYNAMIC = 4
    }
}
