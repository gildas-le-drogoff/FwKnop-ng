// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessEntry
import fr.gildasledrogoff.fwknop.presentation.service.KeepOpenService

object QuickKnockIntents {
    const val ACTION_KNOCK = "fr.gildasledrogoff.fwknop.action.KNOCK"
    const val EXTRA_NICKNAME = "nickname"

    private const val KNOCK_ALIAS = "fr.gildasledrogoff.fwknop.presentation.KnockShortcut"

    fun runsInBackground(entry: QuickAccessEntry, lockEnabled: Boolean): Boolean =
        entry.unattended && !lockEnabled

    fun background(context: Context, nickname: String): Intent =
        KeepOpenService.quickKnockIntent(context, nickname)

    fun app(context: Context, nickname: String): Intent = Intent(ACTION_KNOCK)
        .setComponent(ComponentName(context.packageName, KNOCK_ALIAS))
        .putExtra(EXTRA_NICKNAME, nickname)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun nicknameOf(intent: Intent?): String? = intent
        ?.takeIf { it.action == ACTION_KNOCK && it.component?.className == KNOCK_ALIAS }
        ?.getStringExtra(EXTRA_NICKNAME)
}
