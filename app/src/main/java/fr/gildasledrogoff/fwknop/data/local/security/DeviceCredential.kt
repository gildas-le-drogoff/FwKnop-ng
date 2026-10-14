// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.app.KeyguardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DeviceCredential @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun isSecure(): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
}
