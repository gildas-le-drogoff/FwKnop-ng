// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.domain.model.QuickAccessCatalog
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveQuickAccessUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.presentation.MainActivity
import fr.gildasledrogoff.fwknop.presentation.quickaccess.OpenPort
import fr.gildasledrogoff.fwknop.presentation.quickaccess.OpenPortTracker
import fr.gildasledrogoff.fwknop.presentation.quickaccess.QuickKnockIntents
import kotlinx.coroutines.flow.first
import java.util.Date

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KnockWidgetEntryPoint {
    fun observeQuickAccess(): ObserveQuickAccessUseCase
    fun observeVault(): ObserveVaultStatusUseCase
    fun openPorts(): OpenPortTracker
}

class KnockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KnockWidget()
}

class KnockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, KnockWidgetEntryPoint::class.java)
        val catalogs = deps.observeQuickAccess().invoke()
        val initial = catalogs.first()
        provideContent {
            val catalog by catalogs.collectAsState(initial)
            val open by deps.openPorts().open.collectAsState()
            val vault by deps.observeVault().invoke().collectAsState()
            GlanceTheme { KnockWidgetContent(rows(context, catalog, open, vault.lockEnabled)) }
        }
    }
}

private data class WidgetRow(
    val nickname: String,
    val kind: ConfigKind,
    val inBackground: Boolean,
    val status: String?,
    val open: Boolean,
)

private fun rows(
    context: Context,
    catalog: QuickAccessCatalog,
    open: Map<String, OpenPort>,
    lockEnabled: Boolean,
): List<WidgetRow> {
    val now = System.currentTimeMillis()
    val time = DateFormat.getTimeFormat(context)
    return catalog.entries
        .sortedByDescending { it.nickname == catalog.favorite }
        .map { entry ->
            val port = open[entry.nickname]?.takeIf { it.untilMillis > now }
            val last = catalog.lastKnocks[entry.nickname]
            val status = when {
                port != null -> context.getString(R.string.quick_open_until, time.format(Date(port.untilMillis)))
                last != null -> context.getString(
                    if (last.sent) R.string.quick_last_sent else R.string.quick_last_failed,
                    DateUtils.formatSameDayTime(last.atMillis, now, java.text.DateFormat.SHORT, java.text.DateFormat.SHORT),
                )

                else -> null
            }
            WidgetRow(
                nickname = entry.nickname,
                kind = entry.kind,
                inBackground = QuickKnockIntents.runsInBackground(entry, lockEnabled),
                status = status,
                open = port != null,
            )
        }
}

@Composable
private fun KnockWidgetContent(rows: List<WidgetRow>) {
    val context = LocalContext.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(8.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(4.dp).clickable(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_shield_rounded_fill),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        if (rows.isEmpty()) {
            Text(
                context.getString(R.string.widget_empty),
                modifier = GlanceModifier.padding(4.dp).clickable(openApp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            LazyColumn {
                items(rows, itemId = { it.nickname.hashCode().toLong() }) { row ->
                    Box(GlanceModifier.padding(vertical = 3.dp)) { KnockRow(row, row.action(context)) }
                }
            }
        }
    }
}

private fun WidgetRow.action(context: Context): Action = if (inBackground) {
    actionStartService(QuickKnockIntents.background(context, nickname), isForegroundService = true)
} else {
    actionStartActivity(QuickKnockIntents.app(context, nickname))
}

@Composable
private fun KnockRow(row: WidgetRow, onClick: Action) {
    val colors = GlanceTheme.colors
    val container = if (row.open) colors.primaryContainer else colors.secondaryContainer
    val content = if (row.open) colors.onPrimaryContainer else colors.onSecondaryContainer
    Row(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(container)
            .clickable(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(
                if (row.kind == ConfigKind.WAKE_ON_LAN) R.drawable.ic_power_rounded else R.drawable.ic_bolt_rounded_fill
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(content),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                row.nickname,
                maxLines = 1,
                style = TextStyle(color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            row.status?.let {
                Text(it, maxLines = 1, style = TextStyle(color = content, fontSize = 12.sp))
            }
        }
    }
}
