// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


enum class LocationAccessStatus { PERMISSION_MISSING, BACKGROUND_MISSING, SERVICE_OFF, GRANTED }


interface LocationAccess {
    fun status(): LocationAccessStatus

    companion object {
        val FOREGROUND = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        val BACKGROUND: String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACCESS_BACKGROUND_LOCATION else null
    }
}

class AndroidLocationAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationAccess {
    override fun status(): LocationAccessStatus {
        val background = LocationAccess.BACKGROUND
        val locationManager = context.getSystemService(LocationManager::class.java)
        return when {
            !granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccessStatus.PERMISSION_MISSING
            background != null && !granted(background) -> LocationAccessStatus.BACKGROUND_MISSING
            locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager) ->
                LocationAccessStatus.SERVICE_OFF

            else -> LocationAccessStatus.GRANTED
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
