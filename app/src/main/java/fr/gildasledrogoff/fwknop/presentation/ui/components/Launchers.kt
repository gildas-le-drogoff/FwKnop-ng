// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.presentation.state.UiError

@Composable
fun rememberQrScanner(onScanned: (String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onScanned)
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(callback) ?: run { cameraDenied = result.isMissingCamera() }
    }
    if (cameraDenied) CameraDeniedDialog { cameraDenied = false }
    return remember(launcher) {
        {
            launcher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .addExtra(Intents.Scan.SHOW_MISSING_CAMERA_PERMISSION_DIALOG, false),
            )
        }
    }
}

private fun ScanIntentResult.isMissingCamera(): Boolean =
    originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true

@Composable
private fun CameraDeniedDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val action = DialogAction(stringResource(R.string.action_open_app_settings)) {
        openAppSettings(context)
        onDismiss()
    }
    ErrorDialog(
        UiError.Message(R.string.error_camera_denied),
        action = action,
        onDismiss = onDismiss
    )
}

@Composable
fun rememberPermissionRequest(
    permission: String,
    onShown: () -> Unit = {},
    onResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current
    val shown by rememberUpdatedState(onShown)
    val result by rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val refusedForGood = !granted && activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            result(granted, refusedForGood)
        }
    return remember(launcher) {
        {
            shown()
            launcher.launch(permission)
        }
    }
}

@Composable
fun rememberInteractionLauncher(
    onFinished: (InteractionId, completed: Boolean, data: Intent?) -> Unit,
): (InteractionId, IntentSender) -> Unit {
    val callback by rememberUpdatedState(onFinished)
    val pendingId = rememberSaveable { mutableIntStateOf(0) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            callback(
                InteractionId(pendingId.intValue),
                it.resultCode == Activity.RESULT_OK,
                it.data
            )
        }
    return remember(launcher) {
        { id, sender ->
            pendingId.intValue = id.value
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}

@Composable
fun rememberExternalAppLauncher(onError: (UiError) -> Unit): (ExternalApp, String) -> Unit {
    val callback by rememberUpdatedState(onError)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val context = LocalContext.current
    return remember(launcher, context) {
        { app, nickname ->
            if (app is ExternalApp.WireGuard) {
                startWireGuard(context, app)?.let(callback)
            } else {
                try {
                    app.toIntent(nickname)?.let(launcher::launch)
                } catch (e: ActivityNotFoundException) {
                    callback(app.missingAppError())
                }
            }
        }
    }
}

private fun startWireGuard(context: Context, app: ExternalApp.WireGuard): UiError? {
    when {
        !context.isInstalled(WIREGUARD_PACKAGE) -> return UiError.Message(R.string.error_no_wireguard_app)
        app.remoteKey.isBlank() -> return UiError.Message(R.string.error_wireguard_key_missing)
    }
    context.sendBroadcast(
        Intent("$WIREGUARD_PACKAGE.START_TUNNEL")
            .setPackage(WIREGUARD_PACKAGE)
            .putExtra("tunnelName", app.tunnel)
            .putExtra("key", app.remoteKey),
    )
    return null
}

private fun Context.isInstalled(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun ExternalApp.toIntent(nickname: String): Intent? = when (this) {
    ExternalApp.None, is ExternalApp.WireGuard -> null
    is ExternalApp.SshUri -> Intent(Intent.ACTION_VIEW, "ssh://$target/#$nickname".toUri())
    is ExternalApp.OpenVpn -> Intent(Intent.ACTION_MAIN)
        .setClassName(OPENVPN_PACKAGE, "$OPENVPN_PACKAGE.LaunchVPN")
        .putExtra("$OPENVPN_PACKAGE.shortcutProfileName", profile)
}

private fun ExternalApp.missingAppError(): UiError = UiError.Message(
    when (this) {
        is ExternalApp.OpenVpn -> R.string.error_no_vpn_app
        is ExternalApp.WireGuard -> R.string.error_no_wireguard_app
        else -> R.string.error_no_ssh_app
    },
)

fun openAppSettings(context: Context) = startSettings(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
)

fun openNfcSettings(context: Context) = startSettings(context, Intent(Settings.ACTION_NFC_SETTINGS))
private fun startSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private const val OPENVPN_PACKAGE = "de.blinkt.openvpn"
private const val WIREGUARD_PACKAGE = "com.zaneschepke.wireguardautotunnel"
