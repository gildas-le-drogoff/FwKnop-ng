// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.presentation.MainActivity
import fr.gildasledrogoff.fwknop.presentation.quickaccess.QuickKnockIntents
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

class KeepOpenActions(val stop: PendingIntent, val extend: PendingIntent)

@Singleton
class KnockNotifications @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    fun showCountdown(nickname: String, seconds: Int) {
        val notification = baseBuilder(nickname, seconds)
            .setContentText(context.getString(R.string.notification_port_open))
            .setTimeoutAfter(seconds * MILLIS)
            .build()
        notify(nickname.hashCode(), notification)
    }

    fun keepOpen(nickname: String, seconds: Int, actions: KeepOpenActions): Notification {
        val until = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + seconds * MILLIS))
        val text = context.getString(R.string.notification_keep_open)
        val details = context.getString(R.string.notification_keep_open_details, until)
        return baseBuilder(nickname, seconds)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(openApp())
            .setOngoing(true)
            .addAction(R.drawable.ic_history_rounded, context.getString(R.string.action_extend_port), actions.extend)
            .addAction(R.drawable.ic_lock_rounded, context.getString(R.string.action_close_port), actions.stop)
            .build()
    }

    fun knocking(nickname: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setContentText(context.getString(R.string.notification_knocking))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun quickKnockSent(nickname: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setContentText(context.getString(R.string.knock_sent, nickname))
            .setContentIntent(openApp())
            .setTimeoutAfter(SENT_VISIBLE_MS)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notify(nickname.hashCode(), notification)
    }

    fun quickKnockFailed(nickname: String, error: AutoKnockError) {
        ensureChannel()
        val reason = when (val ui = error.toUiError()) {
            is UiError.Message -> context.getString(ui.resId)
            is UiError.Detailed -> context.getString(ui.resId, ui.detail)
        }
        val text = context.getString(R.string.quick_knock_failed, reason)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(knockInApp(nickname))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        notify(nickname.hashCode(), notification)
    }

    fun keepOpenFailed(nickname: String) {
        ensureChannel()
        val text = context.getString(R.string.notification_keep_open_failed)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        notify(nickname.hashCode(), notification)
    }

    fun canNotify(): Boolean = hasPermission() && manager.areNotificationsEnabled()
    fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) manager.notify(id, notification)
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun cancel(id: Int) = manager.cancel(id)
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun knockInApp(nickname: String): PendingIntent = PendingIntent.getActivity(
        context,
        nickname.hashCode(),
        QuickKnockIntents.app(context, nickname),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun baseBuilder(nickname: String, seconds: Int): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setWhen(System.currentTimeMillis() + seconds * MILLIS)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel))
                .build()
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "knock_countdown"
        const val MILLIS = 1_000L
        const val SENT_VISIBLE_MS = 10_000L
    }
}
