package fr.gildasledrogoff.fwknop

import androidx.lifecycle.ProcessLifecycleOwner
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.usecase.KnockHistoryPurger
import fr.gildasledrogoff.fwknop.presentation.service.AutoKnockController
import fr.gildasledrogoff.fwknop.presentation.service.AutoLock
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.QrShareCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class AppStartup @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val qrShareCache: QrShareCache,
    private val autoLock: AutoLock,
    private val knockHistoryPurger: KnockHistoryPurger,
    private val autoKnock: AutoKnockController,
) {
    fun run() {
        scope.launch(dispatchers.io) { qrShareCache.clear() }
        autoLock.register(ProcessLifecycleOwner.get().lifecycle)
        knockHistoryPurger.start(scope)
        autoKnock.register()
    }
}
