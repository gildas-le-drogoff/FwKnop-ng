package fr.gildasledrogoff.fwknop.data.datasource.spa

import android.os.Process
import javax.inject.Inject

fun interface SpaUsernameProvider {
    fun username(): String
}

class AndroidSpaUsername @Inject constructor() : SpaUsernameProvider {
    override fun username(): String = androidUsername(Process.myUid())
}

internal fun androidUsername(uid: Int): String {
    val appId = uid % PER_USER_RANGE
    if (appId < FIRST_APPLICATION_UID) return FALLBACK_USERNAME
    return "u${uid / PER_USER_RANGE}_a${appId - FIRST_APPLICATION_UID}"
}

private const val PER_USER_RANGE = 100_000
private const val FIRST_APPLICATION_UID = 10_000
private const val FALLBACK_USERNAME = "NO_USER"
