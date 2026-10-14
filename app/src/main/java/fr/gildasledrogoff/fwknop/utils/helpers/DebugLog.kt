// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.util.Log
import fr.gildasledrogoff.fwknop.BuildConfig

object DebugLog {
    private const val TAG = "Fwknop"
    fun failure(event: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, event, error)
    }

    fun event(event: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, event)
    }
}
