// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveConfigsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveQuickAccessUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SyncQuickAccessUseCase
import fr.gildasledrogoff.fwknop.presentation.widget.KnockWidget
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class QuickAccessSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val observeConfigs: ObserveConfigsUseCase,
    private val syncQuickAccess: SyncQuickAccessUseCase,
    private val observeQuickAccess: ObserveQuickAccessUseCase,
    private val observeVault: ObserveVaultStatusUseCase,
    private val openPorts: OpenPortTracker,
    private val shortcuts: KnockShortcuts,
) {
    fun register() {
        scope.launch {
            observeConfigs().collect { result ->
                if (result is DomainResult.Success) syncQuickAccess(result.data)
            }
        }
        scope.launch {
            observeQuickAccess().map { it.entries to it.favorite }.distinctUntilChanged()
                .collect { (entries, favorite) -> shortcuts.publish(entries, favorite) }
        }
        scope.launch {
            combine(
                observeQuickAccess(),
                observeVault().map { it.lockEnabled }.distinctUntilChanged(),
                openPorts.open,
            ) { _, _, _ -> }.collect { updateWidgets() }
        }
    }

    private suspend fun updateWidgets() {
        try {
            KnockWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            DebugLog.failure("widget update", e)
        }
    }
}
