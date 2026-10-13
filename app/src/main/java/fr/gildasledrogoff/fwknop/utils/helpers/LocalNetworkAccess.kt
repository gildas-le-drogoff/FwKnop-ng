package fr.gildasledrogoff.fwknop.utils.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface LocalNetworkAccess {
    fun needsRequest(): Boolean

    companion object {
        const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK
    }
}

@Singleton
class AndroidLocalNetworkAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalNetworkAccess {
    override fun needsRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                ContextCompat.checkSelfPermission(
                    context,
                    LocalNetworkAccess.PERMISSION
                ) != PackageManager.PERMISSION_GRANTED
}
