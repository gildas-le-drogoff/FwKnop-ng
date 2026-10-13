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
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.R
import javax.inject.Inject
import javax.inject.Singleton

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

    fun keepOpen(nickname: String, seconds: Int, stopIntent: PendingIntent): Notification =
        baseBuilder(nickname, seconds)
            .setContentText(context.getString(R.string.notification_keep_open))
            .setOngoing(true)
            .addAction(0, context.getString(R.string.action_close_port), stopIntent)
            .build()

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
    }
}
