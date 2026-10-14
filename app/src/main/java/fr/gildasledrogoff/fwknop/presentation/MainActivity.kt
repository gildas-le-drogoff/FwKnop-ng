// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.domain.model.ThemeMode
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveSettingsUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveVaultStatusUseCase
import fr.gildasledrogoff.fwknop.presentation.service.AutoLock
import fr.gildasledrogoff.fwknop.presentation.ui.navigation.FwknopNavHost
import fr.gildasledrogoff.fwknop.presentation.ui.screens.lock.LockGate
import fr.gildasledrogoff.fwknop.presentation.ui.screens.lock.LockScreenContent
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.isDark
import fr.gildasledrogoff.fwknop.presentation.viewmodel.LockGateViewModel
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var autoLock: AutoLock

    @Inject
    lateinit var observeVault: ObserveVaultStatusUseCase

    @Inject
    lateinit var permissions: RuntimePermissions

    @Inject
    lateinit var observeSettings: ObserveSettingsUseCase
    private val gate: LockGateViewModel by viewModels()
    private var sawUnlocked = false
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        autoLock.lockIfExpired()
        val appearance = observeSettings().map { Appearance(it.themeMode, it.dynamicColor) }
            .distinctUntilChanged()
        setContent {
            val current by appearance.collectAsStateWithLifecycle(initialValue = null)
            current?.let { (mode, dynamicColor) ->
                val dark = mode.isDark()
                SystemBarsStyle(dark)
                FwknopTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                    LockGate(
                        gate,
                        locked = { state, actions -> LockScreenContent(state, actions) }) {
                        FwknopNavHost()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { observeVault().collect { onVaultState(it.state) } }
        }
    }

    override fun onStart() {
        permissions.refresh(this)
        autoLock.lockIfExpired()
        super.onStart()
    }

    override fun onResume() {
        permissions.refresh(this)
        super.onResume()
    }

    @Composable
    private fun SystemBarsStyle(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
            onDispose {}
        }
    }

    private fun onVaultState(state: VaultState) {
        if (state == VaultState.UNLOCKED) sawUnlocked = true else if (sawUnlocked) restartLocked()
    }

    private fun restartLocked() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private data class Appearance(val mode: ThemeMode, val dynamicColor: Boolean)
    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
