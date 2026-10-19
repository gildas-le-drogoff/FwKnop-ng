// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessCatalog
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessEntry
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveQuickAccessUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.presentation.MainActivity
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class KnockTileService : TileService() {
    @Inject
    lateinit var observeQuickAccess: ObserveQuickAccessUseCase

    @Inject
    lateinit var observeVault: ObserveVaultStatusUseCase

    @Inject
    lateinit var openPorts: OpenPortTracker
    private var listening: CoroutineScope? = null
    private var favorite: QuickAccessEntry? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        listening = scope
        scope.launch {
            combine(observeQuickAccess(), openPorts.open) { catalog, open -> render(catalog, open) }
                .collect {}
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val entry = favorite
        if (isLocked) unlockAndRun { knock(entry) } else knock(entry)
    }

    private fun knock(entry: QuickAccessEntry?) {
        if (entry != null && QuickKnockIntents.runsInBackground(entry, observeVault().value.lockEnabled)) {
            try {
                ContextCompat.startForegroundService(this, QuickKnockIntents.background(this, entry.nickname))
                return
            } catch (e: IllegalStateException) {
                DebugLog.failure("tile knock", e)
            }
        }
        openApp(
            entry?.let { QuickKnockIntents.app(this, it.nickname) }
                ?: Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openApp(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(catalog: QuickAccessCatalog, open: Map<String, OpenPort>) {
        val entry = catalog.favoriteEntry
        favorite = entry
        val tile = qsTile ?: return
        val port = entry?.let { open[it.nickname] }
        tile.label = entry?.nickname ?: getString(R.string.tile_label)
        tile.state = if (port != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val last = entry?.let { catalog.lastKnocks[it.nickname] }
            tile.subtitle = when {
                entry == null -> getString(R.string.tile_choose_favorite)
                port != null -> getString(R.string.quick_open_until, time(port.untilMillis))
                last != null -> getString(
                    if (last.sent) R.string.quick_last_sent else R.string.quick_last_failed,
                    time(last.atMillis),
                )

                else -> getString(R.string.knock)
            }
        }
        tile.updateTile()
    }

    private fun time(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))
}
