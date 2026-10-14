// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.data.local.preferences.SettingsPreferences
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class PermissionStatus {
    GRANTED,
    DENIED,
    DENIED_PERMANENTLY,
}

data class PermissionSnapshot(
    val notifications: PermissionStatus = PermissionStatus.GRANTED,
    val localNetwork: PermissionStatus = PermissionStatus.GRANTED,
    val prompted: Set<String> = emptySet(),
) {
    fun canPrompt(permission: String): Boolean =
        permission !in prompted && status(permission) == PermissionStatus.DENIED

    fun status(permission: String): PermissionStatus = when (permission) {
        RuntimePermissions.NOTIFICATIONS -> notifications
        LocalNetworkAccess.PERMISSION -> localNetwork
        else -> PermissionStatus.GRANTED
    }
}

interface RuntimePermissions {
    val snapshot: StateFlow<PermissionSnapshot>
    fun refresh(activity: Activity? = null)
    fun onPromptShown(permission: String)
    fun onPromptResult(permission: String, granted: Boolean, permanentlyDenied: Boolean)

    companion object {
        const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }
}

@Singleton
class AndroidRuntimePermissions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localNetwork: LocalNetworkAccess,
    private val store: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) : RuntimePermissions {
    private val prompted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val refusedForGood: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mutableSnapshot = MutableStateFlow(recompute())
    override val snapshot: StateFlow<PermissionSnapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch { restore() }
    }

    override fun refresh(activity: Activity?) = update { recompute(activity) }
    override fun onPromptShown(permission: String) = update {
        prompted += permission
        recompute()
    }

    override fun onPromptResult(permission: String, granted: Boolean, permanentlyDenied: Boolean) =
        update {
            prompted += permission
            if (granted) refusedForGood -= permission else if (permanentlyDenied) refusedForGood += permission
            recompute()
        }

    private fun update(compute: () -> PermissionSnapshot) {
        val before = history()
        mutableSnapshot.value = compute()
        if (history() != before) persist()
    }

    private fun history() = prompted.toSet() to refusedForGood.toSet()
    private suspend fun restore() {
        val saved = try {
            store.data.first()
        } catch (e: IOException) {
            DebugLog.failure("permission history read", e)
            return
        }
        prompted += saved[SettingsPreferences.PERMISSIONS_PROMPTED].orEmpty()
        refusedForGood += saved[SettingsPreferences.PERMISSIONS_REFUSED_FOR_GOOD].orEmpty()
        mutableSnapshot.value = recompute()
    }

    private fun persist() {
        scope.launch {
            try {
                store.edit {
                    it[SettingsPreferences.PERMISSIONS_PROMPTED] = prompted.toSet()
                    it[SettingsPreferences.PERMISSIONS_REFUSED_FOR_GOOD] = refusedForGood.toSet()
                }
            } catch (e: IOException) {
                DebugLog.failure("permission history write", e)
            }
        }
    }

    private fun recompute(activity: Activity? = null): PermissionSnapshot = PermissionSnapshot(
        notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) statusOf(
            RuntimePermissions.NOTIFICATIONS,
            {
                ContextCompat.checkSelfPermission(
                    context,
                    RuntimePermissions.NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            },
            activity,
        ) else PermissionStatus.GRANTED,
        localNetwork = statusOf(
            LocalNetworkAccess.PERMISSION,
            { !localNetwork.needsRequest() },
            activity,
        ),
        prompted = prompted.toSet(),
    )

    private fun statusOf(
        permission: String,
        granted: () -> Boolean,
        activity: Activity?
    ): PermissionStatus {
        if (granted()) {
            refusedForGood -= permission
            return PermissionStatus.GRANTED
        }
        if (permission in refusedForGood) return PermissionStatus.DENIED_PERMANENTLY
        if (activity != null && permission in prompted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        ) {
            refusedForGood += permission
            return PermissionStatus.DENIED_PERMANENTLY
        }
        return PermissionStatus.DENIED
    }
}
