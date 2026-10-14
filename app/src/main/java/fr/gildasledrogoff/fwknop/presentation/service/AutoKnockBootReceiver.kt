package fr.gildasledrogoff.fwknop.presentation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restarts the Wi-Fi monitoring after a reboot or an app update, when the automatic knock is armed. */
@AndroidEntryPoint
class AutoKnockBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var controller: AutoKnockController
    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                controller.syncNow()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
