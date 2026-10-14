// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.presentation.MainActivity
import fr.gildasledrogoff.fwknop.presentation.state.UiError
import fr.gildasledrogoff.fwknop.presentation.state.toUiError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoKnockNotifications @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    fun monitoring(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.auto_knock_notification_title))
            .setContentText(context.getString(R.string.auto_knock_notification_body))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun sent(nickname: String) = result(
        nickname,
        context.getString(R.string.auto_knock_sent, nickname),
        NotificationCompat.CATEGORY_STATUS,
    )

    fun failed(nickname: String, error: AutoKnockError) {
        val reason = when (val ui = error.toUiError()) {
            is UiError.Message -> context.getString(ui.resId)
            is UiError.Detailed -> context.getString(ui.resId, ui.detail)
        }
        result(
            nickname,
            context.getString(R.string.auto_knock_failed, nickname, reason),
            NotificationCompat.CATEGORY_ERROR,
        )
    }

    private fun result(nickname: String, text: String, category: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_rounded_fill)
            .setContentTitle(context.getString(R.string.notification_title, nickname))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setCategory(category)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) manager.notify(RESULT_ID, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(
                    MONITOR_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_MIN
                )
                    .setName(context.getString(R.string.auto_knock_channel_monitor))
                    .build(),
                NotificationChannelCompat.Builder(
                    RESULT_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                )
                    .setName(context.getString(R.string.auto_knock_channel_result))
                    .build(),
            )
        )
    }

    companion object {
        const val MONITOR_ID = 0x0A0C
        private const val RESULT_ID = 0x0A0D
        private const val MONITOR_CHANNEL = "auto_knock_monitor"
        private const val RESULT_CHANNEL = "auto_knock_result"
    }
}
