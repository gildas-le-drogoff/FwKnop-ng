// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager

object SecureWindowPolicy : Application.ActivityLifecycleCallbacks {
    private const val EXTRA_SCREENSHOTS = "screenshots"
    private var screenshotsAllowed = false
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (allowsScreenshots(activity)) return
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun allowsScreenshots(activity: Activity): Boolean {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val requested = activity.intent?.getBooleanExtra(EXTRA_SCREENSHOTS, false) == true
        screenshotsAllowed = screenshotsAllowed || (debuggable && requested)
        return screenshotsAllowed
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
