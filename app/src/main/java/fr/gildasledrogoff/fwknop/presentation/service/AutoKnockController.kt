package fr.gildasledrogoff.fwknop.presentation.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockMonitor
import fr.gildasledrogoff.fwknop.domain.usecase.ObserveAutoKnockSettingsUseCase
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccess
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccessStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Runs [AutoKnockService] exactly when the automatic knock is armed and can read the Wi-Fi name. */
@Singleton
class AutoKnockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeSettings: ObserveAutoKnockSettingsUseCase,
    private val location: LocationAccess,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AutoKnockMonitor {
    /** Follows the settings for the life of the process. */
    fun register() {
        scope.launch { observeSettings().collect(::sync) }
    }

    override fun refresh() {
        scope.launch { sync(observeSettings().first()) }
    }

    suspend fun syncNow() = sync(observeSettings().first())

    private fun sync(settings: AutoKnockSettings) {
        val intent = AutoKnockService.intent(context)
        if (!settings.armed || location.status() != LocationAccessStatus.GRANTED) {
            context.stopService(intent)
            return
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a foreground service started from the background outside of the
            // exemptions (boot, app update, visible app): the next of those starts it.
            DebugLog.failure("auto-knock service start", e)
        }
    }
}
