// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.gildasledrogoff.fwknop.utils.helpers.SecureWindowPolicy
import javax.inject.Inject

@HiltAndroidApp
class FwknopApp : Application() {
    @Inject
    lateinit var startup: AppStartup
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindowPolicy)
        startup.run()
    }
}
